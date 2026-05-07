package br.com.oraped.service.whatsapp.cliente.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.carrinho.CarrinhoService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.cliente.MenuClienteService;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.sessao.SessaoItemCarrinhoEmMontagemService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Rotear os comandos de carrinho executados pelo cliente no WhatsApp.
 *
 * Aplicação:
 * Usado pelo RoteamentoClienteService para delegar visualização e limpeza
 * do carrinho em andamento.
 *
 * Utilização:
 * Mantém as ações de carrinho isoladas do roteador principal do cliente,
 * evitando que regras de limpeza e visualização fiquem misturadas ao fluxo
 * de catálogo, pedido, revisão ou marketplace.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoCarrinhoClienteService {

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final SessaoItemCarrinhoEmMontagemService itemEmMontagemService;
    private final CarrinhoService carrinhoPersistenciaService;

    private final MenuClienteService menus;
    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "VISUALIZAR_CARRINHO":
                return visualizarCarrinho(estabelecimento, whatsappCliente, idSessao);

            case "LIMPAR_CARRINHO":
                return limparCarrinho(estabelecimento, whatsappCliente, idSessao);

            default:
                return new RoteamentoResultado(
                    "carrinho_comando_desconhecido",
                    msg.texto(
                        whatsappCliente,
                        "⚠️ Não consegui identificar a ação do carrinho.\n\nTente novamente."
                    )
                );
        }
    }

    private RoteamentoResultado visualizarCarrinho(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        return new RoteamentoResultado(
            "visualizar_carrinho",
            menus.montarVisualizacaoCarrinho(estabelecimento, whatsappCliente, idSessao)
        );
    }

    private RoteamentoResultado limparCarrinho(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        // A limpeza precisa remover sessão temporária, carrinho persistido e item ainda em montagem.
        sessaoService.limparPedidoEmAndamento(idSessao);
        carrinhoPersistenciaService.limparCarrinho(idSessao);
        itemEmMontagemService.limparMontagem(idSessao);

        return new RoteamentoResultado(
            "carrinho_limpo",
            menus.montarCarrinhoLimpo(estabelecimento, whatsappCliente, idSessao)
        );
    }
}