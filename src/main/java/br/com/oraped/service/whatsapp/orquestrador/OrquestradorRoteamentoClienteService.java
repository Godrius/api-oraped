package br.com.oraped.service.whatsapp.orquestrador;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.PedidoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.PedidoService;
import br.com.oraped.service.whatsapp.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorRoteamentoClienteService {

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final PedidoService pedidoService;
    private final EstabelecimentoService estabelecimentoService;
    
    private final OrquestradorParseService parse;
    private final OrquestradorMenusClienteService menus;
    private final OrquestradorFluxoClienteService fluxo;
    private final OrquestradorRevisaoPedidoService revisao;
    
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
            return new RoteamentoResultado("comando_desconhecido", menus.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente));
        }

        switch (acao) {

            case "FAZER_PEDIDO":
            case "INCLUIR_OUTRO_ITEM":
                return new RoteamentoResultado("lista_categorias", menus.montarListaCategorias(estabelecimento, whatsappCliente));

            case "VISUALIZAR_CARRINHO":
                return new RoteamentoResultado("visualizar_carrinho", menus.montarVisualizacaoCarrinho(estabelecimento, whatsappCliente, idSessao));

            case "LIMPAR_CARRINHO":
                sessaoService.limparPedidoEmAndamento(idSessao);
                return new RoteamentoResultado("carrinho_limpo", menus.montarCarrinhoLimpo(estabelecimento, whatsappCliente, idSessao));

            case "INFORMAR_ENDERECO":
                return fluxo.tratarFluxoEndereco(estabelecimento, whatsappCliente, idSessao);

            case "FAZER_PEDIDO_COM_ENDERECO_ANTERIOR":
                return fluxo.tratarConfirmacaoEnderecoAnterior(estabelecimento, whatsappCliente, whatsappReceptor, phoneNumberId, idSessao);

            case "INFORMAR_OUTRO_ENDERECO":
                sessaoService.marcarAguardandoCepEntrega(idSessao);
                return new RoteamentoResultado(
                    "solicitar_cep_entrega",
                    menus.montarSolicitacaoCepEntrega(whatsappCliente)
                );
            case "SELECIONAR_PAGAMENTO": {
                FormaPagamentoPedido fp = parse.parseFormaPagamento(cmd.getParte(2));
                return fluxo.tratarSelecaoPagamento(estabelecimento, whatsappCliente, whatsappReceptor, phoneNumberId, idSessao, fp);
            }

            case "TROCO":
                return fluxo.tratarTroco(estabelecimento, whatsappCliente, whatsappReceptor, phoneNumberId, idSessao, cmd.getParte(2));

            case "ENVIAR_PEDIDO":
                sessaoService.desmarcarAguardandoConfirmacaoFinal(idSessao);
                return fluxo.tratarEnvioPedidoDefinitivo(estabelecimento, whatsappCliente, idSessao);

            case "LISTA_PRODUTOS": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
                return new RoteamentoResultado(
                    "lista_produtos",
                    menus.montarListaProdutosPorCategoriaPaginada(estabelecimento, whatsappCliente, idCategoria, quantidadeMultipla, 0)
                );
            }

            case "LISTA_PRODUTOS_PAG": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
                Integer offset = parse.parseIntObrigatorio(cmd.getParte(4), "offset");
                return new RoteamentoResultado(
                    "lista_produtos",
                    menus.montarListaProdutosPorCategoriaPaginada(estabelecimento, whatsappCliente, idCategoria, quantidadeMultipla, offset)
                );
            }

            case "LISTAR_QUANTIDADES": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(4), "idProduto");
                return new RoteamentoResultado(
                    "listar_quantidades",
                    menus.montarListaQuantidades(estabelecimento, whatsappCliente, idCategoria, quantidadeMultipla, idProduto)
                );
            }

            case "ADICIONAR_PRODUTO": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer quantidade = parse.parseIntObrigatorio(cmd.getParte(3), "quantidade");
                return fluxo.tratarAdicionarProduto(estabelecimento, whatsappCliente, idProduto, quantidade);
            }

            case "SOLICITAR_QUANTIDADE": {

                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");

                sessaoService.marcarAguardandoQuantidadeManual(idSessao, idProduto);

                MensagemWhatsappSaidaDTO m = msg.texto(
                    whatsappCliente,
                    "Certo! Me informe a quantidade desejada para o produto.\n\nExemplo: 15"
                );

                return new RoteamentoResultado("solicitar_quantidade_manual", m);
            }

            case "ULTIMO_PEDIDO":
                return revisao.tratarUltimoPedidoParaRevisao(estabelecimento, whatsappCliente);

            // =========================
            // REVISÃO DE PEDIDO (cliente)
            // =========================
            case "PEDIDO_REVISAR": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                return revisao.tratarTelaRevisaoPedido(estabelecimento, whatsappCliente, idPedido);
            }

            case "REVISAO_ADICIONAR_ITENS": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");

                PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(
                    estabelecimento.getId(),
                    idPedido,
                    whatsappCliente
                );

                if (pedido == null || pedido.getStatus() == null) {
                    return new RoteamentoResultado(
                        "revisao_pedido_nao_encontrado",
                        msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
                    );
                }

                if (pedido.getStatus() != StatusPedido.CRIADO) {
                    MensagemWhatsappSaidaDTO tela = revisao.montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado("revisao_bloqueio_adicionar_itens", aviso, java.util.List.of(tela));
                }

                return new RoteamentoResultado(
                    "revisao_lista_categorias",
                    revisao.montarListaCategoriasRevisao(estabelecimento, whatsappCliente, idPedido)
                );
            }

            case "REVISAO_LISTA_PRODUTOS": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(4), "quantidadeMultipla");

                PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(
                    estabelecimento.getId(),
                    idPedido,
                    whatsappCliente
                );

                if (pedido == null || pedido.getStatus() == null) {
                    return new RoteamentoResultado(
                        "revisao_pedido_nao_encontrado",
                        msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
                    );
                }

                if (pedido.getStatus() != StatusPedido.CRIADO) {
                    MensagemWhatsappSaidaDTO tela = revisao.montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado("revisao_bloqueio_listar_produtos", aviso, java.util.List.of(tela));
                }

                return new RoteamentoResultado(
                    "revisao_lista_produtos",
                    revisao.montarListaProdutosPorCategoriaPaginadaRevisao(estabelecimento, whatsappCliente, idPedido, idCategoria, quantidadeMultipla, 0)
                );
            }

            case "REVISAO_LISTA_PRODUTOS_PAG": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(4), "quantidadeMultipla");
                Integer offset = parse.parseIntObrigatorio(cmd.getParte(5), "offset");

                PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(
                    estabelecimento.getId(),
                    idPedido,
                    whatsappCliente
                );

                if (pedido == null || pedido.getStatus() == null) {
                    return new RoteamentoResultado(
                        "revisao_pedido_nao_encontrado",
                        msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
                    );
                }

                if (pedido.getStatus() != StatusPedido.CRIADO) {
                    MensagemWhatsappSaidaDTO tela = revisao.montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado("revisao_bloqueio_listar_produtos_pag", aviso, java.util.List.of(tela));
                }

                return new RoteamentoResultado(
                    "revisao_lista_produtos",
                    revisao.montarListaProdutosPorCategoriaPaginadaRevisao(estabelecimento, whatsappCliente, idPedido, idCategoria, quantidadeMultipla, offset)
                );
            }

            case "REVISAO_LISTAR_QUANTIDADES": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(4), "quantidadeMultipla");
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(5), "idProduto");

                PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(
                    estabelecimento.getId(),
                    idPedido,
                    whatsappCliente
                );

                if (pedido == null || pedido.getStatus() == null) {
                    return new RoteamentoResultado(
                        "revisao_pedido_nao_encontrado",
                        msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
                    );
                }

                if (pedido.getStatus() != StatusPedido.CRIADO) {
                    MensagemWhatsappSaidaDTO tela = revisao.montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado("revisao_bloqueio_listar_quantidades", aviso, java.util.List.of(tela));
                }

                return new RoteamentoResultado(
                    "revisao_listar_quantidades",
                    revisao.montarListaQuantidadesRevisao(estabelecimento, whatsappCliente, idPedido, idCategoria, quantidadeMultipla, idProduto)
                );
            }

            case "REVISAO_ADICIONAR_PRODUTO": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(3), "idProduto");
                Integer quantidade = parse.parseIntObrigatorio(cmd.getParte(4), "quantidade");
                return revisao.tratarRevisaoAdicionarProduto(estabelecimento, whatsappCliente, idPedido, idProduto, quantidade);
            }

            case "REVISAO_CANCELAR_PEDIDO": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                return revisao.tratarRevisaoCancelarPedido(estabelecimento, whatsappCliente, idPedido);
            }

            case "REVISAO_CONFIRMAR_ENTREGA": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                return revisao.tratarRevisaoConfirmarEntrega(estabelecimento, whatsappCliente, idPedido);
            }
            
            //QUANDO O ESTABELECIMENTO ESTÁ FECHADO O CLIENTE PODE PEDIR PARA SER NOTIFICADO QUANDO ABRIR
            case "CADASTRAR_NOTIFICACAO_ESTABELECIMENTO_ABERTO": {

                if (estabelecimento != null && estabelecimento.isAberto()) {
                    MensagemWhatsappSaidaDTO m = msg.texto(
                        whatsappCliente,
                        "✅ O estabelecimento já está *aberto*.\n\n" +
                            "Você já pode fazer seu pedido agora. 🙂"
                    );
                    return new RoteamentoResultado("notificacao_estabelecimento_ja_aberto", m);
                }

                boolean criado = estabelecimentoService.solicitarNotificacaoQuandoAbrir(
                    estabelecimento.getId(),
                    whatsappCliente,
                    phoneNumberId,
                    wamidEntrada,
                    idCorrelacao
                );

                MensagemWhatsappSaidaDTO m = msg.texto(
                    whatsappCliente,
                    criado
                        ? "✅ Combinado! Vou te avisar assim que o estabelecimento abrir."
                        : "✅ Você já está na lista para ser avisado quando o estabelecimento abrir."
                );

                return new RoteamentoResultado("notificacao_estabelecimento_cadastrada", m);
            }

            default:
                return new RoteamentoResultado("comando_desconhecido", menus.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente));
        }
    }
}