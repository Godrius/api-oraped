package br.com.oraped.service.whatsapp.cliente.roteamento;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.PedidoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.pedido.PedidoService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.cliente.RevisaoPedidoClienteService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Rotear os comandos de revisão de pedido executados pelo cliente no WhatsApp.
 *
 * Aplicação:
 * Centraliza ações de consulta do último pedido, tela de revisão, inclusão de itens,
 * cancelamento e confirmação de entrega.
 *
 * Utilização:
 * Mantém o RoteamentoClienteService enxuto e delega para RevisaoPedidoClienteService
 * as regras e telas específicas de revisão de pedido.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoRevisaoPedidoClienteService {

    private final PedidoService pedidoService;

    private final OrquestradorParseService parse;

    private final RevisaoPedidoClienteService revisaoPedidoClienteService;

    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ULTIMO_PEDIDO":
                return revisaoPedidoClienteService.tratarUltimoPedidoParaRevisao(
                    estabelecimento,
                    whatsappCliente
                );

            case "PEDIDO_REVISAR":
                return revisarPedido(estabelecimento, whatsappCliente, cmd);

            case "REVISAO_ADICIONAR_ITENS":
                return adicionarItens(estabelecimento, whatsappCliente, cmd);

            case "REVISAO_LISTA_PRODUTOS":
                return listarProdutos(estabelecimento, whatsappCliente, cmd);

            case "REVISAO_LISTA_PRODUTOS_PAG":
                return listarProdutosPaginados(estabelecimento, whatsappCliente, cmd);

            case "REVISAO_LISTAR_QUANTIDADES":
                return listarQuantidades(estabelecimento, whatsappCliente, cmd);

            case "REVISAO_ADICIONAR_PRODUTO":
                return adicionarProduto(estabelecimento, whatsappCliente, cmd);

            case "REVISAO_CANCELAR_PEDIDO":
                return cancelarPedido(estabelecimento, whatsappCliente, cmd);

            case "REVISAO_CONFIRMAR_ENTREGA":
                return confirmarEntrega(estabelecimento, whatsappCliente, cmd);

            default:
                return new RoteamentoResultado(
                    "revisao_cliente_comando_desconhecido",
                    msg.texto(
                        whatsappCliente,
                        "⚠️ Não consegui identificar a ação de revisão do pedido.\n\nTente novamente."
                    )
                );
        }
    }

    private RoteamentoResultado revisarPedido(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        ComandoWhatsapp cmd
    ) {

        Long idPedido = parse.parseLongObrigatorio(
            cmd.getParte(2),
            "idPedido"
        );

        return revisaoPedidoClienteService.tratarTelaRevisaoPedido(
            estabelecimento,
            whatsappCliente,
            idPedido
        );
    }

    private RoteamentoResultado adicionarItens(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        ComandoWhatsapp cmd
    ) {

        Long idPedido = parse.parseLongObrigatorio(
            cmd.getParte(2),
            "idPedido"
        );

        PedidoResponseDTO pedido = buscarPedidoParaRevisao(
            estabelecimento,
            whatsappCliente,
            idPedido
        );

        if (pedido == null || pedido.getStatus() == null) {
            return pedidoNaoEncontrado(whatsappCliente);
        }

        if (pedido.getStatus() != StatusPedido.CRIADO) {
            return bloquearAlteracao(
                estabelecimento,
                whatsappCliente,
                pedido,
                "revisao_bloqueio_adicionar_itens"
            );
        }

        return new RoteamentoResultado(
            "revisao_lista_categorias",
            revisaoPedidoClienteService.montarListaCategoriasRevisao(
                estabelecimento,
                whatsappCliente,
                idPedido
            )
        );
    }

    private RoteamentoResultado listarProdutos(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        ComandoWhatsapp cmd
    ) {

        Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
        Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
        Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(4), "quantidadeMultipla");

        PedidoResponseDTO pedido = buscarPedidoParaRevisao(
            estabelecimento,
            whatsappCliente,
            idPedido
        );

        if (pedido == null || pedido.getStatus() == null) {
            return pedidoNaoEncontrado(whatsappCliente);
        }

        if (pedido.getStatus() != StatusPedido.CRIADO) {
            return bloquearAlteracao(
                estabelecimento,
                whatsappCliente,
                pedido,
                "revisao_bloqueio_listar_produtos"
            );
        }

        return new RoteamentoResultado(
            "revisao_lista_produtos",
            revisaoPedidoClienteService.montarListaProdutosPorCategoriaPaginadaRevisao(
                estabelecimento,
                whatsappCliente,
                idPedido,
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

        Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
        Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
        Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(4), "quantidadeMultipla");
        Integer offset = parse.parseIntObrigatorio(cmd.getParte(5), "offset");

        PedidoResponseDTO pedido = buscarPedidoParaRevisao(
            estabelecimento,
            whatsappCliente,
            idPedido
        );

        if (pedido == null || pedido.getStatus() == null) {
            return pedidoNaoEncontrado(whatsappCliente);
        }

        if (pedido.getStatus() != StatusPedido.CRIADO) {
            return bloquearAlteracao(
                estabelecimento,
                whatsappCliente,
                pedido,
                "revisao_bloqueio_listar_produtos_pag"
            );
        }

        return new RoteamentoResultado(
            "revisao_lista_produtos",
            revisaoPedidoClienteService.montarListaProdutosPorCategoriaPaginadaRevisao(
                estabelecimento,
                whatsappCliente,
                idPedido,
                idCategoria,
                quantidadeMultipla,
                offset
            )
        );
    }

    private RoteamentoResultado listarQuantidades(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        ComandoWhatsapp cmd
    ) {

        Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
        Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
        Integer quantidadeMultipla = parse.parseIntObrigatorio(cmd.getParte(4), "quantidadeMultipla");
        Long idProduto = parse.parseLongObrigatorio(cmd.getParte(5), "idProduto");

        PedidoResponseDTO pedido = buscarPedidoParaRevisao(
            estabelecimento,
            whatsappCliente,
            idPedido
        );

        if (pedido == null || pedido.getStatus() == null) {
            return pedidoNaoEncontrado(whatsappCliente);
        }

        if (pedido.getStatus() != StatusPedido.CRIADO) {
            return bloquearAlteracao(
                estabelecimento,
                whatsappCliente,
                pedido,
                "revisao_bloqueio_listar_quantidades"
            );
        }

        return new RoteamentoResultado(
            "revisao_listar_quantidades",
            revisaoPedidoClienteService.montarListaQuantidadesRevisao(
                estabelecimento,
                whatsappCliente,
                idPedido,
                idCategoria,
                quantidadeMultipla,
                idProduto
            )
        );
    }

    private RoteamentoResultado adicionarProduto(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        ComandoWhatsapp cmd
    ) {

        Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");
        Long idProduto = parse.parseLongObrigatorio(cmd.getParte(3), "idProduto");
        Integer quantidade = parse.parseIntObrigatorio(cmd.getParte(4), "quantidade");

        return revisaoPedidoClienteService.tratarRevisaoAdicionarProduto(
            estabelecimento,
            whatsappCliente,
            idPedido,
            idProduto,
            quantidade
        );
    }

    private RoteamentoResultado cancelarPedido(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        ComandoWhatsapp cmd
    ) {

        Long idPedido = parse.parseLongObrigatorio(
            cmd.getParte(2),
            "idPedido"
        );

        return revisaoPedidoClienteService.tratarRevisaoCancelarPedido(
            estabelecimento,
            whatsappCliente,
            idPedido
        );
    }

    private RoteamentoResultado confirmarEntrega(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        ComandoWhatsapp cmd
    ) {

        Long idPedido = parse.parseLongObrigatorio(
            cmd.getParte(2),
            "idPedido"
        );

        return revisaoPedidoClienteService.tratarRevisaoConfirmarEntrega(
            estabelecimento,
            whatsappCliente,
            idPedido
        );
    }

    private PedidoResponseDTO buscarPedidoParaRevisao(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido
    ) {

        return pedidoService.buscarResumoPedidoParaCliente(
            estabelecimento.getId(),
            idPedido,
            whatsappCliente
        );
    }

    private RoteamentoResultado pedidoNaoEncontrado(String whatsappCliente) {

        return new RoteamentoResultado(
            "revisao_pedido_nao_encontrado",
            msg.texto(
                whatsappCliente,
                "Não encontrei esse pedido para revisão."
            )
        );
    }

    private RoteamentoResultado bloquearAlteracao(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        PedidoResponseDTO pedido,
        String chave
    ) {

        MensagemWhatsappSaidaDTO tela = revisaoPedidoClienteService.montarTelaRevisaoPedido(
            estabelecimento,
            whatsappCliente,
            pedido
        );

        MensagemWhatsappSaidaDTO aviso = msg.texto(
            whatsappCliente,
            "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
        );

        // Mantém a tela de revisão como próxima mensagem para o cliente não perder contexto.
        return new RoteamentoResultado(
            chave,
            aviso,
            List.of(tela)
        );
    }
}