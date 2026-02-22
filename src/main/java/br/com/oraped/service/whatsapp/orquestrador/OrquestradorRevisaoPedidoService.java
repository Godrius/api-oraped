// src/main/java/br/com/oraped/service/whatsapp/orquestrador/OrquestradorRevisaoPedidoService.java
package br.com.oraped.service.whatsapp.orquestrador;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.CategoriaProduto;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.Pedido;
import br.com.oraped.domain.Produto;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.PedidoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.PedidoService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdministradorWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorRevisaoPedidoService {

    private final PedidoService pedidoService;
    private final AdministradorWhatsappService administradorWhatsappService;

    private final OrquestradorExtracaoEstabelecimentoService extracaoService;
    private final OrquestradorMensagemHelperService helper;

    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado tratarUltimoPedidoParaRevisao(
        Estabelecimento estabelecimento,
        String whatsappCliente
    ) {

        PedidoResponseDTO ultimo = pedidoService.buscarUltimoPedidoDoCliente(estabelecimento.getId(), whatsappCliente);

        if (ultimo == null || ultimo.getId() == null) {
            MensagemWhatsappSaidaDTO saida = msg.botoes(
                whatsappCliente,
                msg.trunc("Não encontrei pedidos recentes para revisar. 🛒", 1024),
                List.of(
                    helper.btn("COMANDO|FAZER_PEDIDO", "🛍️ Fazer um pedido"),
                    helper.btn("COMANDO|MENU", "⬅️ Menu")
                )
            );
            return new RoteamentoResultado("revisao_sem_pedidos", saida);
        }

        return tratarTelaRevisaoPedido(estabelecimento, whatsappCliente, ultimo.getId());
    }

    public RoteamentoResultado tratarTelaRevisaoPedido(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido
    ) {

        PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(estabelecimento.getId(), idPedido, whatsappCliente);

        if (pedido == null) {
            return new RoteamentoResultado(
                "revisao_pedido_nao_encontrado",
                msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
            );
        }

        MensagemWhatsappSaidaDTO tela = montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);

        return new RoteamentoResultado("revisao_pedido_tela", tela);
    }

    public MensagemWhatsappSaidaDTO montarTelaRevisaoPedido(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        PedidoResponseDTO pedido
    ) {

        StatusPedido st = pedido == null ? null : pedido.getStatus();

        String resumoItens = msg.safe(pedido == null ? null : pedido.getResumoItens());
        if (!StringUtils.hasText(resumoItens)) {
            resumoItens = "(sem itens)";
        }

        String totalFmt = msg.formatarMoeda(
            (pedido == null || pedido.getTotal() == null) ? BigDecimal.ZERO : pedido.getTotal()
        );

        String statusLabel;

        if (StringUtils.hasText(msg.safe(pedido == null ? null : pedido.getStatusLabel()))) {
            statusLabel = msg.safe(pedido.getStatusLabel());
        } else {
            statusLabel = formatarStatusFallback(st);
        }

        String corpo =
            "🔎 *Revisão do pedido*\n\n" +
                "📌 *Pedido:* #" + (pedido == null ? "" : pedido.getId()) + "\n" +
                "⏳ *Status:* " + statusLabel + "\n\n" +
                "🛒 *Itens:*\n" +
                msg.trunc(resumoItens, 1400) + "\n\n" +
                "💰 *Total:* " + totalFmt + "\n\n" +
                "O que deseja fazer?";

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = new ArrayList<>();

        if (st == StatusPedido.CRIADO) {
            botoes.add(helper.btn("COMANDO|REVISAO_ADICIONAR_ITENS|" + pedido.getId(), "➕ Adicionar itens"));
            botoes.add(helper.btn("COMANDO|REVISAO_CANCELAR_PEDIDO|" + pedido.getId(), "🗑️ Cancelar"));
        } else if (st == StatusPedido.EM_PREPARO) {
            botoes.add(helper.btn("COMANDO|REVISAO_CANCELAR_PEDIDO|" + pedido.getId(), "🗑️ Cancelar"));
        } else if (st == StatusPedido.PRONTO) {
            botoes.add(helper.btn("COMANDO|REVISAO_CONFIRMAR_ENTREGA|" + pedido.getId(), "✅ Confirmar entrega"));
        }

        botoes.add(helper.btn("COMANDO|MENU", "⬅️ Menu"));

        return msg.botoes(whatsappCliente, msg.trunc(corpo, 1024), botoes);
    }

    private String formatarStatusFallback(StatusPedido st) {
        if (st == null) return "desconhecido";
        switch (st) {
            case CRIADO:
                return "aguardando confirmação do estabelecimento";
            case EM_PREPARO:
                return "em preparo";
            case PRONTO:
                return "saiu para entrega";
            case ENTREGUE:
                return "entregue";
            case CANCELADO:
                return "cancelado";
            default:
                return st.name();
        }
    }

    public MensagemWhatsappSaidaDTO montarListaCategoriasRevisao(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido
    ) {

        List<CategoriaProduto> categorias = extracaoService.extrairCategoriasDoEstabelecimento(estabelecimento);

        if (categorias.isEmpty()) {
            return msg.texto(whatsappCliente, "No momento não encontrei categorias disponíveis para este estabelecimento.");
        }

        String cabecalho =
            "➕ Adicionar itens\n\n" +
                "Pedido #" + idPedido + "\n" +
                "Escolha uma categoria:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = categorias.stream()
            .sorted(java.util.Comparator.comparing(c -> msg.safe(c.getNome()), String.CASE_INSENSITIVE_ORDER))
            .map(c -> {
                Integer qm = c.getQuantidadeMultipla() == null ? 1 : c.getQuantidadeMultipla();
                return helper.row(
                    "COMANDO|REVISAO_LISTA_PRODUTOS|" + idPedido + "|" + c.getId() + "|" + qm,
                    msg.safe(c.getNome()),
                    "Clique para ver produtos"
                );
            })
            .toList();

        return msg.lista(whatsappCliente, cabecalho, "Categorias", "Categorias", itens);
    }

    public MensagemWhatsappSaidaDTO montarListaProdutosPorCategoriaPaginadaRevisao(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido,
        Long idCategoria,
        Integer quantidadeMultipla,
        Integer offset
    ) {

        List<Produto> produtos = extracaoService.extrairProdutosPorCategoria(estabelecimento, idCategoria);

        if (produtos.isEmpty()) {
            return msg.texto(whatsappCliente, "Não encontrei produtos para esta categoria.");
        }

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;
        int pageSizeProdutos = 9;

        List<Produto> ordenados = produtos.stream()
            .filter(Objects::nonNull)
            .sorted(java.util.Comparator.comparing(p -> msg.safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
            .toList();

        int total = ordenados.size();
        if (safeOffset >= total) safeOffset = 0;

        int endExclusive = Math.min(safeOffset + pageSizeProdutos, total);
        List<Produto> page = ordenados.subList(safeOffset, endExclusive);

        String nomeCategoria = extracaoService.extrairNomeCategoria(estabelecimento, idCategoria);
        String tituloCategoria = (nomeCategoria == null ? ("Categoria #" + idCategoria) : nomeCategoria);

        int paginaAtual = (safeOffset / pageSizeProdutos) + 1;
        int paginasTotal = (int) Math.ceil(total / (double) pageSizeProdutos);

        String cabecalho =
            "➕ Adicionar itens (Pedido #" + idPedido + ")\n" +
                "Produtos - " + tituloCategoria + ":\n" +
                "Página " + paginaAtual + " de " + paginasTotal;

        List<MensagemInterativaItemListaWhatsappDTO> itens = page.stream()
            .map(p -> {
                String nome = msg.safe(p.getNome());
                String preco = msg.formatarMoeda(p.getPreco());

                String title = msg.trunc(nome + " • " + preco, 24);

                String desc = msg.safe(p.getDescricao());
                String description = StringUtils.hasText(desc)
                    ? msg.trunc(desc, 72)
                    : msg.trunc("Unit: " + preco, 72);

                return MensagemInterativaItemListaWhatsappDTO.builder()
                    .id("COMANDO|REVISAO_LISTAR_QUANTIDADES|" + idPedido + "|" + idCategoria + "|" + quantidadeMultipla + "|" + p.getId())
                    .title(title)
                    .description(description)
                    .build();
            })
            .toList();

        List<MensagemInterativaItemListaWhatsappDTO> itensFinal = new ArrayList<>(itens);

        if (endExclusive < total) {
            int nextOffset = endExclusive;
            itensFinal.add(helper.row(
                "COMANDO|REVISAO_LISTA_PRODUTOS_PAG|" + idPedido + "|" + idCategoria + "|" + quantidadeMultipla + "|" + nextOffset,
                "➡️ Mais produtos",
                "Ver próxima página"
            ));
        }

        return msg.lista(
            whatsappCliente,
            msg.truncWord(cabecalho, 1024),
            msg.truncWord("Produtos", 20),
            msg.truncWord("Produtos", 24),
            itensFinal
        );
    }

    public MensagemWhatsappSaidaDTO montarListaQuantidadesRevisao(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido,
        Long idCategoria,
        Integer quantidadeMultipla,
        Long idProduto
    ) {

        Produto produto = extracaoService.extrairProduto(estabelecimento, idProduto);

        if (produto == null) {
            return msg.texto(whatsappCliente, "Produto não encontrado.");
        }

        int qm = (quantidadeMultipla == null || quantidadeMultipla < 1) ? 1 : quantidadeMultipla;

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (int i = 1; i <= 9; i++) {
            int quantidade = qm * i;
            BigDecimal preco = (produto.getPreco() == null ? BigDecimal.ZERO : produto.getPreco()).multiply(BigDecimal.valueOf(quantidade));

            itens.add(helper.row(
                "COMANDO|REVISAO_ADICIONAR_PRODUTO|" + idPedido + "|" + idProduto + "|" + quantidade,
                quantidade + " unidades",
                "Valor total: " + msg.formatarMoeda(preco)
            ));
        }

        String cabecalho =
            "➕ Adicionar itens (Pedido #" + idPedido + ")\n\n" +
                "Quantidades - " + msg.safe(produto.getNome()) + "\n" +
                "Escolha uma opção:";

        return msg.lista(whatsappCliente, cabecalho, "Quantidades", "Quantidades", itens);
    }

    public RoteamentoResultado tratarRevisaoAdicionarProduto(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido,
        Long idProduto,
        Integer quantidade
    ) {

        if (quantidade == null || quantidade < 1) {
            return new RoteamentoResultado(
                "revisao_quantidade_invalida",
                msg.texto(whatsappCliente, "Quantidade inválida.")
            );
        }

        PedidoResponseDTO atualizado = pedidoService.adicionarItemNoPedidoDoCliente(
            estabelecimento.getId(),
            idPedido,
            whatsappCliente,
            idProduto,
            quantidade
        );

        String textoCliente =
            "✅ Item adicionado ao pedido!\n\n" +
                "Pedido #" + idPedido + "\n" +
                "Total atualizado: " +
                msg.formatarMoeda(atualizado.getTotal() == null ? BigDecimal.ZERO : atualizado.getTotal()) +
                "\n\nDeseja fazer mais alguma alteração?";

        MensagemWhatsappSaidaDTO saidaCliente = msg.botoes(
            whatsappCliente,
            msg.trunc(textoCliente, 1024),
            List.of(
                helper.btn("COMANDO|PEDIDO_REVISAR|" + idPedido, "🔎 Voltar à revisão"),
                helper.btn("COMANDO|REVISAO_ADICIONAR_ITENS|" + idPedido, "➕ Adicionar mais"),
                helper.btn("COMANDO|MENU", "⬅️ Menu")
            )
        );

        Pedido pedidoEntidade = pedidoService.buscarEntidadeComItens(estabelecimento.getId(), idPedido);

        List<MensagemWhatsappSaidaDTO> extras =
            administradorWhatsappService.montarNotificacoesMudancaPedidoParaAdmins(
                estabelecimento,
                idPedido,
                whatsappCliente,
                "➕ Cliente adicionou itens",
                pedidoEntidade.getStatus(),
                administradorWhatsappService.montarResumoItensDoPedido(pedidoEntidade),
                pedidoEntidade.getTotal()
            );

        return new RoteamentoResultado("revisao_item_adicionado", saidaCliente, extras);
    }

    public RoteamentoResultado tratarRevisaoCancelarPedido(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido
    ) {

        pedidoService.cancelarPedidoDoCliente(estabelecimento.getId(), idPedido, whatsappCliente);

        MensagemWhatsappSaidaDTO saidaCliente = msg.texto(
            whatsappCliente,
            msg.trunc(
                "✅ Pedido cancelado.\n\n" +
                    "Pedido #" + idPedido + "\n" +
                    "Se quiser, envie *MENU* para voltar ao início.",
                4096
            )
        );

        Pedido pedidoEntidade = pedidoService.buscarEntidadeComItens(estabelecimento.getId(), idPedido);

        List<MensagemWhatsappSaidaDTO> extras =
            administradorWhatsappService.montarNotificacoesMudancaPedidoParaAdmins(
                estabelecimento,
                idPedido,
                whatsappCliente,
                "🗑️ Cliente cancelou o pedido",
                pedidoEntidade.getStatus(),
                administradorWhatsappService.montarResumoItensDoPedido(pedidoEntidade),
                pedidoEntidade.getTotal()
            );

        return new RoteamentoResultado("revisao_pedido_cancelado", saidaCliente, extras);
    }

    public RoteamentoResultado tratarRevisaoConfirmarEntrega(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido
    ) {

        pedidoService.confirmarEntregaDoCliente(estabelecimento.getId(), idPedido, whatsappCliente);

        MensagemWhatsappSaidaDTO saidaCliente = msg.texto(
            whatsappCliente,
            msg.trunc(
                "✅ Entrega confirmada! Obrigado. 🙂\n\n" +
                    "Pedido #" + idPedido + "\n" +
                    "Se precisar, envie *MENU*.",
                4096
            )
        );

        Pedido pedidoEntidade = pedidoService.buscarEntidadeComItens(estabelecimento.getId(), idPedido);

        List<MensagemWhatsappSaidaDTO> extras =
            administradorWhatsappService.montarNotificacoesMudancaPedidoParaAdmins(
                estabelecimento,
                idPedido,
                whatsappCliente,
                "✅ Cliente confirmou a entrega",
                pedidoEntidade.getStatus(),
                administradorWhatsappService.montarResumoItensDoPedido(pedidoEntidade),
                pedidoEntidade.getTotal()
            );

        return new RoteamentoResultado("revisao_entrega_confirmada", saidaCliente, extras);
    }
}