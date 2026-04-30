package br.com.oraped.service.whatsapp.orquestrador;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.marketplace.Marketplace;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.PedidoResponseDTO;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.dto.marktplace.CategoriaMarketplaceDisponivelDTO;
import br.com.oraped.dto.marktplace.EstabelecimentoDisponivelMarketplaceDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.PedidoService;
import br.com.oraped.service.carrinho.CarrinhoService;
import br.com.oraped.service.geolocalizacao.GeolocalizacaoOrigemMarketplaceService;
import br.com.oraped.service.marketplace.MarketplaceCategoriaService;
import br.com.oraped.service.marketplace.MarketplaceEstabelecimentoService;
import br.com.oraped.service.marketplace.MarketplaceService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.orquestrador.marketplace.OrquestradorMarketplaceMensagemService;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.sessao.SessaoItemCarrinhoEmMontagemService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappClienteService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappMarketplaceService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Rotear os comandos do cliente no WhatsApp, tanto para o fluxo tradicional
 * de estabelecimento quanto para o fluxo de discovery do marketplace.
 *
 * Aplicação:
 * Utilizado pelo orquestrador principal após o parse do comando, concentrando
 * as ações do cliente em menus, carrinho, pedido, revisão e marketplace.
 *
 * Utilização:
 * Deve permanecer como ponto central de roteamento do cliente, delegando:
 * - montagem de telas aos services de mensagem/menu
 * - regras de negócio aos services de fluxo/revisão/discovery
 * - persistência de estado à sessão
 */
@Service
@RequiredArgsConstructor
public class OrquestradorRoteamentoClienteService {

	private final SessaoAtendimentoWhatsappService sessaoService;
	private final SessaoWhatsappClienteService sessaoClienteService;
	private final SessaoWhatsappMarketplaceService sessaoMarketplaceService;
	
    private final PedidoService pedidoService;
    private final EstabelecimentoService estabelecimentoService;
    private final MarketplaceService marketplaceService;
    private final MarketplaceCategoriaService marketplaceCategoriaService;
    private final MarketplaceEstabelecimentoService marketplaceEstabelecimentoService;
    private final GeolocalizacaoOrigemMarketplaceService geolocalizacaoOrigemMarketplaceService;
    private final CarrinhoService carrinhoPersistenciaService;
    
    private final SessaoItemCarrinhoEmMontagemService itemEmMontagemService;
    
    private final OrquestradorParseService parse;
    private final OrquestradorMenuClienteService menus;
    private final OrquestradorFluxoClienteService fluxo;
    private final OrquestradorRevisaoPedidoService revisao;
    private final OrquestradorMarketplaceMensagemService marketplaceMensagens;

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

        switch (acao) {

            // =========================================================
            // MARKETPLACE
            // =========================================================

            case "MARKETPLACE_CATEGORIA":
                return tratarSelecaoCategoriaMarketplace(whatsappCliente, idSessao, cmd);

            case "MARKETPLACE_ESTABELECIMENTO":
                return tratarSelecaoEstabelecimentoMarketplace(
                    whatsappCliente,
                    idSessao,
                    cmd
                );
            case "TROCAR_ESTABELECIMENTO_MARKETPLACE":
                return tratarTrocarEstabelecimentoMarketplace(whatsappCliente, idSessao);

            case "TROCAR_CATEGORIA_MARKETPLACE":
                return tratarTrocarCategoriaMarketplace(whatsappCliente, idSessao);
                
            case "TROCAR_LOCALIZACAO_MARKETPLACE":
                return tratarTrocarLocalizacaoMarketplace(whatsappCliente, idSessao);
                
            // =========================================================
            // PEDIDO EM ESTABELECIMENTO
            // =========================================================

            case "FAZER_PEDIDO":
            case "INCLUIR_OUTRO_ITEM":
                // A primeira abertura da lista de categorias sempre começa na página inicial.
                return new RoteamentoResultado(
                    "lista_categorias",
                    menus.montarListaCategoriasPaginada(estabelecimento, whatsappCliente, 0)
                );

            case "LISTA_CATEGORIAS_PAG": {
                Integer offset = parse.parseIntObrigatorio(cmd.getParte(2), "offset");

                return new RoteamentoResultado(
                    "lista_categorias",
                    menus.montarListaCategoriasPaginada(estabelecimento, whatsappCliente, offset)
                );
            }

            case "VISUALIZAR_CARRINHO":
                return new RoteamentoResultado(
                    "visualizar_carrinho",
                    menus.montarVisualizacaoCarrinho(estabelecimento, whatsappCliente, idSessao)
                );

            case "LIMPAR_CARRINHO":
                // =========================================================
                // CLIENTE — Limpeza completa do pedido em montagem
                // =========================================================

                // Limpa dados temporários da sessão, carrinho persistido e item em montagem.
                sessaoService.limparPedidoEmAndamento(idSessao);
                carrinhoPersistenciaService.limparCarrinho(idSessao);
                itemEmMontagemService.limparMontagem(idSessao);

                return new RoteamentoResultado(
                    "carrinho_limpo",
                    menus.montarCarrinhoLimpo(estabelecimento, whatsappCliente, idSessao)
                );

            case "INFORMAR_ENDERECO":
                return fluxo.tratarFluxoEndereco(estabelecimento, whatsappCliente, idSessao);

            case "FAZER_PEDIDO_COM_ENDERECO_ANTERIOR":
                return fluxo.tratarConfirmacaoEnderecoAnterior(
                    estabelecimento,
                    whatsappCliente,
                    whatsappReceptor,
                    phoneNumberId,
                    idSessao
                );

            case "INFORMAR_OUTRO_ENDERECO":
            	sessaoClienteService.marcarAguardandoCepEntrega(idSessao);
                return new RoteamentoResultado(
                    "solicitar_cep_entrega",
                    menus.montarSolicitacaoCepEntrega(whatsappCliente)
                );

            case "SELECIONAR_PAGAMENTO": {
                FormaPagamentoPedido fp = parse.parseFormaPagamento(cmd.getParte(2));
                return fluxo.tratarSelecaoPagamento(
                    estabelecimento,
                    whatsappCliente,
                    whatsappReceptor,
                    phoneNumberId,
                    idSessao,
                    fp
                );
            }

            case "TROCO":
                return fluxo.tratarTroco(
                    estabelecimento,
                    whatsappCliente,
                    whatsappReceptor,
                    phoneNumberId,
                    idSessao,
                    cmd.getParte(2)
                );

            case "ENVIAR_PEDIDO":
            	sessaoClienteService.desmarcarAguardandoConfirmacaoFinal(idSessao);
                return fluxo.tratarEnvioPedidoDefinitivo(estabelecimento, whatsappCliente, idSessao);

            case "LISTA_PRODUTOS": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");

                return new RoteamentoResultado(
                    "lista_produtos",
                    menus.montarListaProdutosPorCategoriaPaginada(
                        estabelecimento,
                        whatsappCliente,
                        idCategoria,
                        quantidadeMultipla,
                        0
                    )
                );
            }

            case "LISTA_PRODUTOS_PAG": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
                Integer offset = parse.parseIntObrigatorio(cmd.getParte(4), "offset");

                return new RoteamentoResultado(
                    "lista_produtos",
                    menus.montarListaProdutosPorCategoriaPaginada(
                        estabelecimento,
                        whatsappCliente,
                        idCategoria,
                        quantidadeMultipla,
                        offset
                    )
                );
            }

            case "SELECIONAR_PRODUTO": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(4), "idProduto");

                Produto produto = menus.getProduto(estabelecimento, idProduto);

                MensagemWhatsappSaidaDTO proximaMensagem;

                if (menus.produtoPossuiComplementosPorCategoria(produto)) {
                    itemEmMontagemService.iniciarMontagem(
                        idSessao,
                        idProduto,
                        idCategoria,
                        quantidadeMultipla
                    );

                    proximaMensagem = menus.montarListaComplementosEmMontagem(
                        estabelecimento,
                        whatsappCliente,
                        idSessao
                    );
                } else {
                	proximaMensagem = menus.montarSelecaoProduto(
            		    estabelecimento,
            		    whatsappCliente,
            		    idSessao,
            		    idCategoria,
            		    quantidadeMultipla,
            		    idProduto
            		);
                }

                // Sem foto, segue direto para a próxima etapa do fluxo: complementos ou quantidades.
                if (produto == null || !StringUtils.hasText(produto.getUrlFoto())) {
                    return new RoteamentoResultado(
                        "selecionar_produto",
                        proximaMensagem
                    );
                }

                MensagemWhatsappSaidaDTO imagemProduto = menus.montarImagemProdutoAntesDasQuantidades(
                    estabelecimento,
                    whatsappCliente,
                    idProduto
                );

                // Com foto, enviamos duas mensagens:
                // 1) imagem do produto
                // 2) lista de quantidades
                if (imagemProduto != null) {
                    return new RoteamentoResultado(
                        "selecionar_produto_com_imagem",
                        imagemProduto,
                        List.of(proximaMensagem)
                    );
                }

                return new RoteamentoResultado(
                    "selecionar_produto",
                    proximaMensagem
                );
            }

            case "SELECIONAR_COMPLEMENTO": {
                Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(2), "idComplemento");

                return fluxo.tratarSelecionarComplemento(
                    estabelecimento,
                    whatsappCliente,
                    idSessao,
                    idComplemento
                );
            }

            case "NAO_QUERO_COMPLEMENTO":
	            return fluxo.tratarNaoQueroComplemento(
	                estabelecimento,
	                whatsappCliente,
	                idSessao
	            );
	            
            case "COMPRAR_PRODUTO": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(4), "idProduto");

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

            case "ESCOLHER_OUTRO_PRODUTO": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");

                return new RoteamentoResultado(
                    "lista_produtos",
                    menus.montarListaProdutosPorCategoriaPaginada(
                        estabelecimento,
                        whatsappCliente,
                        idCategoria,
                        quantidadeMultipla,
                        0
                    )
                );
            }

            case "LISTAR_QUANTIDADES": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(4), "idProduto");

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

            case "ADICIONAR_PRODUTO": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer quantidade = parse.parseIntObrigatorio(cmd.getParte(3), "quantidade");
                
                return fluxo.tratarAdicionarProduto(
                	    estabelecimento,
                	    whatsappCliente,
                	    idSessao,
                	    idProduto,
                	    quantidade
                	);
            }

            case "SOLICITAR_QUANTIDADE": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");

                sessaoClienteService.marcarAguardandoQuantidadeManual(idSessao, idProduto);

                MensagemWhatsappSaidaDTO m = msg.texto(
                    whatsappCliente,
                    "Certo! Me informe a quantidade desejada para o produto.\n\nExemplo: 15"
                );

                return new RoteamentoResultado("solicitar_quantidade_manual", m);
            }

            case "ULTIMO_PEDIDO":
                return revisao.tratarUltimoPedidoParaRevisao(estabelecimento, whatsappCliente);

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
                    MensagemWhatsappSaidaDTO tela = revisao.montarTelaRevisaoPedido(
                        estabelecimento,
                        whatsappCliente,
                        pedido
                    );
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado(
                        "revisao_bloqueio_adicionar_itens",
                        aviso,
                        List.of(tela)
                    );
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
                    MensagemWhatsappSaidaDTO tela = revisao.montarTelaRevisaoPedido(
                        estabelecimento,
                        whatsappCliente,
                        pedido
                    );
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado(
                        "revisao_bloqueio_listar_produtos",
                        aviso,
                        List.of(tela)
                    );
                }

                return new RoteamentoResultado(
                    "revisao_lista_produtos",
                    revisao.montarListaProdutosPorCategoriaPaginadaRevisao(
                        estabelecimento,
                        whatsappCliente,
                        idPedido,
                        idCategoria,
                        quantidadeMultipla,
                        0
                    )
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
                    MensagemWhatsappSaidaDTO tela = revisao.montarTelaRevisaoPedido(
                        estabelecimento,
                        whatsappCliente,
                        pedido
                    );
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado(
                        "revisao_bloqueio_listar_produtos_pag",
                        aviso,
                        List.of(tela)
                    );
                }

                return new RoteamentoResultado(
                    "revisao_lista_produtos",
                    revisao.montarListaProdutosPorCategoriaPaginadaRevisao(
                        estabelecimento,
                        whatsappCliente,
                        idPedido,
                        idCategoria,
                        quantidadeMultipla,
                        offset
                    )
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
                    MensagemWhatsappSaidaDTO tela = revisao.montarTelaRevisaoPedido(
                        estabelecimento,
                        whatsappCliente,
                        pedido
                    );
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado(
                        "revisao_bloqueio_listar_quantidades",
                        aviso,
                        List.of(tela)
                    );
                }

                return new RoteamentoResultado(
                    "revisao_listar_quantidades",
                    revisao.montarListaQuantidadesRevisao(
                        estabelecimento,
                        whatsappCliente,
                        idPedido,
                        idCategoria,
                        quantidadeMultipla,
                        idProduto
                    )
                );
            }

            case "REVISAO_ADICIONAR_PRODUTO": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(3), "idProduto");
                Integer quantidade = parse.parseIntObrigatorio(cmd.getParte(4), "quantidade");
                return revisao.tratarRevisaoAdicionarProduto(
                    estabelecimento,
                    whatsappCliente,
                    idPedido,
                    idProduto,
                    quantidade
                );
            }

            case "REVISAO_CANCELAR_PEDIDO": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                return revisao.tratarRevisaoCancelarPedido(estabelecimento, whatsappCliente, idPedido);
            }

            case "REVISAO_CONFIRMAR_ENTREGA": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
                return revisao.tratarRevisaoConfirmarEntrega(estabelecimento, whatsappCliente, idPedido);
            }

            case "CADASTRAR_NOTIFICACAO_ESTABELECIMENTO_ABERTO": {

                if (estabelecimento == null) {
                    return new RoteamentoResultado(
                        "notificacao_estabelecimento_invalido",
                        msg.texto(
                            whatsappCliente,
                            "⚠️ Não consegui identificar o estabelecimento desta conversa."
                        )
                    );
                }

                if (estabelecimento.isAberto()) {
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
                return new RoteamentoResultado(
                    "comando_desconhecido",
                    montarMenuFallback(estabelecimento, whatsappCliente, idSessao)
                );
        }
    }

    private RoteamentoResultado tratarSelecaoCategoriaMarketplace(
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        Long idCategoriaMarketplace = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoriaMarketplace");

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        if (sessao == null || sessao.getIdMarketplace() == null) {
            return new RoteamentoResultado(
                "marketplace_sessao_invalida",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar o marketplace desta conversa.\n\nTente iniciar novamente."
                )
            );
        }

        Marketplace marketplace = marketplaceService.buscarPorId(sessao.getIdMarketplace());

        CategoriaMarketplaceDisponivelDTO categoria = marketplaceCategoriaService.buscarCategoriaDisponivel(
            idCategoriaMarketplace,
            marketplace,
            sessao
        );

        sessaoMarketplaceService.salvarCategoriaMarketplaceSelecionada(idSessao, categoria.getId());

        SessaoAtendimentoWhatsapp sessaoAtualizada = sessaoService.buscarPorId(idSessao);

        List<EstabelecimentoDisponivelMarketplaceDTO> estabelecimentos =
            marketplaceEstabelecimentoService.listarEstabelecimentosDisponiveis(
                marketplace,
                sessaoAtualizada
            );

        MensagemWhatsappSaidaDTO mensagem = marketplaceMensagens.montarMenuEstabelecimentos(
            whatsappCliente,
            categoria,
            estabelecimentos
        );

        return new RoteamentoResultado(
            "marketplace_lista_estabelecimentos",
            mensagem
        );
    }
    
    private RoteamentoResultado tratarSelecaoEstabelecimentoMarketplace(
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        Long idEstabelecimento = parse.parseLongObrigatorio(cmd.getParte(2), "idEstabelecimento");

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        if (sessao == null || sessao.getIdMarketplace() == null) {
            return new RoteamentoResultado(
                "marketplace_sessao_invalida",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar o marketplace desta conversa.\n\nTente iniciar novamente."
                )
            );
        }

        Estabelecimento estabelecimentoSelecionado = estabelecimentoService.buscar(idEstabelecimento);

        if (estabelecimentoSelecionado == null || !estabelecimentoSelecionado.isAtivo()) {
            return new RoteamentoResultado(
                "marketplace_estabelecimento_invalido",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar um estabelecimento disponível para continuar.\n\nTente escolher outro."
                )
            );
        }

        // A partir daqui, a sessão passa a seguir exclusivamente o fluxo
        // do estabelecimento escolhido.
        sessaoMarketplaceService.vincularEstabelecimentoAoAtendimentoMarketplace(
            idSessao,
            estabelecimentoSelecionado.getId()
        );

        Estabelecimento estabelecimentoAtualizado = estabelecimentoService.buscar(
            estabelecimentoSelecionado.getId()
        );

        MensagemWhatsappSaidaDTO avisoConexao = msg.texto(
            whatsappCliente,
            "✅ Agora você está falando com *" + msg.safe(estabelecimentoAtualizado.getNome()) + "*."
        );

        MensagemWhatsappSaidaDTO menuPrincipal = menus.montarMenuPrincipalSemSaudacao(
    	    estabelecimentoAtualizado,
    	    whatsappCliente,
    	    idSessao
    	);

        return new RoteamentoResultado(
            "marketplace_estabelecimento_selecionado",
            avisoConexao,
            List.of(menuPrincipal)
        );
    }

    
    private RoteamentoResultado tratarTrocarEstabelecimentoMarketplace(
	    String whatsappCliente,
	    Long idSessao
	) {

	    SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

	    if (sessao == null || sessao.getIdMarketplace() == null || sessao.getIdCategoriaMarketplace() == null) {
	        return new RoteamentoResultado(
	            "marketplace_troca_estabelecimento_invalida",
	            msg.texto(
	                whatsappCliente,
	                "⚠️ Não consegui identificar a categoria atual para trocar de loja.\n\nVolte ao marketplace e escolha uma categoria novamente."
	            )
	        );
	    }

	    Long idMarketplace = sessao.getIdMarketplace();
	    Long idCategoriaMarketplace = sessao.getIdCategoriaMarketplace();

	    /*
	     * Trocar estabelecimento mantém a localização e a categoria atual,
	     * limpando apenas a loja/pedido em andamento.
	     */
	    sessaoMarketplaceService.trocarEstabelecimentoMarketplace(idSessao, idMarketplace);

	    SessaoAtendimentoWhatsapp sessaoAtualizada = sessaoService.buscarPorId(idSessao);
	    Marketplace marketplace = marketplaceService.buscarPorId(idMarketplace);

	    CategoriaMarketplaceDisponivelDTO categoria = marketplaceCategoriaService.buscarCategoriaDisponivel(
	        idCategoriaMarketplace,
	        marketplace,
	        sessaoAtualizada
	    );

	    List<EstabelecimentoDisponivelMarketplaceDTO> estabelecimentos =
	        marketplaceEstabelecimentoService.listarEstabelecimentosDisponiveis(
	            marketplace,
	            sessaoAtualizada
	        );

	    MensagemWhatsappSaidaDTO mensagem = marketplaceMensagens.montarMenuEstabelecimentos(
	        whatsappCliente,
	        categoria,
	        estabelecimentos
	    );

	    return new RoteamentoResultado(
	        "marketplace_trocar_estabelecimento",
	        mensagem
	    );
	}

    
    private RoteamentoResultado tratarTrocarCategoriaMarketplace(
	    String whatsappCliente,
	    Long idSessao
	) {

	    SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

	    if (sessao == null || sessao.getIdMarketplace() == null) {
	        return new RoteamentoResultado(
	            "marketplace_troca_categoria_invalida",
	            msg.texto(
	                whatsappCliente,
	                "⚠️ Não consegui identificar o marketplace desta conversa.\n\nTente iniciar novamente."
	            )
	        );
	    }

	    Long idMarketplace = sessao.getIdMarketplace();

	    /*
	     * Trocar categoria mantém a localização atual, mas remove a loja e a categoria
	     * anteriores para reabrir a árvore de tipos de estabelecimento.
	     */
	    sessaoMarketplaceService.trocarCategoriaMarketplace(idSessao, idMarketplace);

	    SessaoAtendimentoWhatsapp sessaoAtualizada = sessaoService.buscarPorId(idSessao);
	    Marketplace marketplace = marketplaceService.buscarPorId(idMarketplace);

	    EnderecoResolvidoDTO enderecoResolvido = geolocalizacaoOrigemMarketplaceService.resolverOrigemCliente(
	        sessaoAtualizada.getLatitudeOrigemCliente(),
	        sessaoAtualizada.getLongitudeOrigemCliente()
	    );

	    List<CategoriaMarketplaceDisponivelDTO> categorias = marketplaceCategoriaService.listarCategoriasDisponiveis(
	        marketplace,
	        sessaoAtualizada
	    );

	    MensagemWhatsappSaidaDTO mensagem = marketplaceMensagens.montarMenuCategoriasComLocalizacaoExistente(
	        whatsappCliente,
	        marketplace,
	        enderecoResolvido,
	        categorias
	    );

	    return new RoteamentoResultado(
	        "marketplace_trocar_categoria",
	        mensagem
	    );
	}
    
    private RoteamentoResultado tratarTrocarLocalizacaoMarketplace(
	    String whatsappCliente,
	    Long idSessao
	) {

	    SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

	    if (sessao == null || sessao.getIdMarketplace() == null) {
	        return new RoteamentoResultado(
	            "marketplace_troca_localizacao_invalida",
	            msg.texto(
	                whatsappCliente,
	                "⚠️ Não consegui identificar o marketplace desta conversa.\n\nTente iniciar novamente."
	            )
	        );
	    }

	    Long idMarketplace = sessao.getIdMarketplace();
	    Marketplace marketplace = marketplaceService.buscarPorId(idMarketplace);

	    /*
	     * Trocar localização reinicia o discovery do marketplace.
	     * O cliente deverá informar nova localização ou novo CEP.
	     */
	    sessaoMarketplaceService.trocarLocalizacaoMarketplace(idSessao, idMarketplace);

	    MensagemWhatsappSaidaDTO mensagem = msg.texto(
	        whatsappCliente,
	        "📍 Tudo bem! Vamos trocar sua localização.\n\n" +
	            "Você pode:\n" +
	            "- Compartilhar sua localização atual pelo WhatsApp 📍\n" +
	            "ou\n" +
	            "- Digitar o novo CEP\n\n" +
	            "Assim eu atualizo as opções disponíveis no marketplace " + msg.safe(marketplace.getNome()) + "."
	    );

	    return new RoteamentoResultado(
	        "marketplace_trocar_localizacao",
	        mensagem
	    );
	}
    
    
    private MensagemWhatsappSaidaDTO montarMenuFallback(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        if (estabelecimento != null) {
        	return menus.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente, idSessao);
        }

        SessaoAtendimentoWhatsapp sessao = idSessao == null ? null : sessaoService.buscarPorId(idSessao);

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