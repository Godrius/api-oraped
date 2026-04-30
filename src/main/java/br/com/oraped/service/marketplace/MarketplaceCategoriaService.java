package br.com.oraped.service.marketplace;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.enums.AbrangenciaEntrega;
import br.com.oraped.domain.marketplace.CategoriaMarketplace;
import br.com.oraped.domain.marketplace.Marketplace;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.dto.marktplace.CategoriaMarketplaceDisponivelDTO;
import br.com.oraped.repository.marketplace.CategoriaMarketplaceRepository;
import br.com.oraped.service.geolocalizacao.GeolocalizacaoOrigemMarketplaceService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappMarketplaceService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Concentrar as regras de descoberta e validação das categorias disponíveis no marketplace
 * de acordo com a localização atual do cliente.
 *
 * Aplicação:
 * Utilizado após a coleta da localização do cliente para montar o menu de categorias
 * e validar se a categoria escolhida realmente possui atendimento para a região.
 *
 * Utilização:
 * O orquestrador deve delegar para este service toda regra de disponibilidade regional
 * das categorias, evitando acoplamento com a lógica geográfica.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceCategoriaService {

    private final CategoriaMarketplaceRepository categoriaMarketplaceRepository;
    private final SessaoWhatsappMarketplaceService sessaoMarketplaceService;
    private final GeolocalizacaoOrigemMarketplaceService geolocalizacaoOrigemMarketplaceService;

    @Transactional(readOnly = true)
    public List<CategoriaMarketplaceDisponivelDTO> listarCategoriasDisponiveis(
        Marketplace marketplace,
        SessaoAtendimentoWhatsapp sessao
    ) {

        validarContextoMarketplace(marketplace);

        EnderecoResolvidoDTO origem = resolverOrigemSessao(sessao);

        // A regra atual de discovery por categoria considera estabelecimentos que atendam:
        // - todo o território nacional
        // - todo o estado do cliente
        // - o bairro específico do cliente
        List<CategoriaMarketplace> categorias = categoriaMarketplaceRepository.listarCategoriasDisponiveisPorRegiao(
            AbrangenciaEntrega.NACIONAL,
            AbrangenciaEntrega.ESTADO,
            AbrangenciaEntrega.BAIRRO,
            origem.getUf(),
            normalizar(origem.getBairro())
        );

        return categorias.stream()
            .map(categoria -> new CategoriaMarketplaceDisponivelDTO(
                categoria.getId(),
                categoria.getNome()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaMarketplaceDisponivelDTO buscarCategoriaDisponivel(
        Long idCategoria,
        Marketplace marketplace,
        SessaoAtendimentoWhatsapp sessao
    ) {

        if (idCategoria == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "idCategoria é obrigatório"
            );
        }

        return listarCategoriasDisponiveis(marketplace, sessao)
            .stream()
            .filter(categoria -> Objects.equals(categoria.getId(), idCategoria))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Categoria do marketplace não está disponível"
            ));
    }

    private EnderecoResolvidoDTO resolverOrigemSessao(SessaoAtendimentoWhatsapp sessao) {

        validarSessaoComLocalizacao(sessao);

        return geolocalizacaoOrigemMarketplaceService.resolverOrigemCliente(
            sessao.getLatitudeOrigemCliente(),
            sessao.getLongitudeOrigemCliente()
        );
    }

    private void validarContextoMarketplace(Marketplace marketplace) {

        if (marketplace == null || marketplace.getId() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Marketplace não informado para consulta das categorias"
            );
        }
    }

    private void validarSessaoComLocalizacao(SessaoAtendimentoWhatsapp sessao) {

        if (sessao == null || sessao.getId() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Sessão do marketplace não encontrada"
            );
        }

        if (!sessaoMarketplaceService.hasLocalizacaoOrigemMarketplace(sessao.getId())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Localização do cliente ainda não foi informada"
            );
        }
    }

    private String normalizar(String valor) {

        if (!StringUtils.hasText(valor)) {
            return null;
        }

        String texto = valor.trim().toLowerCase(Locale.ROOT);
        texto = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        texto = texto.replaceAll("[^a-z0-9\\s]", " ");
        texto = texto.replaceAll("\\s{2,}", " ").trim();

        return StringUtils.hasText(texto) ? texto : null;
    }
}