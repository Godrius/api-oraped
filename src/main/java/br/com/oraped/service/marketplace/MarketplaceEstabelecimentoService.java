package br.com.oraped.service.marketplace;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.geolocalizacao.EstabelecimentoBairroAtendido;
import br.com.oraped.domain.marketplace.Marketplace;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.dto.marktplace.EstabelecimentoDisponivelMarketplaceDTO;
import br.com.oraped.repository.EstabelecimentoRepository;
import br.com.oraped.service.geolocalizacao.GeolocalizacaoOrigemMarketplaceService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Realizar o discovery de estabelecimentos disponíveis no marketplace
 * com base na localização atual do cliente e na categoria selecionada.
 *
 * Aplicação:
 * Utilizado no fluxo do marketplace após o cliente escolher uma categoria,
 * retornando apenas estabelecimentos ativos, abertos e compatíveis com a
 * abrangência de entrega para a região do cliente.
 *
 * Utilização:
 * Centraliza a lógica de elegibilidade dos estabelecimentos:
 * - filtro por categoria selecionada
 * - filtro por status (ativo + aberto)
 * - filtro por abrangência (bairro, cidade, estado, nacional)
 * - ordenação inicial alfabética
 *
 * Observação:
 * Hoje a ordenação é alfabética, mas esta classe já é o ponto certo para,
 * no futuro, aplicar regras comerciais de priorização.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceEstabelecimentoService {

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final GeolocalizacaoOrigemMarketplaceService geolocalizacaoOrigemMarketplaceService;

    @Transactional(readOnly = true)
    public List<EstabelecimentoDisponivelMarketplaceDTO> listarEstabelecimentosDisponiveis(
        Marketplace marketplace,
        SessaoAtendimentoWhatsapp sessao
    ) {

        if (marketplace == null || sessao == null) {
            return List.of();
        }

        if (sessao.getIdCategoriaMarketplace() == null) {
            return List.of();
        }

        if (sessao.getLatitudeOrigemCliente() == null || sessao.getLongitudeOrigemCliente() == null) {
            return List.of();
        }

        // A localização do cliente é resolvida com cache persistente antes de aplicar o discovery.
        EnderecoResolvidoDTO origemCliente = geolocalizacaoOrigemMarketplaceService.resolverOrigemCliente(
            sessao.getLatitudeOrigemCliente(),
            sessao.getLongitudeOrigemCliente()
        );

        List<Estabelecimento> filtrados = estabelecimentoRepository.findAll()
            .stream()
            .filter(Objects::nonNull)
            .filter(Estabelecimento::isAtivo)
            .filter(Estabelecimento::isAberto)
            .filter(estabelecimento -> estabelecimento.getCategoriaMarketplace() != null)
            .filter(estabelecimento -> Objects.equals(
                estabelecimento.getCategoriaMarketplace().getId(),
                sessao.getIdCategoriaMarketplace()
            ))
            .filter(estabelecimento -> atendeLocalizacao(estabelecimento, origemCliente))
            .sorted(Comparator.comparing(estabelecimento -> safeLower(estabelecimento.getNome())))
            .toList();

        return filtrados.stream()
            .map(this::toDTO)
            .toList();
    }

    /**
     * Regra central de atendimento geográfico.
     *
     * NACIONAL:
     * - sempre atende
     *
     * ESTADO:
     * - atende se a UF base do estabelecimento for igual à UF do cliente
     *
     * CIDADE:
     * - atende se cidade e UF base do estabelecimento forem iguais às do cliente
     *
     * BAIRRO:
     * - atende se o bairro do cliente estiver explicitamente cadastrado
     *   na lista de bairros atendidos do estabelecimento
     */
    private boolean atendeLocalizacao(
        Estabelecimento estabelecimento,
        EnderecoResolvidoDTO origemCliente
    ) {

        if (estabelecimento.getAbrangenciaEntrega() == null || origemCliente == null) {
            return false;
        }

        return switch (estabelecimento.getAbrangenciaEntrega()) {
            case NACIONAL -> true;
            case ESTADO -> atendeEstado(estabelecimento, origemCliente);
            case CIDADE -> atendeCidade(estabelecimento, origemCliente);
            case BAIRRO -> atendeBairro(estabelecimento, origemCliente);
        };
    }

    private boolean atendeEstado(
        Estabelecimento estabelecimento,
        EnderecoResolvidoDTO origemCliente
    ) {

        Bairro bairroBase = estabelecimento.getBairro();

        return bairroBase != null
            && StringUtils.hasText(bairroBase.getUf())
            && StringUtils.hasText(origemCliente.getUf())
            && normalizar(bairroBase.getUf()).equals(normalizar(origemCliente.getUf()));
    }

    private boolean atendeCidade(
        Estabelecimento estabelecimento,
        EnderecoResolvidoDTO origemCliente
    ) {

        Bairro bairroBase = estabelecimento.getBairro();

        return bairroBase != null
            && StringUtils.hasText(bairroBase.getCidade())
            && StringUtils.hasText(bairroBase.getUf())
            && StringUtils.hasText(origemCliente.getCidade())
            && StringUtils.hasText(origemCliente.getUf())
            && normalizar(bairroBase.getCidade()).equals(normalizar(origemCliente.getCidade()))
            && normalizar(bairroBase.getUf()).equals(normalizar(origemCliente.getUf()));
    }

    private boolean atendeBairro(
        Estabelecimento estabelecimento,
        EnderecoResolvidoDTO origemCliente
    ) {

        if (!StringUtils.hasText(origemCliente.getBairro())
            || !StringUtils.hasText(origemCliente.getCidade())
            || !StringUtils.hasText(origemCliente.getUf())
        ) {
            return false;
        }

        if (estabelecimento.getBairrosAtendidos() == null || estabelecimento.getBairrosAtendidos().isEmpty()) {
            return false;
        }

        return estabelecimento.getBairrosAtendidos()
            .stream()
            .map(EstabelecimentoBairroAtendido::getBairro)
            .filter(Objects::nonNull)
            .anyMatch(bairroAtendido -> mesmoBairro(bairroAtendido, origemCliente));
    }

    private boolean mesmoBairro(
        Bairro bairroAtendido,
        EnderecoResolvidoDTO origemCliente
    ) {

        return StringUtils.hasText(bairroAtendido.getNome())
            && StringUtils.hasText(bairroAtendido.getCidade())
            && StringUtils.hasText(bairroAtendido.getUf())
            && normalizar(bairroAtendido.getNome()).equals(normalizar(origemCliente.getBairro()))
            && normalizar(bairroAtendido.getCidade()).equals(normalizar(origemCliente.getCidade()))
            && normalizar(bairroAtendido.getUf()).equals(normalizar(origemCliente.getUf()));
    }

    /**
     * Mapeamento leve para exibição no marketplace.
     */
    private EstabelecimentoDisponivelMarketplaceDTO toDTO(Estabelecimento estabelecimento) {

        String bairro = null;
        String cidade = null;
        String uf = null;

        if (estabelecimento.getBairro() != null) {
            bairro = estabelecimento.getBairro().getNome();
            cidade = estabelecimento.getBairro().getCidade();
            uf = estabelecimento.getBairro().getUf();
        }

        return new EstabelecimentoDisponivelMarketplaceDTO(
            estabelecimento.getId(),
            estabelecimento.getNome(),
            bairro,
            cidade,
            uf,
            estabelecimento.getValorPedidoMinimo()
        );
    }

    private String safeLower(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }
}