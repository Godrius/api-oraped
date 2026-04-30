package br.com.oraped.service.geolocalizacao;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.geolocalizacao.BairroVizinhanca;
import br.com.oraped.dto.geolocalizacao.BairroVizinhoInputDTO;
import br.com.oraped.dto.geolocalizacao.EnderecoBairroProximoDTO;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.repository.geolocalizacao.BairroRepository;
import br.com.oraped.repository.geolocalizacao.BairroVizinhancaRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BairroService {

    private final BairroRepository bairroRepository;
    private final BairroVizinhancaRepository bairroVizinhancaRepository;

    /**
     * Provider externo (CEP -> endereço/bairro/cidade/uf + lat/lng) e (lat/lng -> bairros próximos).
     * Implemente com OSM/Mapbox/Google sem acoplar o domínio.
     */
    private final GeolocalizacaoProvider geolocalizacaoProvider;

    @Transactional
    public Bairro obterOuCriarPorNomeCidadeUf(String nome, String cidade, String uf, Double latitude, Double longitude) {

        String nomeLimpo = limpar(nome);
        String cidadeLimpa = limpar(cidade);
        String ufLimpo = limpar(uf);

        if (!StringUtils.hasText(nomeLimpo) || !StringUtils.hasText(cidadeLimpa) || !StringUtils.hasText(ufLimpo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bairro/cidade/uf são obrigatórios");
        }

        String nomeNormalizado = normalizar(nomeLimpo);

        Optional<Bairro> existente =
            bairroRepository.findByNomeNormalizadoIgnoreCaseAndCidadeIgnoreCaseAndUfIgnoreCase(
                nomeNormalizado,
                cidadeLimpa,
                ufLimpo
            );

        if (existente.isPresent()) {
            Bairro b = existente.get();

            boolean precisaAtualizarCoords =
                (b.getLatitude() == null || b.getLongitude() == null) &&
                (latitude != null && longitude != null);

            boolean precisaAtualizarNome =
                !Objects.equals(b.getNome(), nomeLimpo);

            boolean precisaAtualizarNomeNormalizado =
                !Objects.equals(b.getNomeNormalizado(), nomeNormalizado);

            if (precisaAtualizarCoords || precisaAtualizarNome || precisaAtualizarNomeNormalizado) {
                if (precisaAtualizarCoords) {
                    b.setLatitude(latitude);
                    b.setLongitude(longitude);
                }
                if (precisaAtualizarNome) {
                    b.setNome(nomeLimpo);
                }
                if (precisaAtualizarNomeNormalizado) {
                    b.setNomeNormalizado(nomeNormalizado);
                }
                return bairroRepository.save(b);
            }

            return b;
        }

        Bairro novo = new Bairro();
        novo.setNome(nomeLimpo);
        novo.setNomeNormalizado(nomeNormalizado);
        novo.setCidade(cidadeLimpa);
        novo.setUf(ufLimpo);
        novo.setLatitude(latitude);
        novo.setLongitude(longitude);

        return bairroRepository.save(novo);
    }

    @Transactional(readOnly = true)
    public List<Bairro> listarVizinhosOrdenados(Long idBairroBase) {

        if (idBairroBase == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairroBase é obrigatório");
        }

        return bairroVizinhancaRepository.findVizinhosOrdenados(idBairroBase);
    }

    /**
     * Recria a vizinhança inteira do bairro base, preservando a ordem recebida.
     *
     * Regras:
     * - o próprio bairro base deve existir na vizinhança
     * - a lista é deduplicada por id de bairro vizinho
     * - para bairros diferentes, mantém simetria A->B e B->A
     */
    @Transactional
    public void salvarVizinhanca(Long idBairroBase, List<BairroVizinhoInputDTO> vizinhosOrdenados) {

        if (idBairroBase == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairroBase é obrigatório");
        }

        if (vizinhosOrdenados == null) {
            vizinhosOrdenados = List.of();
        }

        // Limpa a malha anterior do bairro base e força o banco a refletir isso
        // antes das novas inserções, evitando colisão com a constraint única.
        bairroVizinhancaRepository.deleteByBairroId(idBairroBase);
        bairroVizinhancaRepository.flush();

        Bairro bairroBaseRef = bairroRepository.getReferenceById(idBairroBase);

        int ordem = 0;
        Set<Long> idsJaProcessados = new LinkedHashSet<>();

        for (BairroVizinhoInputDTO v : vizinhosOrdenados) {

            if (v == null || v.getIdBairroVizinho() == null) {
                continue;
            }

            Long idVizinho = v.getIdBairroVizinho();

            // Evita inserir o mesmo vizinho duas vezes na mesma reconstrução.
            if (!idsJaProcessados.add(idVizinho)) {
                continue;
            }

            Bairro bairroVizinhoRef = bairroRepository.getReferenceById(idVizinho);

            BairroVizinhanca rel = new BairroVizinhanca();
            rel.setBairro(bairroBaseRef);
            rel.setVizinho(bairroVizinhoRef);
            rel.setOrdem(ordem++);
            rel.setDistanciaMetros(v.getDistanciaMetros() == null ? 0 : v.getDistanciaMetros());

            bairroVizinhancaRepository.save(rel);

            // Para o próprio bairro, a relação já está completa.
            if (Objects.equals(idVizinho, idBairroBase)) {
                continue;
            }

            // Garante navegação simétrica entre bairros distintos.
            if (!bairroVizinhancaRepository.existsByBairroIdAndVizinhoId(idVizinho, idBairroBase)) {
                BairroVizinhanca inv = new BairroVizinhanca();
                inv.setBairro(bairroVizinhoRef);
                inv.setVizinho(bairroBaseRef);
                inv.setOrdem(0);
                inv.setDistanciaMetros(v.getDistanciaMetros() == null ? 0 : v.getDistanciaMetros());
                bairroVizinhancaRepository.save(inv);
            }
        }

        bairroVizinhancaRepository.flush();
    }

    /**
     * Fluxo principal do setup:
     * - resolve CEP -> bairro/cidade/uf + lat/lng
     * - findOrCreate do bairro base
     * - sempre recompõe a vizinhança quando o CEP é informado
     * - inclui o próprio bairro da loja como primeiro item
     *
     * Isso permite corrigir estabelecimentos antigos ao reenviar o mesmo CEP.
     */
    @Transactional
    public Bairro setupBairroBasePorCep(String cep) {

        String cepLimpo = limparCep(cep);
        if (!StringUtils.hasText(cepLimpo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CEP é obrigatório");
        }

        EnderecoResolvidoDTO end = geolocalizacaoProvider.resolverCep(cepLimpo);

        if (end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não consegui resolver o CEP informado");
        }

        if (!StringUtils.hasText(end.getBairro()) || !StringUtils.hasText(end.getCidade()) || !StringUtils.hasText(end.getUf())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "CEP resolvido, mas não retornou bairro/cidade/UF suficientes para o setup"
            );
        }

        Bairro base = obterOuCriarPorNomeCidadeUf(
            end.getBairro(),
            end.getCidade(),
            end.getUf(),
            end.getLatitude(),
            end.getLongitude()
        );

        List<BairroVizinhoInputDTO> inputs = new ArrayList<>();

        // O próprio bairro da loja deve sempre aparecer na primeira posição.
        inputs.add(criarInputVizinho(base.getId(), 0));

        // Se não houver coordenadas, ainda assim garantimos ao menos o próprio bairro.
        if (base.getLatitude() == null || base.getLongitude() == null) {
            salvarVizinhanca(base.getId(), inputs);
            return base;
        }

        List<EnderecoBairroProximoDTO> proximos =
            geolocalizacaoProvider.buscarBairrosProximos(
                base.getLatitude(),
                base.getLongitude(),
                base.getCidade(),
                base.getUf(),
                40
            );

        if (proximos != null) {
            proximos = proximos.stream()
                .filter(Objects::nonNull)
                .filter(p -> StringUtils.hasText(p.getBairro()))
                .sorted(Comparator.comparingInt(p -> p.getDistanciaMetros() == null ? Integer.MAX_VALUE : p.getDistanciaMetros()))
                .limit(40)
                .toList();

            for (EnderecoBairroProximoDTO p : proximos) {

                Bairro viz = obterOuCriarPorNomeCidadeUf(
                    p.getBairro(),
                    base.getCidade(),
                    base.getUf(),
                    p.getLatitude(),
                    p.getLongitude()
                );

                // Evita duplicar o próprio bairro caso o provider já o devolva na busca.
                if (Objects.equals(viz.getId(), base.getId())) {
                    continue;
                }

                inputs.add(criarInputVizinho(viz.getId(), p.getDistanciaMetros()));
            }
        }

        salvarVizinhanca(base.getId(), inputs);

        return base;
    }

    @Transactional(readOnly = true)
    public Bairro buscar(Long idBairro) {

        if (idBairro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
        }

        return bairroRepository.findById(idBairro)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bairro não encontrado"));
    }

    private BairroVizinhoInputDTO criarInputVizinho(Long idBairroVizinho, Integer distanciaMetros) {
        BairroVizinhoInputDTO in = new BairroVizinhoInputDTO();
        in.setIdBairroVizinho(idBairroVizinho);
        in.setDistanciaMetros(distanciaMetros == null ? 0 : distanciaMetros);
        return in;
    }

    private String limpar(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim();
        if (!StringUtils.hasText(v)) {
            return null;
        }
        v = v.replaceAll("\\s{2,}", " ");
        return v;
    }

    private String normalizar(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim().toLowerCase(Locale.ROOT);
        v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        v = v.replaceAll("[^a-z0-9\\s]", " ");
        v = v.replaceAll("\\s{2,}", " ").trim();
        return v;
    }

    private String limparCep(String cep) {
        if (cep == null) {
            return null;
        }
        String v = cep.replaceAll("\\D", "");
        if (v.length() != 8) {
            return null;
        }
        return v;
    }
}