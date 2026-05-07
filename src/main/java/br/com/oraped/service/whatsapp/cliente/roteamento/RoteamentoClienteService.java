package br.com.oraped.service.whatsapp.cliente.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.marketplace.Marketplace;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.marketplace.MarketplaceService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.cliente.MenuClienteService;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento dos comandos do cliente no WhatsApp.
 *
 * Aplicação:
 * Recebe o comando já interpretado pelo orquestrador principal e delega
 * para roteamentos especializados por domínio funcional.
 *
 * Utilização:
 * Deve permanecer fino, sem regras específicas de marketplace, catálogo,
 * carrinho, pedido, revisão ou notificação.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoClienteService {

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final MarketplaceService marketplaceService;

    private final RoteamentoMarketplaceClienteService roteamentoMarketplaceClienteService;
    private final RoteamentoCarrinhoClienteService roteamentoCarrinhoClienteService;
    private final RoteamentoCatalogoClienteService roteamentoCatalogoClienteService;
    private final RoteamentoPedidoClienteService roteamentoPedidoClienteService;
    private final RoteamentoRevisaoPedidoClienteService roteamentoRevisaoPedidoClienteService;
    private final RoteamentoNotificacaoClienteService roteamentoNotificacaoClienteService;

    private final MenuClienteService menus;
    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotearCliente(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        String whatsappReceptor,
        String phoneNumberId,
        String idCorrelacao,
        String wamidEntrada,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        if (acao == null) {
            return new RoteamentoResultado(
                "comando_desconhecido",
                montarMenuFallback(estabelecimento, whatsappCliente, idSessao)
            );
        }

        if (isComandoMarketplace(acao)) {
            return roteamentoMarketplaceClienteService.rotear(
                whatsappCliente,
                idSessao,
                cmd
            );
        }

        if (isComandoCarrinho(acao)) {
            return roteamentoCarrinhoClienteService.rotear(
                estabelecimento,
                whatsappCliente,
                idSessao,
                cmd
            );
        }

        if (isComandoCatalogo(acao)) {
            return roteamentoCatalogoClienteService.rotear(
                estabelecimento,
                whatsappCliente,
                idSessao,
                cmd
            );
        }

        if (isComandoPedido(acao)) {
            return roteamentoPedidoClienteService.rotear(
                estabelecimento,
                whatsappCliente,
                whatsappReceptor,
                phoneNumberId,
                idSessao,
                cmd
            );
        }

        if (isComandoRevisaoPedido(acao)) {
            return roteamentoRevisaoPedidoClienteService.rotear(
                estabelecimento,
                whatsappCliente,
                cmd
            );
        }

        if (isComandoNotificacao(acao)) {
            return roteamentoNotificacaoClienteService.rotear(
                estabelecimento,
                whatsappCliente,
                phoneNumberId,
                idCorrelacao,
                wamidEntrada,
                cmd
            );
        }

        return new RoteamentoResultado(
            "comando_desconhecido",
            montarMenuFallback(estabelecimento, whatsappCliente, idSessao)
        );
    }

    private boolean isComandoMarketplace(String acao) {
        return "MARKETPLACE_CATEGORIA".equals(acao)
            || "MARKETPLACE_ESTABELECIMENTO".equals(acao)
            || "TROCAR_ESTABELECIMENTO_MARKETPLACE".equals(acao)
            || "TROCAR_CATEGORIA_MARKETPLACE".equals(acao)
            || "TROCAR_LOCALIZACAO_MARKETPLACE".equals(acao);
    }

    private boolean isComandoCarrinho(String acao) {
        return "VISUALIZAR_CARRINHO".equals(acao)
            || "LIMPAR_CARRINHO".equals(acao);
    }

    private boolean isComandoCatalogo(String acao) {
        return "FAZER_PEDIDO".equals(acao)
            || "INCLUIR_OUTRO_ITEM".equals(acao)
            || "LISTA_CATEGORIAS_PAG".equals(acao)
            || "LISTA_PRODUTOS".equals(acao)
            || "LISTA_PRODUTOS_PAG".equals(acao)
            || "SELECIONAR_PRODUTO".equals(acao)
            || "ESCOLHER_OUTRO_PRODUTO".equals(acao);
    }

    private boolean isComandoPedido(String acao) {
        return "INFORMAR_ENDERECO".equals(acao)
            || "FAZER_PEDIDO_COM_ENDERECO_ANTERIOR".equals(acao)
            || "INFORMAR_OUTRO_ENDERECO".equals(acao)
            || "SELECIONAR_PAGAMENTO".equals(acao)
            || "TROCO".equals(acao)
            || "ENVIAR_PEDIDO".equals(acao)
            || "SELECIONAR_TAMANHO".equals(acao)
            || "SELECIONAR_COMPLEMENTO".equals(acao)
            || "NAO_QUERO_COMPLEMENTO".equals(acao)
            || "COMPRAR_PRODUTO".equals(acao)
            || "LISTAR_QUANTIDADES".equals(acao)
            || "ADICIONAR_PRODUTO".equals(acao)
            || "SOLICITAR_QUANTIDADE".equals(acao);
    }

    private boolean isComandoRevisaoPedido(String acao) {
        return "ULTIMO_PEDIDO".equals(acao)
            || "PEDIDO_REVISAR".equals(acao)
            || "REVISAO_ADICIONAR_ITENS".equals(acao)
            || "REVISAO_LISTA_PRODUTOS".equals(acao)
            || "REVISAO_LISTA_PRODUTOS_PAG".equals(acao)
            || "REVISAO_LISTAR_QUANTIDADES".equals(acao)
            || "REVISAO_ADICIONAR_PRODUTO".equals(acao)
            || "REVISAO_CANCELAR_PEDIDO".equals(acao)
            || "REVISAO_CONFIRMAR_ENTREGA".equals(acao);
    }

    private boolean isComandoNotificacao(String acao) {
        return "CADASTRAR_NOTIFICACAO_ESTABELECIMENTO_ABERTO".equals(acao);
    }

    private MensagemWhatsappSaidaDTO montarMenuFallback(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        if (estabelecimento != null) {
            return menus.montarMenuPrincipalSemSaudacao(
                estabelecimento,
                whatsappCliente,
                idSessao
            );
        }

        SessaoAtendimentoWhatsapp sessao = idSessao == null
            ? null
            : sessaoService.buscarPorId(idSessao);

        if (sessao != null && sessao.getIdMarketplace() != null) {
            Marketplace marketplace = marketplaceService.buscarPorId(sessao.getIdMarketplace());

            return msg.texto(
                whatsappCliente,
                "📍 Você está no marketplace " + marketplace.getNome() + ".\n\n" +
                    "Escolha uma categoria para continuar."
            );
        }

        return msg.texto(
            whatsappCliente,
            "⚠️ Não consegui identificar o fluxo atual da conversa.\n\nTente novamente."
        );
    }
}