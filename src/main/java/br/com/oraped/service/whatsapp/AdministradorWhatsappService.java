// src/main/java/br/com/oraped/service/whatsapp/AdministradorWhatsappService.java
package br.com.oraped.service.whatsapp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.AdministradorEstabelecimento;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.ItemPedido;
import br.com.oraped.domain.MarcaProduto;
import br.com.oraped.domain.Pedido;
import br.com.oraped.domain.Produto;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.MarcaProdutoService;
import br.com.oraped.service.PedidoService;
import br.com.oraped.service.ProdutoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdministradorWhatsappService {

    // =========================================================
    // CONSTANTES / LIMITES
    // =========================================================

    private static final int LIST_MAX_ROWS = 10;
    private static final int PAGE_SIZE = 9;

    // =========================================================
    // DEPENDÊNCIAS
    // =========================================================

    private final EstabelecimentoService estabelecimentoService;
    private final PedidoService pedidoService;
    private final ProdutoService produtoService;
    private final MarcaProdutoService marcaProdutoService;
    private final SessaoAtendimentoWhatsappService sessaoService;

    private final WhatsappMensagemFactory msg;

    // =========================================================
    // TIPOS (retornos internos)
    // =========================================================

    public static class ResultadoAdmin {
        public final String chave;
        public final MensagemWhatsappSaidaDTO mensagem;

        public ResultadoAdmin(String chave, MensagemWhatsappSaidaDTO mensagem) {
            this.chave = chave;
            this.mensagem = mensagem;
        }
    }

    public static class ResultadoAdminPreco {
        public final ResultadoAdmin admin;
        public final BigDecimal novoPreco;
        public final String nomeProduto;
        public final String descricaoProduto;

        public ResultadoAdminPreco(
            ResultadoAdmin admin,
            BigDecimal novoPreco,
            String nomeProduto,
            String descricaoProduto
        ) {
            this.admin = admin;
            this.novoPreco = novoPreco;
            this.nomeProduto = nomeProduto;
            this.descricaoProduto = descricaoProduto;
        }
    }

    public static class ResultadoAdminMarca {
        public final ResultadoAdmin admin;
        public final Long idMarca;
        public final String nomeMarca;

        public ResultadoAdminMarca(ResultadoAdmin admin, Long idMarca, String nomeMarca) {
            this.admin = admin;
            this.idMarca = idMarca;
            this.nomeMarca = nomeMarca;
        }
    }

    public enum AcaoPedidoAdmin {
        ACEITAR,
        RECUSAR,
        PREPARAR,
        CANCELAR,
        INICIAR_ENTREGA
    }

    public static class ResultadoAdminAcaoPedido {
        public final ResultadoAdmin admin;
        public final String whatsappCliente;
        public final String textoCliente;

        public ResultadoAdminAcaoPedido(ResultadoAdmin admin, String whatsappCliente, String textoCliente) {
            this.admin = admin;
            this.whatsappCliente = whatsappCliente;
            this.textoCliente = textoCliente;
        }
    }

    // =========================================================
    // ADMINISTRADORES: LISTAGEM + PERMISSÃO
    // =========================================================

    public List<String> listarWhatsappsAdministradoresAtivos(Estabelecimento e) {

        if (e == null || e.getAdministradores() == null) return List.of();

        return e.getAdministradores().stream()
            .filter(Objects::nonNull)
            .filter(AdministradorEstabelecimento::isAtivo)
            .map(AdministradorEstabelecimento::getWhatsapp)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(msg::normalizarSomenteDigitos)
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.toList());
    }

    public boolean isAdminAtivo(Estabelecimento e, String whatsapp) {

        if (e == null || e.getAdministradores() == null) return false;

        String w = msg.normalizarSomenteDigitos(whatsapp);
        if (!StringUtils.hasText(w)) return false;

        return e.getAdministradores().stream()
            .filter(Objects::nonNull)
            .filter(AdministradorEstabelecimento::isAtivo)
            .map(AdministradorEstabelecimento::getWhatsapp)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(msg::normalizarSomenteDigitos)
            .anyMatch(w::equals);
    }

    // =========================================================
    // MENU PRINCIPAL DO ADMIN
    // =========================================================

    public ResultadoAdmin montarMenuAdmin(Estabelecimento estabelecimento, String whatsappAdmin) {

        validarBasico(estabelecimento, whatsappAdmin);

        String cabecalho =
            "🛠️ *Menu do Administrador*\n" +
                "*" + msg.safe(estabelecimento.getNome()) + "*\n\n" +
                "Escolha uma opção:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        if (estabelecimento.isAberto()) {
            itens.add(row("COMANDO|ADMIN_FECHAR_LOJA", "Fechar a loja", "Parar de aceitar pedidos"));
        } else {
            itens.add(row("COMANDO|ADMIN_ABRIR_LOJA", "Abrir loja", "Voltar a aceitar pedidos"));
        }

        itens.add(row(
            "COMANDO|ADMIN_VER_PEDIDOS|CRIADO|0",
            "Ver pedidos abertos",
            "Status: " + formatarStatusParaExibicao(StatusPedido.CRIADO)
        ));

        itens.add(row(
            "COMANDO|ADMIN_VER_PEDIDOS|EM_PREPARO|0",
            "Ver pedidos em preparo",
            "Status: " + formatarStatusParaExibicao(StatusPedido.EM_PREPARO)
        ));

        itens.add(row(
            "COMANDO|ADMIN_VER_PEDIDOS|PRONTO|0",
            "Ver pedidos em entrega",
            "Status: " + formatarStatusParaExibicao(StatusPedido.PRONTO)
        ));

        itens.add(row(
            "COMANDO|ADMIN_VER_PEDIDOS|ENTREGUE|0",
            "Ver pedidos entregues",
            "Status: " + formatarStatusParaExibicao(StatusPedido.ENTREGUE)
        ));

        itens.add(row("COMANDO|ADMIN_SUSPENDER_PRODUTO_MENU|0", "Suspender venda", "Tornar produto indisponível"));
        itens.add(row("COMANDO|ADMIN_LIBERAR_PRODUTO_MENU|0", "Liberar venda", "Tornar produto disponível"));

        itens.add(row("COMANDO|ADMIN_CARDAPIO_MENU", "Revisar cardápio", "Produtos e marcas"));

        return new ResultadoAdmin(
            "admin_menu",
            msg.lista(whatsappAdmin, msg.truncWord(cabecalho, 1024), "Ver opções", "Admin", itens)
        );
    }

    // =========================================================
    // ABRIR / FECHAR LOJA
    // =========================================================

    public ResultadoAdmin abrirLoja(Estabelecimento estabelecimento, String whatsappAdmin) {

        validarBasico(estabelecimento, whatsappAdmin);

        estabelecimentoService.abrir(estabelecimento.getId());

        String corpo =
            "✅ Loja *aberta*.\n\n" +
                "O estabelecimento agora está aceitando pedidos.";

        return new ResultadoAdmin(
            "admin_loja_aberta",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
            )
        );
    }

    public ResultadoAdmin fecharLoja(Estabelecimento estabelecimento, String whatsappAdmin) {

        validarBasico(estabelecimento, whatsappAdmin);

        estabelecimentoService.fechar(estabelecimento.getId());

        String corpo =
            "✅ Loja *fechada*.\n\n" +
                "O estabelecimento não aceitará novos pedidos.";

        return new ResultadoAdmin(
            "admin_loja_fechada",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
            )
        );
    }

    // =========================================================
    // NOTIFICAÇÃO PARA ADMIN (NOVO PEDIDO)
    // =========================================================

    public MensagemWhatsappSaidaDTO montarNotificacaoPedidoParaAdmin(
        String whatsappAdmin,
        Long idPedido,
        String whatsappCliente,
        String endereco,
        String observacoes,
        String resumoItens,
        BigDecimal total
    ) {

        if (!StringUtils.hasText(whatsappAdmin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappAdmin é obrigatório");
        }
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        String corpo =
            "🆕 *Novo pedido recebido*\n\n" +
                "*Pedido:* #" + idPedido + "\n" +
                "*Cliente:* " + msg.safe(whatsappCliente) + "\n\n" +
                "*Entrega:*\n" +
                msg.trunc(msg.safe(endereco), 700) + "\n" +
                (StringUtils.hasText(observacoes)
                    ? ("\n*Obs:* " + msg.trunc(msg.safe(observacoes), 250) + "\n")
                    : "\n"
                ) +
                "\n*Itens:*\n" +
                msg.trunc(msg.safe(resumoItens), 700) + "\n" +
                "\n*Total:* " + msg.formatarMoeda(total) + "\n\n" +
                "Deseja aceitar este pedido?";

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
            btn("COMANDO|ADMIN_ACEITAR_PEDIDO|" + idPedido, "✅ Aceitar pedido"),
            btn("COMANDO|ADMIN_RECUSAR_PEDIDO|" + idPedido, "❌ Recusar pedido")
        );

        return msg.botoes(whatsappAdmin, msg.trunc(corpo, 1024), botoes);
    }

    // =========================================================
    // PEDIDOS: LISTAGEM POR STATUS (PAGINADO)
    // =========================================================

    public ResultadoAdmin listarPedidosPorStatus(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        StatusPedido status,
        Integer offset
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status é obrigatório");
        }

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        List<Pedido> pedidos = pedidoService.listarPorStatus(
            estabelecimento.getId(),
            status,
            safeOffset,
            PAGE_SIZE + 1
        );

        boolean temProximaPagina = pedidos.size() > PAGE_SIZE;

        int maxPedidosNaTela = temProximaPagina ? 8 : 9;
        if (pedidos.size() > maxPedidosNaTela) {
            pedidos = pedidos.subList(0, maxPedidosNaTela);
        }

        int paginaAtual = (safeOffset / Math.max(1, maxPedidosNaTela)) + 1;

        String titulo =
            "📦 Pedidos - *" + formatarStatusParaExibicao(status) + "*\n" +
                "Página " + paginaAtual;

        if (pedidos.isEmpty()) {

            String corpo =
                titulo + "\n\n" +
                    "Nenhum pedido encontrado neste status.";

            return new ResultadoAdmin(
                "admin_pedidos_vazio",
                msg.botoes(
                    whatsappAdmin,
                    msg.trunc(corpo, 1024),
                    List.of(btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
                )
            );
        }

        List<MensagemInterativaItemListaWhatsappDTO> itens = pedidos.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(Pedido::getId).reversed())
            .map(p -> row(
                "COMANDO|ADMIN_PEDIDO_DETALHE|" + p.getId(),
                msg.trunc("#" + p.getId() + " • " + msg.formatarMoeda(p.getTotal()), 24),
                msg.trunc(montarDescricaoPedidoLista(p), 72)
            ))
            .collect(Collectors.toList());

        if (temProximaPagina) {
            int nextOffset = safeOffset + itens.size();
            itens.add(row(
                "COMANDO|ADMIN_VER_PEDIDOS|" + status.name() + "|" + nextOffset,
                "➡️ Mais pedidos",
                "Ver próxima página"
            ));
        }

        itens.add(row("COMANDO|ADMIN_MENU", "⬅️ Voltar", "Menu do administrador"));

        return new ResultadoAdmin(
            "admin_lista_pedidos",
            msg.lista(whatsappAdmin, msg.truncWord(titulo, 1024), "Pedidos", "Pedidos", itens)
        );
    }

    private String montarDescricaoPedidoLista(Pedido p) {

        String cliente = StringUtils.hasText(p.getClienteNome())
            ? msg.safe(p.getClienteNome())
            : msg.safe(p.getClienteTelefone());

        String extra = "";

        if (p.getTipoAtendimento() != null) {

            if (p.getTipoAtendimento() == br.com.oraped.domain.enums.TipoAtendimento.MESA) {

                extra = StringUtils.hasText(p.getNumeroMesa())
                    ? ("Mesa: " + msg.safe(p.getNumeroMesa()))
                    : "Mesa";

            } else {

                if (StringUtils.hasText(p.getEnderecoEntrega()) && pareceEndereco(p.getEnderecoEntrega())) {
                    extra = msg.safe(p.getEnderecoEntrega());
                } else {
                    extra = p.getTipoAtendimento().name();
                }
            }

        } else {

            if (StringUtils.hasText(p.getEnderecoEntrega()) && pareceEndereco(p.getEnderecoEntrega())) {
                extra = msg.safe(p.getEnderecoEntrega());
            }
        }

        return "Cliente: " + cliente + (StringUtils.hasText(extra) ? (" | " + extra) : "");
    }

    private boolean pareceEndereco(String s) {

        if (!StringUtils.hasText(s)) return false;

        String v = s.trim().toLowerCase(Locale.ROOT);

        boolean temNumero = v.chars().anyMatch(Character::isDigit);
        if (temNumero) return true;

        return v.contains("rua ")
            || v.contains("av ")
            || v.contains("avenida")
            || v.contains("travessa")
            || v.contains("estrada")
            || v.contains("bairro")
            || v.contains("cep");
    }

    // =========================================================
    // PEDIDOS: DETALHE + BOTÕES (ADMIN)
    // =========================================================

    public ResultadoAdmin montarDetalhePedido(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idPedido
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        Pedido p = pedidoService.buscarEntidadeComItens(estabelecimento.getId(), idPedido);

        StringBuilder sb = new StringBuilder();
        sb.append("📦 *Pedido #").append(p.getId()).append("*\n");
        sb.append("Status: *").append(formatarStatusParaExibicao(p.getStatus())).append("*\n\n");

        String cliente = StringUtils.hasText(p.getClienteTelefone())
            ? msg.safe(p.getClienteTelefone())
            : "(não informado)";

        sb.append("*Cliente:* ").append(cliente).append("\n");

        if (StringUtils.hasText(p.getEnderecoEntrega())) {
            sb.append("*Entrega:* ").append(msg.trunc(msg.safe(p.getEnderecoEntrega()), 350)).append("\n");
        }

        if (StringUtils.hasText(p.getObservacoes())) {
            sb.append("\n*Obs:* ").append(msg.trunc(msg.safe(p.getObservacoes()), 250)).append("\n");
        }

        String itens = montarResumoItensDoPedido(p);
        if (StringUtils.hasText(itens)) {
            sb.append("\n*Itens:*\n").append(msg.trunc(itens, 700)).append("\n");
        }

        sb.append("\n*Total:* ").append(msg.formatarMoeda(p.getTotal()));

        StatusPedido statusVoltar = statusListaVoltar(p.getStatus());

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = new ArrayList<>();
        botoes.addAll(montarBotoesAcaoDoPedido(p));
        botoes.add(btn("COMANDO|ADMIN_VER_PEDIDOS|" + statusVoltar.name() + "|0", "📦 Voltar à lista"));

        if (botoes.size() < 3) {
            botoes.add(btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"));
        }

        if (botoes.size() > 3) {
            botoes = botoes.subList(0, 3);
        }

        return new ResultadoAdmin(
            "admin_pedido_detalhe",
            msg.botoes(whatsappAdmin, msg.trunc(sb.toString(), 1024), botoes)
        );
    }

    private List<MensagemInterativaBotaoReplyWhatsappDTO> montarBotoesAcaoDoPedido(Pedido p) {

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = new ArrayList<>();

        StatusPedido st = p == null ? null : p.getStatus();
        Long idPedido = p == null ? null : p.getId();

        if (idPedido == null || st == null) return botoes;

        if (st == StatusPedido.CRIADO) {
            botoes.add(btn("COMANDO|ADMIN_CANCELAR_PEDIDO|" + idPedido, "❌ Cancelar pedido"));
            botoes.add(btn("COMANDO|ADMIN_PREPARAR_PEDIDO|" + idPedido, "🍳 Preparar pedido"));
        } else if (st == StatusPedido.EM_PREPARO) {
            botoes.add(btn("COMANDO|ADMIN_CANCELAR_PEDIDO|" + idPedido, "❌ Cancelar pedido"));
            botoes.add(btn("COMANDO|ADMIN_INICIAR_ENTREGA|" + idPedido, "🏍️ Saiu p/ entrega"));
        }

        return botoes;
    }

    // =========================================================
    // PEDIDOS: AÇÕES + NOTIFICAÇÃO DO CLIENTE
    // =========================================================

    public ResultadoAdminAcaoPedido executarAcaoPedido(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idPedido,
        AcaoPedidoAdmin acao
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }
        if (acao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "acao é obrigatória");
        }

        Pedido pedido;

        switch (acao) {

            case ACEITAR:
                pedido = pedidoService.aceitar(estabelecimento.getId(), idPedido);
                break;

            case RECUSAR:
                pedido = pedidoService.recusar(estabelecimento.getId(), idPedido);
                break;

            case PREPARAR:
                pedido = pedidoService.preparar(estabelecimento.getId(), idPedido);
                break;

            case CANCELAR:
                pedido = pedidoService.cancelar(estabelecimento.getId(), idPedido);
                break;

            case INICIAR_ENTREGA:
                pedido = pedidoService.iniciarEntrega(estabelecimento.getId(), idPedido);
                break;

            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ação inválida");
        }

        ResultadoAdmin detalheAtualizado = montarDetalhePedido(estabelecimento, whatsappAdmin, idPedido);

        String telCliente = msg.normalizarSomenteDigitos(pedido.getClienteTelefone());
        String textoCliente = montarTextoNotificacaoCliente(acao, pedido);

        return new ResultadoAdminAcaoPedido(detalheAtualizado, telCliente, textoCliente);
    }

    private String montarTextoNotificacaoCliente(AcaoPedidoAdmin acao, Pedido pedido) {

        Long idPedido = pedido == null ? null : pedido.getId();
        if (idPedido == null) idPedido = 0L;

        if (acao == AcaoPedidoAdmin.ACEITAR || acao == AcaoPedidoAdmin.PREPARAR) {
            return "🍳 Seu pedido *#" + idPedido + "* foi *aceito* e já está em *preparo*! 🙂";
        }

        if (acao == AcaoPedidoAdmin.RECUSAR) {
            return "❌ Seu pedido *#" + idPedido + "* foi *recusado* pelo estabelecimento.\n\n" +
                "Você pode tentar novamente em instantes.";
        }

        if (acao == AcaoPedidoAdmin.CANCELAR) {
            return "❌ Seu pedido *#" + idPedido + "* foi *cancelado* pelo estabelecimento.\n\n" +
                "Se precisar, você pode fazer um novo pedido.";
        }

        return "🏍️ Seu pedido *#" + idPedido + "* saiu para entrega!\n\n" +
            "Daqui a pouquinho ele chega. 🙂";
    }

    // =========================================================
    // CARDÁPIO: MENU PRINCIPAL
    // =========================================================

    public ResultadoAdmin montarMenuCardapio(Estabelecimento estabelecimento, String whatsappAdmin) {

        validarBasico(estabelecimento, whatsappAdmin);

        String cabecalho =
            "🧾 *Revisar cardápio*\n" +
                "*" + msg.safe(estabelecimento.getNome()) + "*\n\n" +
                "O que você deseja administrar?";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(row("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|0", "Produtos", "Ajustar / excluir"));
        itens.add(row("COMANDO|ADMIN_CARDAPIO_MARCAS_MENU|0", "Marcas", "Cadastrar / alterar / excluir"));

        itens.add(row("COMANDO|ADMIN_MENU", "⬅️ Voltar", "Menu do administrador"));

        return new ResultadoAdmin(
            "admin_cardapio_menu",
            msg.lista(whatsappAdmin, msg.truncWord(cabecalho, 1024), "Ver opções", "Cardápio", itens)
        );
    }

    // =========================================================
    // CARDÁPIO: PRODUTOS (LISTA PAGINADA <= 10 rows)
    // =========================================================

    public ResultadoAdmin montarMenuCardapioProdutos(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offset
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        List<Produto> produtos = produtoService.listarPorEstabelecimento(estabelecimento.getId());
        if (produtos == null) produtos = List.of();

        List<Produto> ordenados = produtos.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(p -> msg.safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        int total = ordenados.size();

        if (total == 0) {

            String corpo =
                "🧾 *Produtos do cardápio*\n\n" +
                    "Nenhum produto cadastrado.";

            return new ResultadoAdmin(
                "admin_cardapio_produtos_vazio",
                msg.botoes(
                    whatsappAdmin,
                    msg.trunc(corpo, 1024),
                    List.of(
                        btn("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Voltar"),
                        btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                    )
                )
            );
        }

        if (safeOffset >= total) safeOffset = 0;

        boolean precisaPaginacao = total > LIST_MAX_ROWS;
        int pageSizeBase = precisaPaginacao ? 8 : 9;
        int paginasTotal = precisaPaginacao ? (int) Math.ceil(total / (double) pageSizeBase) : 1;
        int paginaAtual = (safeOffset / pageSizeBase) + 1;

        int endExclusive = Math.min(safeOffset + pageSizeBase, total);
        List<Produto> page = ordenados.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < total;

        String cabecalho =
            "🧾 *Produtos do cardápio*\n" +
                (paginasTotal > 1
                    ? ("Página " + paginaAtual + " de " + paginasTotal)
                    : "Página 1"
                );

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (Produto p : page) {

            String nome = msg.safe(p.getNome());
            String preco = msg.formatarMoeda(p.getPreco());

            String title = msg.trunc(nome + " • " + preco, 24);

            String desc = msg.safe(p.getDescricao());
            String description = StringUtils.hasText(desc)
                ? msg.trunc(desc, 72)
                : msg.trunc("Preço: " + preco, 72);

            itens.add(MensagemInterativaItemListaWhatsappDTO.builder()
                .id("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + p.getId() + "|" + safeOffset)
                .title(title)
                .description(description)
                .build());
        }

        if (temMais) {
            int nextOffset = safeOffset + page.size();
            itens.add(row("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + nextOffset, "➡️ Mais produtos", "Ver próxima página"));
        }

        itens.add(row("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Voltar", "Revisar cardápio"));

        return new ResultadoAdmin(
            "admin_cardapio_produtos_menu",
            msg.lista(whatsappAdmin, msg.truncWord(cabecalho, 1024), "Produtos", "Produtos", itens)
        );
    }

    // =========================================================
    // CARDÁPIO: PRODUTO (AÇÕES)
    // =========================================================

    public ResultadoAdmin montarMenuAcoesProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String nome = msg.trunc(msg.safe(p.getNome()), 80);

        String descricao = msg.safe(p.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String preco = msg.formatarMoeda(p.getPreco());

        String cabecalho =
            "*" + nome + "*\n" +
                msg.trunc(descricao, 500) + "\n\n" +
                "*Preço atual:* " + preco + "\n\n" +
                "O que deseja fazer?";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(row("COMANDO|ADMIN_PROD_PRECO_MENU|" + idProduto + "|" + safeOffset, "Ajustar preço", "Incrementos ou informar valor"));

        itens.add(row("COMANDO|ADMIN_PROD_NOME_MENU|" + idProduto + "|" + safeOffset, "Ajustar nome", "Enviar 1 mensagem com o novo nome"));
        itens.add(row("COMANDO|ADMIN_PROD_DESC_MENU|" + idProduto + "|" + safeOffset, "Ajustar descrição", "Enviar 1 mensagem com a nova descrição"));

        itens.add(row("COMANDO|ADMIN_PROD_EXCLUIR_CONFIRM|" + idProduto + "|" + safeOffset, "Excluir produto", "Remover do cardápio"));

        itens.add(row("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + safeOffset, "⬅️ Voltar", "Lista de produtos"));

        return new ResultadoAdmin(
            "admin_cardapio_produto_acoes",
            msg.lista(whatsappAdmin, msg.truncWord(cabecalho, 1024), "Ações", "Ações", itens)
        );
    }

    // =========================================================
    // CARDÁPIO: AJUSTE DE PREÇO (LISTA <= 10)
    // =========================================================

    public ResultadoAdmin montarMenuAjustePrecoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String nome = msg.trunc(msg.safe(p.getNome()), 80);

        String descricao = msg.safe(p.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String preco = msg.formatarMoeda(p.getPreco());

        String cabecalho =
            "💲 Ajustar preço\n\n" +
                "*" + nome + "*\n" +
                msg.trunc(descricao, 500) + "\n\n" +
                "*Preço atual:* " + preco + "\n\n" +
                "Escolha um ajuste:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|100|" + safeOffset, "+ R$ 1,00", "Aumentar"));
        itens.add(row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|200|" + safeOffset, "+ R$ 2,00", "Aumentar"));
        itens.add(row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|500|" + safeOffset, "+ R$ 5,00", "Aumentar"));

        itens.add(row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-100|" + safeOffset, "- R$ 1,00", "Diminuir"));
        itens.add(row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-200|" + safeOffset, "- R$ 2,00", "Diminuir"));
        itens.add(row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-500|" + safeOffset, "- R$ 5,00", "Diminuir"));

        itens.add(row("COMANDO|ADMIN_PROD_PRECO_MANUAL|" + idProduto + "|" + safeOffset, "Outro valor (digitar)", "Enviar 1 mensagem com o valor"));

        itens.add(row("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + safeOffset, "⬅️ Voltar", "Ações do produto"));

        return new ResultadoAdmin(
            "admin_prod_preco_menu",
            msg.lista(whatsappAdmin, msg.truncWord(cabecalho, 1024), "Preço", "Preço", itens)
        );
    }

    public ResultadoAdminPreco aplicarDeltaPrecoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer deltaCentavos,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }
        if (deltaCentavos == null || deltaCentavos == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deltaCentavos é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        BigDecimal atual = p.getPreco() == null ? BigDecimal.ZERO : p.getPreco();
        BigDecimal delta = BigDecimal.valueOf(deltaCentavos).movePointLeft(2);

        BigDecimal novo = atual.add(delta);
        if (novo.compareTo(BigDecimal.ZERO) < 0) novo = BigDecimal.ZERO;

        produtoService.atualizarPreco(idProduto, novo);

        String nome = msg.trunc(msg.safe(p.getNome()), 80);
        String desc = msg.safe(p.getDescricao());
        if (!StringUtils.hasText(desc)) desc = "Sem descrição.";

        ResultadoAdmin lista = montarMenuCardapioProdutos(estabelecimento, whatsappAdmin, safeOffset);

        return new ResultadoAdminPreco(lista, novo, nome, desc);
    }

    // =========================================================
    // PRODUTO: PREÇO POR DIGITAÇÃO LIVRE
    // =========================================================

    public ResultadoAdmin iniciarPrecoManualProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        sessaoService.marcarAguardandoNovoPreco(idSessao, idProduto, safeOffset);

        String corpo =
            "💲 *Ajustar preço*\n\n" +
                "*" + msg.trunc(msg.safe(p.getNome()), 80) + "*\n\n" +
                "Agora envie apenas o *novo preço*.\n\n" +
                "Exemplos:\n" +
                "- 10\n" +
                "- 10,50\n" +
                "- R$ 10,50";

        return new ResultadoAdmin(
            "admin_prod_preco_digitacao",
            msg.texto(whatsappAdmin, msg.trunc(corpo, 1024))
        );
    }

    public ResultadoAdminPreco concluirPrecoManualProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String textoDigitado
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (!StringUtils.hasText(textoDigitado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "textoDigitado é obrigatório");
        }

        if (!sessaoService.isAguardandoNovoPreco(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo preço");
        }

        Long idProduto = sessaoService.getIdProdutoNovoPreco(idSessao);
        int safeOffset = sessaoService.getOffsetListaNovoPreco(idSessao);

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        BigDecimal novoPreco = parsePrecoDigitado(textoDigitado);
        if (novoPreco.compareTo(BigDecimal.ZERO) < 0) novoPreco = BigDecimal.ZERO;

        produtoService.atualizarPreco(idProduto, novoPreco);
        sessaoService.limparAguardandoNovoPreco(idSessao);

        String nome = msg.trunc(msg.safe(p.getNome()), 80);
        String desc = msg.safe(p.getDescricao());
        if (!StringUtils.hasText(desc)) desc = "Sem descrição.";

        ResultadoAdmin lista = montarMenuCardapioProdutos(estabelecimento, whatsappAdmin, safeOffset);

        return new ResultadoAdminPreco(lista, novoPreco, nome, desc);
    }

    // =========================================================
    // PRODUTO: NOME / DESCRIÇÃO POR DIGITAÇÃO LIVRE
    // =========================================================

    public ResultadoAdmin iniciarAlteracaoNomeProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        sessaoService.marcarAguardandoNovoNomeProduto(idSessao, idProduto, safeOffset);

        String corpo =
            "✏️ *Ajustar nome*\n\n" +
                "Atual: *" + msg.trunc(msg.safe(p.getNome()), 80) + "*\n\n" +
                "Agora envie apenas o *novo nome*.\n\n" +
                "Exemplos:\n" +
                "- Coca-Cola 2L\n" +
                "- Heineken Lata 350ml";

        return new ResultadoAdmin(
            "admin_prod_nome_digitacao",
            msg.texto(whatsappAdmin, msg.trunc(corpo, 1024))
        );
    }

    public ResultadoAdmin concluirAlteracaoNomeProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novoNome
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (!StringUtils.hasText(novoNome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoNome é obrigatório");
        }

        if (!sessaoService.isAguardandoNovoNomeProduto(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo nome do produto");
        }

        Long idProduto = sessaoService.getIdProdutoNovoNome(idSessao);
        int safeOffset = sessaoService.getOffsetListaNovoNome(idSessao);

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String nomeLimpo = novoNome.trim();
        produtoService.atualizarNome(idProduto, nomeLimpo);
        sessaoService.limparAguardandoNovoNomeProduto(idSessao);

        String corpo =
            "✅ Nome atualizado.\n\n" +
                "Produto: *" + msg.trunc(msg.safe(nomeLimpo), 80) + "*";

        return new ResultadoAdmin(
            "admin_prod_nome_ok",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(
                    btn("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + safeOffset, "🧾 Voltar ao produto"),
                    btn("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + safeOffset, "📦 Voltar à lista")
                )
            )
        );
    }

    public ResultadoAdmin iniciarAlteracaoDescricaoProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        sessaoService.marcarAguardandoNovaDescricaoProduto(idSessao, idProduto, safeOffset);

        String corpo =
            "📝 *Ajustar descrição*\n\n" +
                "Produto: *" + msg.trunc(msg.safe(p.getNome()), 80) + "*\n\n" +
                "Agora envie apenas a *nova descrição*.\n\n" +
                "Dica: tente manter curto.";

        return new ResultadoAdmin(
            "admin_prod_desc_digitacao",
            msg.texto(whatsappAdmin, msg.trunc(corpo, 1024))
        );
    }

    public ResultadoAdmin concluirAlteracaoDescricaoProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novaDesc
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (!StringUtils.hasText(novaDesc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novaDesc é obrigatória");
        }

        if (!sessaoService.isAguardandoNovaDescricaoProduto(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando nova descrição do produto");
        }

        Long idProduto = sessaoService.getIdProdutoNovaDescricao(idSessao);
        int safeOffset = sessaoService.getOffsetListaNovaDescricao(idSessao);

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String descLimpa = novaDesc.trim();
        produtoService.atualizarDescricao(idProduto, descLimpa);
        sessaoService.limparAguardandoNovaDescricaoProduto(idSessao);

        String corpo =
            "✅ Descrição atualizada.\n\n" +
                "Produto: *" + msg.trunc(msg.safe(p.getNome()), 80) + "*";

        return new ResultadoAdmin(
            "admin_prod_desc_ok",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(
                    btn("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + safeOffset, "🧾 Voltar ao produto"),
                    btn("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + safeOffset, "📦 Voltar à lista")
                )
            )
        );
    }

    // =========================================================
    // PRODUTO: EXCLUSÃO (CONFIRMAÇÃO + AÇÃO)
    // =========================================================

    public ResultadoAdmin confirmarExclusaoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String corpo =
            "⚠️ *Excluir produto*\n\n" +
                "*" + msg.trunc(msg.safe(p.getNome()), 80) + "*\n" +
                "Preço: " + msg.formatarMoeda(p.getPreco()) + "\n\n" +
                "Tem certeza que deseja excluir?";

        return new ResultadoAdmin(
            "admin_prod_excluir_confirm",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(
                    btn("COMANDO|ADMIN_PROD_EXCLUIR|" + idProduto + "|" + safeOffset, "🗑️ Excluir"),
                    btn("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + safeOffset, "⬅️ Cancelar")
                )
            )
        );
    }

    public ResultadoAdmin excluirProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        produtoService.excluir(idProduto);

        String corpo =
            "🗑️ Produto excluído.\n\n" +
                "*" + msg.trunc(msg.safe(p.getNome()), 80) + "*";

        return new ResultadoAdmin(
            "admin_prod_excluir_ok",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(
                    btn("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + safeOffset, "🧾 Voltar à lista"),
                    btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }

    // =========================================================
    // MARCAS: LISTA (PAGINADA <= 10 rows)
    // - inclui sempre "➕ Cadastrar nova marca"
    // =========================================================

    public ResultadoAdmin montarMenuMarcas(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offset
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        List<MarcaProduto> todas = marcaProdutoService.listarPorEstabelecimento(estabelecimento.getId());
        if (todas == null) todas = List.of();

        List<MarcaProduto> base = todas.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(m -> msg.safe(m.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        int total = base.size();

        if (total == 0) {

            String corpo =
                "🏷️ *Marcas*\n\n" +
                    "Nenhuma marca cadastrada.\n\n" +
                    "Use a opção abaixo para cadastrar.";

            return new ResultadoAdmin(
                "admin_marcas_vazio",
                msg.botoes(
                    whatsappAdmin,
                    msg.trunc(corpo, 1024),
                    List.of(
                        btn("COMANDO|ADMIN_MARCA_NOVA_MENU|0", "➕ Nova marca"),
                        btn("COMANDO|ADMIN_CARDAPIO_MENU", "🧾 Cardápio"),
                        btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                    )
                )
            );
        }

        if (safeOffset >= total) safeOffset = 0;

        boolean precisaPaginacao = total > (LIST_MAX_ROWS - 2);
        int pageSizeBase = precisaPaginacao ? 7 : 8;

        int paginasTotal = precisaPaginacao ? (int) Math.ceil(total / (double) pageSizeBase) : 1;
        int paginaAtual = (safeOffset / pageSizeBase) + 1;

        int endExclusive = Math.min(safeOffset + pageSizeBase, total);
        List<MarcaProduto> page = base.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < total;

        String cabecalho =
            "🏷️ *Marcas*\n" +
                (paginasTotal > 1
                    ? ("Página " + paginaAtual + " de " + paginasTotal)
                    : "Página 1"
                );

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(row(
            "COMANDO|ADMIN_MARCA_NOVA_MENU|" + safeOffset,
            "➕ Cadastrar nova marca",
            "Adicionar uma marca ao cardápio"
        ));

        for (MarcaProduto m : page) {
            String nome = msg.safe(m.getNome());
            String status = m.isAtiva() ? "Ativa" : "Inativa";

            itens.add(MensagemInterativaItemListaWhatsappDTO.builder()
                .id("COMANDO|ADMIN_MARCA_DETALHE|" + m.getId() + "|" + safeOffset)
                .title(msg.trunc(nome, 24))
                .description(msg.trunc("Status: " + status, 72))
                .build());
        }

        if (temMais) {
            int nextOffset = safeOffset + page.size();
            itens.add(row("COMANDO|ADMIN_CARDAPIO_MARCAS_MENU|" + nextOffset, "➡️ Mais marcas", "Ver próxima página"));
        }

        itens.add(row("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Voltar", "Revisar cardápio"));

        return new ResultadoAdmin(
            "admin_marcas_menu",
            msg.lista(whatsappAdmin, msg.truncWord(cabecalho, 1024), "Marcas", "Marcas", itens)
        );
    }

    // =========================================================
    // MARCA: DETALHE (com offset da lista)
    // =========================================================

    public ResultadoAdmin montarDetalheMarca(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idMarca,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        MarcaProduto m = marcaProdutoService.buscarObrigatorio(idMarca);
        validarMarcaDoEstabelecimento(estabelecimento, m);

        String corpo =
            "🏷️ *Marca*\n\n" +
                "*" + msg.trunc(msg.safe(m.getNome()), 120) + "*\n" +
                "Status: " + (m.isAtiva() ? "Ativa" : "Inativa") + "\n\n" +
                "O que deseja fazer?";

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = new ArrayList<>();

        botoes.add(btn("COMANDO|ADMIN_MARCA_EDITAR_MENU|" + idMarca + "|" + safeOffset, "✏️ Alterar nome"));
        botoes.add(btn("COMANDO|ADMIN_MARCA_EXCLUIR_CONFIRM|" + idMarca + "|" + safeOffset, "🗑️ Excluir"));

        if (botoes.size() < 3) {
            botoes.add(btn("COMANDO|ADMIN_CARDAPIO_MARCAS_MENU|" + safeOffset, "⬅️ Voltar"));
        }

        return new ResultadoAdmin(
            "admin_marca_detalhe",
            msg.botoes(whatsappAdmin, msg.trunc(corpo, 1024), botoes)
        );
    }

    // =========================================================
    // MARCAS: DIGITAÇÃO LIVRE (INICIAR / CONCLUIR)
    // =========================================================

    public ResultadoAdmin iniciarCadastroMarcaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        sessaoService.marcarAguardandoNovaMarca(idSessao, safeOffset);

        String corpo =
            "➕ *Cadastrar nova marca*\n\n" +
                "Agora envie apenas o *nome da marca*.\n\n" +
                "Exemplos:\n" +
                "- Coca-Cola\n" +
                "- Heineken\n" +
                "- Skol";

        return new ResultadoAdmin(
            "admin_marca_nova_digitacao",
            msg.texto(whatsappAdmin, msg.trunc(corpo, 1024))
        );
    }

    public ResultadoAdminMarca concluirCadastroMarcaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String nomeMarca
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (!StringUtils.hasText(nomeMarca)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nomeMarca é obrigatório");
        }

        if (!sessaoService.isAguardandoNovaMarca(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando nova marca");
        }

        int safeOffset = sessaoService.getOffsetNovaMarca(idSessao);

        MarcaProduto m = marcaProdutoService.criar(estabelecimento.getId(), nomeMarca.trim());
        sessaoService.limparAguardandoNovaMarca(idSessao);

        ResultadoAdmin lista = montarMenuMarcas(estabelecimento, whatsappAdmin, safeOffset);

        return new ResultadoAdminMarca(lista, m.getId(), m.getNome());
    }

    public ResultadoAdmin iniciarAlteracaoNomeMarcaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idMarca,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        MarcaProduto m = marcaProdutoService.buscarObrigatorio(idMarca);
        validarMarcaDoEstabelecimento(estabelecimento, m);

        sessaoService.marcarAguardandoEditarMarcaNome(idSessao, idMarca, safeOffset);

        String corpo =
            "✏️ *Alterar nome da marca*\n\n" +
                "Atual: *" + msg.trunc(msg.safe(m.getNome()), 120) + "*\n\n" +
                "Agora envie apenas o *novo nome*.\n\n" +
                "Exemplos:\n" +
                "- Coca-Cola (Oficial)\n" +
                "- Heineken (Lata)";

        return new ResultadoAdmin(
            "admin_marca_editar_digitacao",
            msg.texto(whatsappAdmin, msg.trunc(corpo, 1024))
        );
    }

    public ResultadoAdminMarca concluirAlteracaoNomeMarcaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novoNome
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (!StringUtils.hasText(novoNome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoNome é obrigatório");
        }

        if (!sessaoService.isAguardandoEditarMarcaNome(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando edição do nome da marca");
        }

        Long idMarca = sessaoService.getIdMarcaEditarNome(idSessao);
        int safeOffset = sessaoService.getOffsetEditarMarcaNome(idSessao);

        MarcaProduto m = marcaProdutoService.buscar(idMarca, estabelecimento.getId());
        validarMarcaDoEstabelecimento(estabelecimento, m);

        String nomeLimpo = novoNome.trim();
        marcaProdutoService.atualizarNome(idMarca, estabelecimento.getId(), nomeLimpo);
        sessaoService.limparAguardandoEditarMarcaNome(idSessao);

        ResultadoAdmin lista = montarMenuMarcas(estabelecimento, whatsappAdmin, safeOffset);

        return new ResultadoAdminMarca(lista, idMarca, nomeLimpo);
    }

    // =========================================================
    // MARCAS: EXCLUSÃO (CONFIRMAÇÃO + AÇÃO) - COM OFFSET
    // =========================================================

    public ResultadoAdmin confirmarExclusaoMarca(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idMarca,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        MarcaProduto m = marcaProdutoService.buscarObrigatorio(idMarca);
        validarMarcaDoEstabelecimento(estabelecimento, m);

        String corpo =
            "⚠️ *Excluir marca*\n\n" +
                "*" + msg.trunc(msg.safe(m.getNome()), 120) + "*\n\n" +
                "Tem certeza que deseja excluir?";

        return new ResultadoAdmin(
            "admin_marca_excluir_confirm",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(
                    btn("COMANDO|ADMIN_MARCA_EXCLUIR|" + idMarca + "|" + safeOffset, "🗑️ Excluir"),
                    btn("COMANDO|ADMIN_MARCA_DETALHE|" + idMarca + "|" + safeOffset, "⬅️ Cancelar")
                )
            )
        );
    }

    public ResultadoAdmin excluirMarca(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idMarca,
        Integer offsetLista
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }
        if (estabelecimento.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        MarcaProduto m = marcaProdutoService.buscar(idMarca, estabelecimento.getId());
        validarMarcaDoEstabelecimento(estabelecimento, m);

        marcaProdutoService.excluir(idMarca, estabelecimento.getId());

        String corpo =
            "🗑️ Marca excluída.\n\n" +
                "*" + msg.trunc(msg.safe(m.getNome()), 120) + "*";

        return new ResultadoAdmin(
            "admin_marca_excluir_ok",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(
                    btn("COMANDO|ADMIN_CARDAPIO_MARCAS_MENU|" + safeOffset, "🏷️ Voltar à lista"),
                    btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }

    private void validarMarcaDoEstabelecimento(Estabelecimento estabelecimento, MarcaProduto marca) {

        if (marca == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Marca não encontrada");
        }
        if (marca.getEstabelecimento() == null || marca.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Marca sem estabelecimento associado");
        }
        if (!Objects.equals(marca.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Marca não pertence ao estabelecimento");
        }
    }

    // =========================================================
    // SUSPENDER / LIBERAR PRODUTO (LISTAS <= 10 rows)
    // =========================================================

    public ResultadoAdmin listarProdutosParaSuspender(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offset
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        List<Produto> todos = produtoService.listarPorEstabelecimento(estabelecimento.getId());
        List<Produto> disponiveis = (todos == null ? List.<Produto>of() : todos).stream()
            .filter(Objects::nonNull)
            .filter(Produto::isDisponivelParaVenda)
            .sorted(Comparator.comparing(p -> msg.safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        if (disponiveis.isEmpty()) {

            String corpo =
                "🚫 Suspender venda\n\n" +
                    "Não há produtos *disponíveis* para suspender.";

            return new ResultadoAdmin(
                "admin_suspender_sem_produtos",
                msg.botoes(
                    whatsappAdmin,
                    msg.trunc(corpo, 1024),
                    List.of(btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
                )
            );
        }

        return montarListaProdutosTogglePaginada(
            whatsappAdmin,
            "🚫 Suspender venda\nEscolha um produto:",
            disponiveis,
            safeOffset,
            "COMANDO|ADMIN_SUSPENDER_PRODUTO|",
            "COMANDO|ADMIN_SUSPENDER_PRODUTO_MENU|"
        );
    }

    public ResultadoAdmin listarProdutosParaLiberar(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offset
    ) {

        validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        List<Produto> todos = produtoService.listarPorEstabelecimento(estabelecimento.getId());
        List<Produto> indisponiveis = (todos == null ? List.<Produto>of() : todos).stream()
            .filter(Objects::nonNull)
            .filter(p -> !p.isDisponivelParaVenda())
            .sorted(Comparator.comparing(p -> msg.safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        if (indisponiveis.isEmpty()) {

            String corpo =
                "✅ Liberar venda\n\n" +
                    "Não há produtos *suspensos* para liberar.";

            return new ResultadoAdmin(
                "admin_liberar_sem_produtos",
                msg.botoes(
                    whatsappAdmin,
                    msg.trunc(corpo, 1024),
                    List.of(btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
                )
            );
        }

        return montarListaProdutosTogglePaginada(
            whatsappAdmin,
            "✅ Liberar venda\nEscolha um produto:",
            indisponiveis,
            safeOffset,
            "COMANDO|ADMIN_LIBERAR_PRODUTO|",
            "COMANDO|ADMIN_LIBERAR_PRODUTO_MENU|"
        );
    }

    public ResultadoAdmin suspenderProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        produtoService.indisponibilizar(idProduto);

        String corpo =
            "🚫 Venda suspensa ✅\n\n" +
                "*" + msg.safe(p.getNome()) + "*\n" +
                "Agora este produto não pode ser pedido pelo WhatsApp.";

        return new ResultadoAdmin(
            "admin_produto_suspenso",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(
                    btn("COMANDO|ADMIN_SUSPENDER_PRODUTO_MENU|0", "🚫 Suspender outro"),
                    btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }

    public ResultadoAdmin liberarProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto) {

        validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        produtoService.disponibilizar(idProduto);

        String corpo =
            "✅ Venda liberada ✅\n\n" +
                "*" + msg.safe(p.getNome()) + "*\n" +
                "Agora este produto pode ser pedido pelo WhatsApp.";

        return new ResultadoAdmin(
            "admin_produto_liberado",
            msg.botoes(
                whatsappAdmin,
                msg.trunc(corpo, 1024),
                List.of(
                    btn("COMANDO|ADMIN_LIBERAR_PRODUTO_MENU|0", "✅ Liberar outro"),
                    btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }

    private ResultadoAdmin montarListaProdutosTogglePaginada(
        String whatsappAdmin,
        String cabecalhoBase,
        List<Produto> base,
        int offset,
        String cmdPrefixAcao,
        String cmdPrefixPagina
    ) {

        int total = base == null ? 0 : base.size();
        if (total == 0) {
            return new ResultadoAdmin(
                "admin_lista_produtos_vazio",
                msg.botoes(whatsappAdmin, "Nenhum produto encontrado.", List.of(btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")))
            );
        }

        int safeOffset = Math.max(0, offset);
        if (safeOffset >= total) safeOffset = 0;

        boolean precisaPaginacao = total > LIST_MAX_ROWS;
        int pageSizeBase = precisaPaginacao ? 8 : 9;
        int paginasTotal = precisaPaginacao ? (int) Math.ceil(total / (double) pageSizeBase) : 1;
        int paginaAtual = (safeOffset / pageSizeBase) + 1;

        int endExclusive = Math.min(safeOffset + pageSizeBase, total);
        List<Produto> page = base.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < total;

        String cabecalho =
            cabecalhoBase + "\n" +
                (paginasTotal > 1
                    ? ("Página " + paginaAtual + " de " + paginasTotal)
                    : "Página 1"
                );

        List<MensagemInterativaItemListaWhatsappDTO> itens = page.stream()
            .map(p -> {
                String nome = msg.safe(p.getNome());
                String preco = msg.formatarMoeda(p.getPreco());

                String title = msg.trunc(nome + " • " + preco, 24);

                String desc = StringUtils.hasText(p.getDescricao())
                    ? msg.trunc(p.getDescricao(), 72)
                    : msg.trunc("Preço: " + preco, 72);

                return MensagemInterativaItemListaWhatsappDTO.builder()
                    .id(cmdPrefixAcao + p.getId())
                    .title(title)
                    .description(desc)
                    .build();
            })
            .collect(Collectors.toList());

        if (temMais) {
            int nextOffset = safeOffset + page.size();
            itens.add(row(cmdPrefixPagina + nextOffset, "➡️ Mais produtos", "Ver próxima página"));
        }

        itens.add(row("COMANDO|ADMIN_MENU", "⬅️ Voltar", "Menu do administrador"));

        return new ResultadoAdmin(
            "admin_lista_produtos",
            msg.lista(whatsappAdmin, msg.truncWord(cabecalho, 1024), "Produtos", "Produtos", itens)
        );
    }

    private void validarProdutoDoEstabelecimento(Estabelecimento estabelecimento, Produto produto) {

        if (produto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }
        if (produto.getEstabelecimento() == null || produto.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto sem estabelecimento associado");
        }
        if (!Objects.equals(produto.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto não pertence ao estabelecimento");
        }
    }

    // =========================================================
    // HELPERS: STATUS / RESUMO
    // =========================================================

    private StatusPedido statusListaVoltar(StatusPedido st) {

        if (st == null) return StatusPedido.CRIADO;

        if (st == StatusPedido.PRONTO) {
            return StatusPedido.EM_PREPARO;
        }

        return st;
    }

    private String formatarStatusParaExibicao(StatusPedido st) {

        if (st == null) return "N/D";

        switch (st) {
            case CRIADO:
                return "ABERTO";
            case EM_PREPARO:
                return "EM PREPARO";
            case PRONTO:
                return "SAIU P/ ENTREGA";
            case ENTREGUE:
                return "ENTREGUE";
            case CANCELADO:
                return "CANCELADO";
            default:
                return st.name();
        }
    }

    private String montarResumoItensDoPedido(Pedido pedido) {

        if (pedido == null || pedido.getItens() == null || pedido.getItens().isEmpty()) return "(sem itens)";

        StringBuilder sb = new StringBuilder();

        for (ItemPedido it : pedido.getItens()) {

            if (it == null) continue;

            Produto p = it.getProduto();
            String nome = (p == null ? "Produto" : msg.safe(p.getNome()));

            int qtd = it.getQuantidade() == null ? 0 : it.getQuantidade();

            BigDecimal subtotalItem = it.getSubtotalItem();
            if (subtotalItem == null) {
                BigDecimal unit = it.getPrecoUnitarioProduto() == null ? BigDecimal.ZERO : it.getPrecoUnitarioProduto();
                subtotalItem = unit.multiply(BigDecimal.valueOf(qtd));
            }

            sb.append("- ").append(nome)
                .append(" x").append(qtd)
                .append(" = ").append(msg.formatarMoeda(subtotalItem))
                .append("\n");
        }

        return sb.toString().trim();
    }

    // =========================================================
    // PARSERS (texto livre)
    // =========================================================

    private BigDecimal parsePrecoDigitado(String texto) {

        String v = texto == null ? "" : texto.trim();

        if (!StringUtils.hasText(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }

        v = v.replace("R$", "")
            .replace("r$", "")
            .replace(" ", "")
            .trim();

        // aceita "10,50" ou "10.50"
        v = v.replace(",", ".");

        // remove qualquer coisa que não seja número, ponto, ou sinal
        v = v.replaceAll("[^0-9.\\-+]", "");

        if (!StringUtils.hasText(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }

        try {
            BigDecimal bd = new BigDecimal(v);
            return bd.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }
    }

    // =========================================================
    // BUILDERS (reduzir duplicação)
    // =========================================================

    private MensagemInterativaItemListaWhatsappDTO row(String id, String title, String description) {
        return MensagemInterativaItemListaWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 24))
            .description(msg.trunc(msg.safe(description), 72))
            .build();
    }

    private MensagemInterativaBotaoReplyWhatsappDTO btn(String id, String title) {
        return MensagemInterativaBotaoReplyWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 20))
            .build();
    }

    // =========================================================
    // VALIDAÇÃO BÁSICA
    // =========================================================

    private void validarBasico(Estabelecimento estabelecimento, String whatsappAdmin) {
        if (estabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }
        if (!StringUtils.hasText(whatsappAdmin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappAdmin é obrigatório");
        }
    }
}