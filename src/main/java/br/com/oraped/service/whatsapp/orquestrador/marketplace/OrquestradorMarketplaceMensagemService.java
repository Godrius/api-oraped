package br.com.oraped.service.whatsapp.orquestrador.marketplace;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.marketplace.Marketplace;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.dto.marktplace.CategoriaMarketplaceDisponivelDTO;
import br.com.oraped.dto.marktplace.EstabelecimentoDisponivelMarketplaceDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Montar as mensagens do fluxo de marketplace relacionadas à descoberta,
 * seleção de categorias e exibição dos estabelecimentos disponíveis.
 *
 * Aplicação:
 * Utilizado pelo orquestrador quando o cliente já informou sua localização
 * e interage com as etapas iniciais do marketplace.
 *
 * Utilização:
 * Deve encapsular apenas a montagem das mensagens de saída, mantendo textos
 * e estrutura visual do WhatsApp fora dos services de regra de negócio.
 *
 * Observação:
 * Diferencia:
 * - primeira captura de localização
 * - reutilização da localização da sessão
 * - refinamento da localização por CEP
 */
@Service
@RequiredArgsConstructor
public class OrquestradorMarketplaceMensagemService {

    private static final String DESCRICAO_ITEM_CATEGORIA = "Ver estabelecimentos disponíveis";
    private static final String TEXTO_SEM_CATEGORIAS =
        "Ainda não encontrei opções disponíveis para sua região no momento.\n\n" +
        "Se quiser, você pode digitar um *CEP* para refinar a localização ou tentar novamente mais tarde 🙂";
    private static final String TEXTO_SEM_ESTABELECIMENTOS =
        "No momento não encontrei estabelecimentos disponíveis nessa categoria para a sua região.";

    private final WhatsappMensagemFactory msg;

    // =========================================================
    // MENU DE CATEGORIAS - APÓS NOVA LOCALIZAÇÃO
    // =========================================================
    public MensagemWhatsappSaidaDTO montarMenuCategoriasAposReceberLocalizacao(
        String whatsappCliente,
        Marketplace marketplace,
        EnderecoResolvidoDTO endereco,
        List<CategoriaMarketplaceDisponivelDTO> categorias
    ) {

        String prefixo = montarTextoLocalizacaoDetectada(endereco);

        return montarMenuCategoriasInterno(
            whatsappCliente,
            marketplace,
            categorias,
            prefixo
        );
    }

    // =========================================================
    // MENU DE CATEGORIAS - LOCALIZAÇÃO JÁ EXISTENTE
    // =========================================================
    public MensagemWhatsappSaidaDTO montarMenuCategoriasComLocalizacaoExistente(
        String whatsappCliente,
        Marketplace marketplace,
        EnderecoResolvidoDTO endereco,
        List<CategoriaMarketplaceDisponivelDTO> categorias
    ) {

        String prefixo = montarTextoLocalizacaoAtual(endereco);

        return montarMenuCategoriasInterno(
            whatsappCliente,
            marketplace,
            categorias,
            prefixo
        );
    }

    // =========================================================
    // MENU DE CATEGORIAS - APÓS RECEBER CEP
    // =========================================================
    public MensagemWhatsappSaidaDTO montarMenuCategoriasAposReceberCep(
        String whatsappCliente,
        Marketplace marketplace,
        EnderecoResolvidoDTO endereco,
        List<CategoriaMarketplaceDisponivelDTO> categorias
    ) {

        String prefixo = montarTextoLocalizacaoRefinadaPorCep(endereco);

        return montarMenuCategoriasInterno(
            whatsappCliente,
            marketplace,
            categorias,
            prefixo
        );
    }

    // =========================================================
    // MENU DE ESTABELECIMENTOS
    // =========================================================
    public MensagemWhatsappSaidaDTO montarMenuEstabelecimentos(
        String whatsappCliente,
        CategoriaMarketplaceDisponivelDTO categoria,
        List<EstabelecimentoDisponivelMarketplaceDTO> estabelecimentos
    ) {

        if (estabelecimentos == null || estabelecimentos.isEmpty()) {
            return msg.texto(whatsappCliente, TEXTO_SEM_ESTABELECIMENTOS);
        }

        String nomeCategoria = categoria != null && StringUtils.hasText(categoria.getNome())
            ? categoria.getNome().trim()
            : "selecionada";

        List<MensagemInterativaItemListaWhatsappDTO> itens = estabelecimentos.stream()
            .filter(estabelecimento ->
                estabelecimento != null
                    && estabelecimento.getId() != null
                    && StringUtils.hasText(estabelecimento.getNome())
            )
            .map(estabelecimento -> MensagemInterativaItemListaWhatsappDTO.builder()
                .id("COMANDO|MARKETPLACE_ESTABELECIMENTO|" + estabelecimento.getId())
                .title(msg.truncWord(estabelecimento.getNome(), 24))
                .description(msg.truncWord(montarDescricaoEstabelecimento(estabelecimento), 72))
                .build()
            )
            .toList();

        if (itens.isEmpty()) {
            return msg.texto(whatsappCliente, TEXTO_SEM_ESTABELECIMENTOS);
        }

        return msg.lista(
            whatsappCliente,
            "Escolha um estabelecimento da categoria " + nomeCategoria + ".",
            "Ver lojas",
            "Estabelecimentos disponíveis",
            itens
        );
    }

    // =========================================================
    // CORE - CATEGORIAS
    // =========================================================
    private MensagemWhatsappSaidaDTO montarMenuCategoriasInterno(
        String whatsappCliente,
        Marketplace marketplace,
        List<CategoriaMarketplaceDisponivelDTO> categorias,
        String prefixo
    ) {

        if (categorias == null || categorias.isEmpty()) {
            return msg.texto(
                whatsappCliente,
                prefixo + TEXTO_SEM_CATEGORIAS
            );
        }

        String nomeMarketplace = marketplace != null && StringUtils.hasText(marketplace.getNome())
            ? marketplace.getNome().trim()
            : "Oraped";

        List<MensagemInterativaItemListaWhatsappDTO> itens = categorias.stream()
            .filter(categoria ->
                categoria != null
                    && categoria.getId() != null
                    && StringUtils.hasText(categoria.getNome())
            )
            .map(categoria -> MensagemInterativaItemListaWhatsappDTO.builder()
                .id("COMANDO|MARKETPLACE_CATEGORIA|" + categoria.getId())
                .title(msg.truncWord(categoria.getNome(), 24))
                .description(DESCRICAO_ITEM_CATEGORIA)
                .build()
            )
            .toList();

        if (itens.isEmpty()) {
            return msg.texto(
                whatsappCliente,
                prefixo + TEXTO_SEM_CATEGORIAS
            );
        }

        return msg.lista(
            whatsappCliente,
            prefixo +
                "Escolha uma categoria disponível agora no marketplace " + nomeMarketplace + ".",
            "Ver categorias",
            "Categorias disponíveis",
            itens
        );
    }

    // =========================================================
    // TEXTOS - LOCALIZAÇÃO
    // =========================================================
    private String montarTextoLocalizacaoDetectada(EnderecoResolvidoDTO endereco) {

        String bairro = extrairBairro(endereco);

        if (bairro != null) {
            return "📍 Localização recebida com sucesso.\n\n" +
                "Identifiquei que você está em *" + bairro + "*.\n\n";
        }

        return "📍 Localização recebida com sucesso.\n\n";
    }

    private String montarTextoLocalizacaoAtual(EnderecoResolvidoDTO endereco) {

        String bairro = extrairBairro(endereco);

        if (bairro != null) {
            return "📍 Utilizando sua localização atual em *" + bairro + "*.\n\n";
        }

        return "📍 Utilizando sua localização atual.\n\n";
    }

    private String montarTextoLocalizacaoRefinadaPorCep(EnderecoResolvidoDTO endereco) {

        String bairro = extrairBairro(endereco);

        if (bairro != null) {
            return "📍 Localização identificada pelo CEP.\n\n" +
                "Agora vou considerar *" + bairro + "* como referência.\n\n";
        }

        return "📍 Localização identificada com base no CEP informado.\n\n";
    }

    private String extrairBairro(EnderecoResolvidoDTO endereco) {

        if (endereco == null || !StringUtils.hasText(endereco.getBairro())) {
            return null;
        }

        return endereco.getBairro().trim();
    }

    // =========================================================
    // TEXTO DE APOIO - ESTABELECIMENTOS
    // =========================================================
    private String montarDescricaoEstabelecimento(EstabelecimentoDisponivelMarketplaceDTO estabelecimento) {

        StringBuilder descricao = new StringBuilder();

        if (StringUtils.hasText(estabelecimento.getBairro())) {
            descricao.append(estabelecimento.getBairro().trim());
        }

        if (StringUtils.hasText(estabelecimento.getCidade())) {
            if (descricao.length() > 0) {
                descricao.append(" - ");
            }
            descricao.append(estabelecimento.getCidade().trim());
        }

        if (StringUtils.hasText(estabelecimento.getUf())) {
            if (descricao.length() > 0) {
                descricao.append("/");
            }
            descricao.append(estabelecimento.getUf().trim());
        }

        if (estabelecimento.getValorPedidoMinimo() != null
            && estabelecimento.getValorPedidoMinimo().compareTo(BigDecimal.ZERO) > 0) {

            if (descricao.length() > 0) {
                descricao.append(" | ");
            }

            descricao.append("Pedido mín. ")
                .append(msg.formatarMoeda(estabelecimento.getValorPedidoMinimo()));
        }

        if (descricao.length() == 0) {
            descricao.append("Disponível para sua região");
        }

        return descricao.toString();
    }
    
    
    // =========================================================
    // MENU DE CATEGORIAS - APÓS VOLTAR AO TOPO DO MARKETPLACE
    // =========================================================
    public MensagemWhatsappSaidaDTO montarMenuCategoriasAposRetornoAoMarketplace(
        String whatsappCliente,
        Marketplace marketplace,
        EnderecoResolvidoDTO endereco,
        List<CategoriaMarketplaceDisponivelDTO> categorias
    ) {

        String prefixo = "🛍️ Tudo bem! Vamos voltar ao marketplace.\n\n";;

        String bairro = extrairBairro(endereco);

        if (bairro != null) {
        	prefixo = "🛍️ Tudo bem! Vamos voltar ao marketplace.\n\n" +
                "Continuarei usando sua localização em *" + bairro + "*.\n\n";
        }

        return montarMenuCategoriasInterno(
            whatsappCliente,
            marketplace,
            categorias,
            prefixo
        );
    }
}