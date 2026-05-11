package br.com.oraped.service.whatsapp.cliente.roteamento;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.cliente.MenuClienteService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import br.com.oraped.service.whatsapp.sessao.SessaoItemCarrinhoEmMontagemService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Rotear os comandos de navegação do catálogo do cliente no WhatsApp.
 *
 * Aplicação:
 * Responsável pela navegação entre categorias, produtos, complementos
 * e seleção inicial do item antes da inclusão no carrinho.
 *
 * Utilização:
 * Centraliza o fluxo de descoberta e seleção de produtos,
 * preparando o terreno para futuras expansões como:
 * - tamanhos
 * - combos
 * - adicionais avançados
 * - observações por item
 */
@Service
@RequiredArgsConstructor
public class RoteamentoCatalogoClienteService {

    private final SessaoItemCarrinhoEmMontagemService itemEmMontagemService;

    private final OrquestradorParseService parse;

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

            case "FAZER_PEDIDO":
            case "INCLUIR_OUTRO_ITEM":
                return listarCategorias(estabelecimento, whatsappCliente);

            case "LISTA_CATEGORIAS_PAG":
                return listarCategoriasPaginadas(estabelecimento, whatsappCliente, cmd);

            case "LISTA_PRODUTOS":
                return listarProdutos(estabelecimento, whatsappCliente, cmd);

            case "LISTA_PRODUTOS_PAG":
                return listarProdutosPaginados(estabelecimento, whatsappCliente, cmd);

            case "SELECIONAR_PRODUTO":
                return selecionarProduto(
                    estabelecimento,
                    whatsappCliente,
                    idSessao,
                    cmd
                );

            case "ESCOLHER_OUTRO_PRODUTO":
                return escolherOutroProduto(estabelecimento, whatsappCliente, cmd);

            default:
                return new RoteamentoResultado(
                    "catalogo_comando_desconhecido",
                    msg.texto(
                        whatsappCliente,
                        "⚠️ Não consegui identificar a ação do catálogo.\n\nTente novamente."
                    )
                );
        }
    }

    private RoteamentoResultado listarCategorias(
        Estabelecimento estabelecimento,
        String whatsappCliente
    ) {

        // Toda abertura de catálogo reinicia na primeira página.
        return new RoteamentoResultado(
            "lista_categorias",
            menus.montarListaCategoriasPaginada(
                estabelecimento,
                whatsappCliente,
                0
            )
        );
    }

    private RoteamentoResultado listarCategoriasPaginadas(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        ComandoWhatsapp cmd
    ) {

        Integer offset = parse.parseIntObrigatorio(
            cmd.getParte(2),
            "offset"
        );

        return new RoteamentoResultado(
            "lista_categorias",
            menus.montarListaCategoriasPaginada(
                estabelecimento,
                whatsappCliente,
                offset
            )
        );
    }

    private RoteamentoResultado listarProdutos(
        Estabelecimento estabelecimento,
        String whatsappCliente,
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

    private RoteamentoResultado listarProdutosPaginados(
        Estabelecimento estabelecimento,
        String whatsappCliente,
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

        Integer offset = parse.parseIntObrigatorio(
            cmd.getParte(4),
            "offset"
        );

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

    private RoteamentoResultado selecionarProduto(
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

	    Produto produto = menus.getProduto(estabelecimento, idProduto);

	    MensagemWhatsappSaidaDTO proximaMensagem;

	    if (menus.produtoPossuiTamanhos(produto)) {

	        itemEmMontagemService.iniciarMontagem(
	            idSessao,
	            idProduto,
	            idCategoria,
	            quantidadeMultipla
	        );

	        proximaMensagem = menus.montarListaTamanhosProduto(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            idCategoria,
	            quantidadeMultipla,
	            idProduto
	        );

	    } else if (menus.produtoPossuiComplementos(produto)) {

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

	    if (produto == null || !StringUtils.hasText(produto.getUrlFoto())) {
	        return new RoteamentoResultado(
	            "selecionar_produto",
	            proximaMensagem
	        );
	    }

	    MensagemWhatsappSaidaDTO imagemProduto =
	        menus.montarImagemProdutoAntesDasQuantidades(
	            estabelecimento,
	            whatsappCliente,
	            idProduto
	        );

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

    private RoteamentoResultado escolherOutroProduto(
        Estabelecimento estabelecimento,
        String whatsappCliente,
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
}