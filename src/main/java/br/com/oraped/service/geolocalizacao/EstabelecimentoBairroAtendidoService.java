package br.com.oraped.service.geolocalizacao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.AbrangenciaEntrega;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.geolocalizacao.EstabelecimentoBairroAtendido;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.repository.geolocalizacao.EstabelecimentoBairroAtendidoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstabelecimentoBairroAtendidoService {

    private final BairroService bairroService;
    private final EstabelecimentoBairroAtendidoRepository repository;

    @Transactional(readOnly = true)
    public List<Bairro> listarBairrosAtendidosDaVizinhanca(Estabelecimento estabelecimento) {

        validarEstabelecimento(estabelecimento);
        validarAbrangenciaBairro(estabelecimento);

        Long idBairroBase = estabelecimento.getBairro() == null ? null : estabelecimento.getBairro().getId();
        if (idBairroBase == null) {
            return List.of();
        }

        List<Bairro> vizinhanca = bairroService.listarVizinhosOrdenados(idBairroBase);
        if (vizinhanca.isEmpty()) {
            return List.of();
        }

        List<Long> idsVizinhos = vizinhanca.stream()
            .map(Bairro::getId)
            .toList();

        Set<Long> idsAtendidos = listarIdsAtendidos(estabelecimento.getId(), idsVizinhos);

        List<Bairro> resultado = new ArrayList<>();
        for (Bairro bairro : vizinhanca) {
            if (idsAtendidos.contains(bairro.getId())) {
                resultado.add(bairro);
            }
        }

        return resultado;
    }

    @Transactional(readOnly = true)
    public Set<Long> listarIdsAtendidos(Long idEstabelecimento, List<Long> idsBairrosVizinhos) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (idsBairrosVizinhos == null || idsBairrosVizinhos.isEmpty()) {
            return Set.of();
        }

        List<EstabelecimentoBairroAtendido> vinculados =
            repository.findByEstabelecimentoIdAndBairroIdIn(idEstabelecimento, idsBairrosVizinhos);

        Set<Long> ids = new HashSet<>();
        for (EstabelecimentoBairroAtendido item : vinculados) {
            if (item != null && item.getBairro() != null && item.getBairro().getId() != null) {
                ids.add(item.getBairro().getId());
            }
        }

        return ids;
    }

    @Transactional
    public boolean adicionarBairroAtendido(Estabelecimento estabelecimento, Long idBairro) {

        validarEstabelecimento(estabelecimento);
        validarAbrangenciaBairro(estabelecimento);

        if (idBairro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
        }

        if (repository.existsByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro)) {
            return false;
        }

        Bairro bairro = bairroService.buscar(idBairro);

        EstabelecimentoBairroAtendido item = new EstabelecimentoBairroAtendido();
        item.setEstabelecimento(estabelecimento);
        item.setBairro(bairro);

        repository.save(item);
        return true;
    }

    @Transactional
    public boolean removerBairroAtendido(Estabelecimento estabelecimento, Long idBairro) {

        validarEstabelecimento(estabelecimento);
        validarAbrangenciaBairro(estabelecimento);

        if (idBairro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
        }

        boolean exists = repository.existsByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro);

        if (!exists) {
            return false;
        }

        repository.deleteByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isBairroAtendido(Estabelecimento estabelecimento, Long idBairro) {

        validarEstabelecimento(estabelecimento);
        validarAbrangenciaBairro(estabelecimento);

        if (idBairro == null) {
            return false;
        }

        return repository.existsByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro);
    }

    @Transactional(readOnly = true)
    public boolean isEnderecoAtendido(Estabelecimento estabelecimento, EnderecoResolvidoDTO end) {

        validarEstabelecimento(estabelecimento);

        if (estabelecimento.getAbrangenciaEntrega() != AbrangenciaEntrega.BAIRRO) {
            return true;
        }

        if (end == null) {
            return false;
        }

        String bairro = end.getBairro();
        String cidade = end.getCidade();
        String uf = end.getUf();

        if (bairro == null || bairro.trim().isEmpty()
            || cidade == null || cidade.trim().isEmpty()
            || uf == null || uf.trim().isEmpty()) {
            return false;
        }

        Bairro bairroResolvido = bairroService.obterOuCriarPorNomeCidadeUf(
            bairro,
            cidade,
            uf,
            end.getLatitude(),
            end.getLongitude()
        );

        return bairroResolvido != null
            && bairroResolvido.getId() != null
            && repository.existsByEstabelecimentoIdAndBairroId(estabelecimento.getId(), bairroResolvido.getId());
    }

    private void validarEstabelecimento(Estabelecimento estabelecimento) {
        if (estabelecimento == null || estabelecimento.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }
    }

    private void validarAbrangenciaBairro(Estabelecimento estabelecimento) {
        if (estabelecimento.getAbrangenciaEntrega() != AbrangenciaEntrega.BAIRRO) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A configuração de bairros atendidos só é permitida para estabelecimentos com abrangência BAIRRO"
            );
        }
    }
}