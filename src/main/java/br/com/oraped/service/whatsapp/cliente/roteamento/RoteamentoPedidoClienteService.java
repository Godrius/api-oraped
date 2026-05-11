package br.com.oraped.service.whatsapp.cliente.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.cliente.EnderecoClienteService;
import br.com.oraped.service.whatsapp.cliente.MenuClienteService;
import br.com.oraped.service.whatsapp.cliente.PedidoClienteService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappClienteService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Rotear os comandos do fluxo de pedido do cliente no WhatsApp.
 *
 * Aplicação:
 * Centraliza ações de endereço, pagamento, troco, complementos, quantidade
 * e inclusão do produto no carrinho.
 *
 * Utilização:
 * Mantém o RoteamentoClienteService enxuto, delegando para PedidoClienteService
 * as regras de negócio do pedido.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoPedidoClienteService {

    private final SessaoWhatsappClienteService sessaoClienteService;

    private final OrquestradorParseService parse;

    private final MenuClienteService menus;
    private final EnderecoClienteService enderecoClienteService;
    private final PedidoClienteService pedidoClienteService;

    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        String whatsappReceptor,
        String phoneNumberId,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "INFORMAR_ENDERECO":
                return pedidoClienteService.tratarFluxoEndereco(
                    estabelecimento,
                    whatsappCliente,
                    idSessao
                );

            case "FAZER_PEDIDO_COM_ENDERECO_ANTERIOR":
                return pedidoClienteService.tratarConfirmacaoEnderecoAnterior(
                    estabelecimento,
                    whatsappCliente,
                    whatsappReceptor,
                    phoneNumberId,
                    idSessao
                );

            case "INFORMAR_OUTRO_ENDERECO":
                return informarOutroEndereco(whatsappCliente, idSessao);

            case "SELECIONAR_PAGAMENTO":
                return selecionarPagamento(
                    estabelecimento,
                    whatsappCliente,
                    whatsappReceptor,
                    phoneNumberId,
                    idSessao,
                    cmd
                );

            case "TROCO":
                return pedidoClienteService.tratarTroco(
                    estabelecimento,
                    whatsappCliente,
                    whatsappReceptor,
                    phoneNumberId,
                    idSessao,
                    cmd.getParte(2)
                );

            case "ENVIAR_PEDIDO":
                sessaoClienteService.desmarcarAguardandoConfirmacaoFinal(idSessao);
                return pedidoClienteService.tratarEnvioPedidoDefinitivo(
                    estabelecimento,
                    whatsappCliente,
                    idSessao
                );

            case "SELECIONAR_TAMANHO":
                return selecionarTamanho(
                    estabelecimento,
                    whatsappCliente,
                    idSessao,
                    cmd
                );
                
            case "SELECIONAR_COMPLEMENTO":
                return selecionarComplemento(
                    estabelecimento,
                    whatsappCliente,
                    idSessao,
                    cmd
                );

            case "NAO_QUERO_COMPLEMENTO":
                return pedidoClienteService.tratarNaoQueroComplemento(
                    estabelecimento,
                    whatsappCliente,
                    idSessao
                );

            case "COMPRAR_PRODUTO":
            case "LISTAR_QUANTIDADES":
                return listarQuantidades(
                    estabelecimento,
                    whatsappCliente,
                    idSessao,
                    cmd
                );

            case "ADICIONAR_PRODUTO":
                return adicionarProduto(
                    estabelecimento,
                    whatsappCliente,
                    idSessao,
                    cmd
                );

            case "SOLICITAR_QUANTIDADE":
                return solicitarQuantidadeManual(
                    whatsappCliente,
                    idSessao,
                    cmd
                );

            default:
                return new RoteamentoResultado(
                    "pedido_cliente_comando_desconhecido",
                    msg.texto(
                        whatsappCliente,
                        "⚠️ Não consegui identificar a ação do pedido.\n\nTente novamente."
                    )
                );
        }
    }

    private RoteamentoResultado informarOutroEndereco(
        String whatsappCliente,
        Long idSessao
    ) {

        sessaoClienteService.marcarAguardandoCepEntrega(idSessao);

        return new RoteamentoResultado(
            "solicitar_cep_entrega",
            enderecoClienteService.montarSolicitacaoCepEntrega(whatsappCliente, true)
        );
    }

    private RoteamentoResultado selecionarPagamento(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        String whatsappReceptor,
        String phoneNumberId,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        FormaPagamentoPedido formaPagamento = parse.parseFormaPagamento(cmd.getParte(2));

        return pedidoClienteService.tratarSelecaoPagamento(
            estabelecimento,
            whatsappCliente,
            whatsappReceptor,
            phoneNumberId,
            idSessao,
            formaPagamento
        );
    }

    private RoteamentoResultado selecionarTamanho(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    ComandoWhatsapp cmd
	) {

	    Long idProduto = parse.parseLongObrigatorio(
	        cmd.getParte(2),
	        "idProduto"
	    );

	    Long idOpcaoTamanhoProduto = parse.parseLongObrigatorio(
	        cmd.getParte(3),
	        "idOpcaoTamanhoProduto"
	    );

	    return pedidoClienteService.tratarSelecionarTamanho(
	        estabelecimento,
	        whatsappCliente,
	        idSessao,
	        idProduto,
	        idOpcaoTamanhoProduto
	    );
	}

    private RoteamentoResultado selecionarComplemento(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        Long idComplemento = parse.parseLongObrigatorio(
            cmd.getParte(2),
            "idComplemento"
        );

        return pedidoClienteService.tratarSelecionarComplemento(
            estabelecimento,
            whatsappCliente,
            idSessao,
            idComplemento
        );
    }

    private RoteamentoResultado listarQuantidades(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        Long idCategoria = parse.parseLongObrigatorio(
            cmd.getParte(2),
            "idCategoria"
        );

        Integer quantidadeMultipla = parse.parseIntObrigatorio(
            cmd.getParte(3),
            "quantidadeMultipla"
        );

        Long idProduto = parse.parseLongObrigatorio(
            cmd.getParte(4),
            "idProduto"
        );

        return new RoteamentoResultado(
            "listar_quantidades",
            menus.montarListaQuantidades(
                estabelecimento,
                whatsappCliente,
                idSessao,
                idCategoria,
                quantidadeMultipla,
                idProduto
            )
        );
    }

    private RoteamentoResultado adicionarProduto(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        Long idProduto = parse.parseLongObrigatorio(
            cmd.getParte(2),
            "idProduto"
        );

        Integer quantidade = parse.parseIntObrigatorio(
            cmd.getParte(3),
            "quantidade"
        );

        return pedidoClienteService.tratarAdicionarProduto(
            estabelecimento,
            whatsappCliente,
            idSessao,
            idProduto,
            quantidade
        );
    }

    private RoteamentoResultado solicitarQuantidadeManual(
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        Long idProduto = parse.parseLongObrigatorio(
            cmd.getParte(2),
            "idProduto"
        );

        // O texto livre seguinte será tratado pelo fluxo de quantidade manual.
        sessaoClienteService.marcarAguardandoQuantidadeManual(idSessao, idProduto);

        MensagemWhatsappSaidaDTO mensagem = msg.texto(
            whatsappCliente,
            "Certo! Me informe a quantidade desejada para o produto.\n\nExemplo: 15"
        );

        return new RoteamentoResultado(
            "solicitar_quantidade_manual",
            mensagem
        );
    }
}