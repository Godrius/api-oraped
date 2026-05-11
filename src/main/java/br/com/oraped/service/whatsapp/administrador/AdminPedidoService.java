package br.com.oraped.service.whatsapp.administrador;

import java.math.BigDecimal;
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

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.pedido.ItemPedido;
import br.com.oraped.domain.pedido.ItemPedidoOpcional;
import br.com.oraped.domain.pedido.Pedido;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.pedido.PedidoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminPedidoService {

    private static final int PAGE_SIZE = 9;

    private final PedidoService pedidoService;
    private final AdminWhatsappUiHelper sup;

    
    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuPedidos(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    String cabecalho =
	        "📦 *Pedidos*\n\n" +
	            "Acompanhe os pedidos por etapa:\n\n" +
	            "Escolha uma opção:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(sup.row(
	        "COMANDO|ADMIN_VER_PEDIDOS|CRIADO|0",
	        "📥 Abertos",
	        "Aguardando aceite"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_VER_PEDIDOS|EM_PREPARO|0",
	        "👨‍🍳 Em preparo",
	        "Pedidos em produção"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_VER_PEDIDOS|PRONTO|0",
	        "🛵 Em entrega",
	        "Pedidos em rota"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_VER_PEDIDOS|ENTREGUE|0",
	        "✅ Entregues",
	        "Histórico finalizado"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_MENU",
	        "⬅️ Voltar",
	        "Menu do administrador"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_pedidos_menu",
	        sup.msg().lista(
	            whatsappAdmin,
	            sup.msg().truncWord(cabecalho, 1024),
	            "Ver pedidos",
	            "Pedidos",
	            itens
	        )
	    );
	}

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
                "*Cliente:* " + sup.msg().safe(whatsappCliente) + "\n\n" +
                "*Entrega:*\n" +
                sup.msg().trunc(sup.msg().safe(endereco), 700) + "\n" +
                (StringUtils.hasText(observacoes)
                    ? ("\n*Obs:* " + sup.msg().trunc(sup.msg().safe(observacoes), 250) + "\n")
                    : "\n"
                ) +
                "\n*Itens:*\n" +
                sup.msg().trunc(sup.msg().safe(resumoItens), 700) + "\n" +
                "\n*Total:* " + sup.msg().formatarMoeda(total) + "\n\n" +
                "Deseja aceitar este pedido?";

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
            sup.btn("COMANDO|ADMIN_ACEITAR_PEDIDO|" + idPedido, "✅ Aceitar pedido"),
            sup.btn("COMANDO|ADMIN_RECUSAR_PEDIDO|" + idPedido, "❌ Recusar pedido")
        );

        return sup.msg().botoes(whatsappAdmin, sup.msg().trunc(corpo, 1024), botoes);
    }

    public List<MensagemWhatsappSaidaDTO> montarNotificacoesMudancaPedidoParaAdmins(
        Estabelecimento estabelecimento,
        Long idPedido,
        String whatsappCliente,
        String motivo,
        StatusPedido statusAtual,
        String resumoItens,
        BigDecimal total,
        List<String> adminsNormalizados
    ) {

        if (estabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }
        if (adminsNormalizados == null || adminsNormalizados.isEmpty()) {
            return List.of();
        }

        String msgNotificacao = montarTextoNotificacaoMudancaPedido(
            idPedido,
            whatsappCliente,
            motivo,
            statusAtual,
            resumoItens,
            total
        );

        return adminsNormalizados.stream()
            .filter(StringUtils::hasText)
            .map(a -> montarNotificacaoMudancaPedidoParaAdmin(a, idPedido, msgNotificacao))
            .collect(Collectors.toList());
    }

    private MensagemWhatsappSaidaDTO montarNotificacaoMudancaPedidoParaAdmin(
        String whatsappAdmin,
        Long idPedido,
        String corpoNotificacao
    ) {

        if (!StringUtils.hasText(whatsappAdmin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappAdmin é obrigatório");
        }
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
            sup.btn("COMANDO|ADMIN_PEDIDO_DETALHE|" + idPedido, "📦 Ver pedido")
        );

        return sup.msg().botoes(
            whatsappAdmin,
            sup.msg().trunc(corpoNotificacao, 1024),
            botoes
        );
    }

    private String montarTextoNotificacaoMudancaPedido(
	    Long idPedido,
	    String whatsappCliente,
	    String motivo,
	    StatusPedido statusAtual,
	    String resumoItens,
	    BigDecimal total
	) {

	    String cliente = StringUtils.hasText(whatsappCliente)
	        ? sup.msg().safe(whatsappCliente)
	        : "(não informado)";

	    String motivoFmt = StringUtils.hasText(motivo)
	        ? sup.msg().safe(motivo)
	        : "Pedido atualizado pelo cliente";

	    String statusFmt = statusAtual == null
	        ? "N/D"
	        : formatarStatusParaExibicao(statusAtual);

	    String itens = sup.msg().safe(resumoItens);
	    itens = StringUtils.hasText(itens) ? itens : "(sem itens)";

	    BigDecimal t = total == null ? BigDecimal.ZERO : total;

	    return
	        "🔔 *Mudança no pedido*\n\n" +
	            "📌 *Pedido:* #" + idPedido + "\n" +
	            "👤 *Cliente:* " + cliente + "\n" +
	            "📍 *Ação:* " + motivoFmt + "\n" +
	            "⏳ *Status:* " + statusFmt + "\n\n" +
	            "🛒 *Itens:*\n" +
	            sup.msg().trunc(itens, 1400) + "\n\n" +
	            "💰 *Total:* " + sup.msg().formatarMoeda(t);
	}
    
    public AdministradorWhatsappResultados.ResultadoAdmin listarPedidosPorStatus(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        StatusPedido status,
        Integer offset
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

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

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_pedidos_vazio",
                sup.msg().botoes(
                    whatsappAdmin,
                    sup.msg().trunc(corpo, 1024),
                    List.of(sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
                )
            );
        }

        List<MensagemInterativaItemListaWhatsappDTO> itens = pedidos.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(Pedido::getId).reversed())
            .map(p -> sup.row(
        	    "COMANDO|ADMIN_PEDIDO_DETALHE|" + p.getId(),
        	    sup.msg().trunc("#" + p.getId() + " • " + sup.msg().formatarMoeda(p.getTotal()), 24),
        	    sup.msg().trunc(montarDescricaoPedidoLista(p), 220)
        	))
            .collect(Collectors.toList());

        if (temProximaPagina) {
            int nextOffset = safeOffset + itens.size();
            itens.add(sup.row(
                "COMANDO|ADMIN_VER_PEDIDOS|" + status.name() + "|" + nextOffset,
                "➡️ Mais pedidos",
                "Ver próxima página"
            ));
        }

        itens.add(sup.row("COMANDO|ADMIN_MENU", "⬅️ Voltar", "Menu do administrador"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_lista_pedidos",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(titulo, 1024), "Pedidos", "Pedidos", itens)
        );
    }

    private String montarDescricaoPedidoLista(Pedido p) {

        String cliente = StringUtils.hasText(p.getClienteNome())
            ? sup.msg().safe(p.getClienteNome())
            : sup.msg().safe(p.getClienteTelefone());

        String extra = "";

        if (p.getTipoAtendimento() != null) {

            if (p.getTipoAtendimento() == br.com.oraped.domain.enums.TipoAtendimento.MESA) {

                extra = StringUtils.hasText(p.getNumeroMesa())
                    ? ("Mesa: " + sup.msg().safe(p.getNumeroMesa()))
                    : "Mesa";

            } else {

                if (StringUtils.hasText(p.getEnderecoEntrega()) && pareceEndereco(p.getEnderecoEntrega())) {
                    extra = sup.msg().safe(p.getEnderecoEntrega());
                } else {
                    extra = p.getTipoAtendimento().name();
                }
            }

        } else if (StringUtils.hasText(p.getEnderecoEntrega()) && pareceEndereco(p.getEnderecoEntrega())) {
            extra = sup.msg().safe(p.getEnderecoEntrega());
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

    public AdministradorWhatsappResultados.ResultadoAdmin montarDetalhePedido(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idPedido
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        Pedido p = pedidoService.buscarEntidadeComItens(estabelecimento.getId(), idPedido);

        StringBuilder sb = new StringBuilder();
        sb.append("📦 *Pedido #").append(p.getId()).append("*\n");
        sb.append("Status: *").append(formatarStatusParaExibicao(p.getStatus())).append("*\n\n");

        String cliente = StringUtils.hasText(p.getClienteTelefone())
            ? sup.msg().safe(p.getClienteTelefone())
            : "(não informado)";

        sb.append("*Cliente:* ").append(cliente).append("\n");

        if (StringUtils.hasText(p.getEnderecoEntrega())) {
            sb.append("*Entrega:* ").append(sup.msg().trunc(sup.msg().safe(p.getEnderecoEntrega()), 350)).append("\n");
        }

        if (StringUtils.hasText(p.getObservacoes())) {
            sb.append("\n*Obs:* ").append(sup.msg().trunc(sup.msg().safe(p.getObservacoes()), 250)).append("\n");
        }

        String itens = montarResumoItensDoPedido(p);
        if (StringUtils.hasText(itens)) {
            sb.append("\n*Itens:*\n").append(sup.msg().trunc(itens, 700)).append("\n");
        }

        sb.append("\n*Total:* ").append(sup.msg().formatarMoeda(p.getTotal()));

        StatusPedido statusVoltar = statusListaVoltar(p.getStatus());

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = new ArrayList<>();
        botoes.addAll(montarBotoesAcaoDoPedido(p));
        botoes.add(sup.btn("COMANDO|ADMIN_VER_PEDIDOS|" + statusVoltar.name() + "|0", "⬅️ Voltar à lista"));

        if (botoes.size() < 3) {
            botoes.add(sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"));
        }

        if (botoes.size() > 3) {
            botoes = botoes.subList(0, 3);
        }

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_pedido_detalhe",
            sup.msg().botoes(whatsappAdmin, sup.msg().trunc(sb.toString(), 1024), botoes)
        );
    }

    private List<MensagemInterativaBotaoReplyWhatsappDTO> montarBotoesAcaoDoPedido(Pedido p) {

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = new ArrayList<>();

        StatusPedido st = p == null ? null : p.getStatus();
        Long idPedido = p == null ? null : p.getId();

        if (idPedido == null || st == null) return botoes;

        if (st == StatusPedido.CRIADO) {
            botoes.add(sup.btn("COMANDO|ADMIN_CANCELAR_PEDIDO|" + idPedido, "❌ Cancelar pedido"));
            botoes.add(sup.btn("COMANDO|ADMIN_PREPARAR_PEDIDO|" + idPedido, "📦 Preparar pedido"));
        } else if (st == StatusPedido.EM_PREPARO) {
            botoes.add(sup.btn("COMANDO|ADMIN_CANCELAR_PEDIDO|" + idPedido, "❌ Cancelar pedido"));
            botoes.add(sup.btn("COMANDO|ADMIN_INICIAR_ENTREGA|" + idPedido, "🏍️ Saiu p/ entrega"));
        }

        return botoes;
    }

    public AdministradorWhatsappResultados.ResultadoAdminAcaoPedido executarAcaoPedido(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idPedido,
        AdministradorWhatsappResultados.AcaoPedidoAdmin acao
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

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

        AdministradorWhatsappResultados.ResultadoAdmin detalheAtualizado = montarDetalhePedido(estabelecimento, whatsappAdmin, idPedido);

        String telCliente = sup.msg().normalizarSomenteDigitos(pedido.getClienteTelefone());

        MensagemWhatsappSaidaDTO mensagemCliente = montarMensagemNotificacaoCliente(acao, telCliente, pedido);

        // mantém textoCliente como fallback (legado)
        String textoCliente = (mensagemCliente == null) ? montarTextoNotificacaoCliente(acao, pedido) : null;

        return new AdministradorWhatsappResultados.ResultadoAdminAcaoPedido(
            detalheAtualizado,
            telCliente,
            textoCliente,
            mensagemCliente
        );
    }

    private String montarTextoNotificacaoCliente(AdministradorWhatsappResultados.AcaoPedidoAdmin acao, Pedido pedido) {

        Long idPedido = pedido == null ? null : pedido.getId();
        if (idPedido == null) idPedido = 0L;

        if (acao == AdministradorWhatsappResultados.AcaoPedidoAdmin.ACEITAR
            || acao == AdministradorWhatsappResultados.AcaoPedidoAdmin.PREPARAR) {
            return "📦 Seu pedido *#" + idPedido + "* foi *aceito* e já está em *preparo*! 🙂";
        }

        if (acao == AdministradorWhatsappResultados.AcaoPedidoAdmin.RECUSAR) {
            return "❌ Seu pedido *#" + idPedido + "* foi *recusado* pelo estabelecimento.\n\n" +
                "Você pode tentar novamente em instantes.";
        }

        if (acao == AdministradorWhatsappResultados.AcaoPedidoAdmin.CANCELAR) {
            return "❌ Seu pedido *#" + idPedido + "* foi *cancelado* pelo estabelecimento.\n\n" +
                "Se precisar, você pode fazer um novo pedido.";
        }

        return "🏍️ Seu pedido *#" + idPedido + "* saiu para entrega!\n\n" +
            "Daqui a pouquinho ele chega. 🙂";
    }

    public String montarResumoItensDoPedido(Pedido pedido) {

        if (pedido == null || pedido.getItens() == null || pedido.getItens().isEmpty()) {
            return "(sem itens)";
        }

        StringBuilder sb = new StringBuilder();

        for (ItemPedido it : pedido.getItens()) {

            if (it == null) {
                continue;
            }

            Produto produto = it.getProduto();

            String categoria =
                produto != null
                    && produto.getCategoria() != null
                    && StringUtils.hasText(produto.getCategoria().getNome())
                        ? sup.msg().safe(produto.getCategoria().getNome())
                        : "Sem categoria";

            String nomeProduto =
                produto != null && StringUtils.hasText(produto.getNome())
                    ? sup.msg().safe(produto.getNome())
                    : "Produto";

            String tamanho = extrairDescricaoTamanho(it);

            int qtd = it.getQuantidade() == null
                ? 0
                : it.getQuantidade();

            BigDecimal subtotal = calcularSubtotalItem(it);

            sb.append("▸ ")
	            .append(categoria)
	            .append("\n");

            sb.append(nomeProduto);

            if (StringUtils.hasText(tamanho)) {
                sb.append(" • ").append(tamanho);
            }

            sb.append("\n");

            sb.append("Qtd: ")
                .append(qtd)
                .append(" • ")
                .append(sup.msg().formatarMoeda(subtotal))
                .append("\n");

            List<String> complementos = extrairComplementos(it);

            for (String comp : complementos) {
                sb.append("↳ ")
                    .append(comp)
                    .append("\n");
            }

            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private static StatusPedido statusListaVoltar(StatusPedido st) {

        if (st == null) return StatusPedido.CRIADO;

        if (st == StatusPedido.PRONTO) {
            return StatusPedido.EM_PREPARO;
        }

        return st;
    }

    public static String formatarStatusParaExibicao(StatusPedido st) {

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
    
    
    private MensagemWhatsappSaidaDTO montarMensagemNotificacaoCliente(
	    AdministradorWhatsappResultados.AcaoPedidoAdmin acao,
	    String whatsappCliente,
	    Pedido pedido
	) {

	    if (!StringUtils.hasText(whatsappCliente) || pedido == null) {
	        return null;
	    }

	    Long idPedido = pedido.getId();
	    if (idPedido == null) {
	        return null;
	    }

	    if (acao == AdministradorWhatsappResultados.AcaoPedidoAdmin.INICIAR_ENTREGA) {

	        String corpo =
	            "🏍️ Seu pedido *#" + idPedido + "* saiu para entrega!\n\n" +
	            "Quando receber, toque no botão abaixo para confirmar. 🙂";

	        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
	            sup.btn("COMANDO|REVISAO_CONFIRMAR_ENTREGA|" + idPedido, "✅ Confirmar entrega")
	        );

	        return sup.msg().botoes(whatsappCliente, sup.msg().trunc(corpo, 1024), botoes);
	    }

	    return null;
	}
    
    
    private String extrairDescricaoTamanho(ItemPedido item) {

        if (item == null || !StringUtils.hasText(item.getNomeTamanho())) {
            return "";
        }

        return sup.msg().safe(item.getNomeTamanho());
    }

    private List<String> extrairComplementos(ItemPedido item) {

        if (item == null
            || item.getOpcionais() == null
            || item.getOpcionais().isEmpty()) {

            return List.of();
        }

        return item.getOpcionais().stream()
            .filter(Objects::nonNull)
            .map(opcional -> {

                String nome = StringUtils.hasText(opcional.getNome())
                    ? sup.msg().safe(opcional.getNome())
                    : "Opcional";

                Integer quantidade = opcional.getQuantidade() == null
                    ? 0
                    : opcional.getQuantidade();

                BigDecimal subtotalOpcional = opcional.getPrecoUnitario() == null
                    ? BigDecimal.ZERO
                    : opcional.getPrecoUnitario()
                        .multiply(BigDecimal.valueOf(Math.max(quantidade, 0)));

                StringBuilder sb = new StringBuilder();

                sb.append(quantidade)
                    .append("x ")
                    .append(nome);

                if (subtotalOpcional.compareTo(BigDecimal.ZERO) > 0) {
                    sb.append(" • ")
                        .append(sup.msg().formatarMoeda(subtotalOpcional));
                }

                return sup.msg().trunc(sb.toString(), 120);
            })
            .collect(Collectors.toList());
    }

    private BigDecimal calcularSubtotalItem(ItemPedido item) {

        if (item == null) {
            return BigDecimal.ZERO;
        }

        // Prioriza o subtotal persistido no snapshot do pedido.
        if (item.getSubtotalItem() != null) {
            return item.getSubtotalItem();
        }

        BigDecimal precoUnitario = item.getPrecoUnitarioProduto() == null
            ? BigDecimal.ZERO
            : item.getPrecoUnitarioProduto();

        Integer quantidade = item.getQuantidade() == null
            ? 0
            : item.getQuantidade();

        BigDecimal subtotal = precoUnitario.multiply(
            BigDecimal.valueOf(Math.max(quantidade, 0))
        );

        if (item.getOpcionais() != null) {

            for (ItemPedidoOpcional opcional : item.getOpcionais()) {

                if (opcional == null || opcional.getPrecoUnitario() == null) {
                    continue;
                }

                Integer qtdOpcional = opcional.getQuantidade() == null
                    ? 0
                    : opcional.getQuantidade();

                subtotal = subtotal.add(
                    opcional.getPrecoUnitario()
                        .multiply(BigDecimal.valueOf(Math.max(qtdOpcional, 0)))
                );
            }
        }

        return subtotal;
    }
    
}