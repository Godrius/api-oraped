// src/main/java/br/com/oraped/service/whatsapp/orquestrador/OrquestradorMenusClienteService.java
package br.com.oraped.service.whatsapp.orquestrador;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.CategoriaProduto;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.Produto;
import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdministradorWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorMenusClienteService {

    private final AdministradorWhatsappService administradorWhatsappService;
    private final WhatsappMensagemFactory msg;

    private final OrquestradorExtracaoEstabelecimentoService extracaoService;
    private final OrquestradorMensagemHelperService helperService;
    private final OrquestradorCarrinhoService carrinhoService;
    private final SessaoAtendimentoWhatsappService sessaoService;
    
    public MensagemWhatsappSaidaDTO montarMenuPrincipal(Estabelecimento estabelecimento, String whatsappCliente) {

        if (administradorWhatsappService.isAdminAtivo(estabelecimento, whatsappCliente)) {
            return administradorWhatsappService.montarMenuAdmin(estabelecimento, whatsappCliente).mensagem;
        }

        String cabecalho =
            "Olá! 👋\n" +
                "Você está falando com *" + msg.safe(estabelecimento.getNome()) + "*.\n\n" +
                "Escolha uma opção:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = List.of(
            helperService.row("COMANDO|FAZER_PEDIDO", "🛎️ Fazer um pedido", "Ver categorias e escolher produtos"),
            helperService.row("COMANDO|ULTIMO_PEDIDO", "📄 Meu último pedido", "Status / dúvidas do pedido mais recente")
        );

        return msg.lista(whatsappCliente, cabecalho, "Ver opções", "Opções", itens);
    }

    public MensagemWhatsappSaidaDTO montarMenuPrincipalSemSaudacao(Estabelecimento estabelecimento, String whatsappCliente) {

        if (administradorWhatsappService.isAdminAtivo(estabelecimento, whatsappCliente)) {
            return administradorWhatsappService.montarMenuAdmin(estabelecimento, whatsappCliente).mensagem;
        }

        String cabecalho = "Escolha uma opção:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = List.of(
            helperService.row("COMANDO|FAZER_PEDIDO", "🛎️ Fazer um pedido", "Ver categorias e escolher produtos"),
            helperService.row("COMANDO|ULTIMO_PEDIDO", "📄 Meu último pedido", "Status / dúvidas do pedido mais recente")
        );

        return msg.lista(whatsappCliente, cabecalho, "Ver opções", "Opções", itens);
    }

    public MensagemWhatsappSaidaDTO montarListaCategorias(Estabelecimento estabelecimento, String whatsappCliente) {

        List<CategoriaProduto> categorias = extracaoService.extrairCategoriasDoEstabelecimento(estabelecimento);

        if (categorias.isEmpty()) {
            return msg.texto(whatsappCliente, "No momento não encontrei categorias disponíveis para este estabelecimento.");
        }

        String cabecalho = "Escolha uma categoria:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = categorias.stream()
            .sorted(Comparator.comparing(c -> msg.safe(c.getNome()), String.CASE_INSENSITIVE_ORDER))
            .map(c -> {
                Integer qm = c.getQuantidadeMultipla() == null ? 1 : c.getQuantidadeMultipla();
                return helperService.row(
                    "COMANDO|LISTA_PRODUTOS|" + c.getId() + "|" + qm,
                    msg.safe(c.getNome()),
                    "Clique para ver produtos"
                );
            })
            .toList();

        return msg.lista(whatsappCliente, cabecalho, "Categorias", "Categorias", itens);
    }

    public MensagemWhatsappSaidaDTO montarListaProdutosPorCategoriaPaginada(
        Estabelecimento estabelecimento,
        String whatsappCliente,
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
            .sorted(Comparator.comparing(p -> msg.safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
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
                    .id("COMANDO|LISTAR_QUANTIDADES|" + idCategoria + "|" + quantidadeMultipla + "|" + p.getId())
                    .title(title)
                    .description(description)
                    .build();
            })
            .toList();

        List<MensagemInterativaItemListaWhatsappDTO> itensFinal = new ArrayList<>(itens);

        if (endExclusive < total) {
            int nextOffset = endExclusive;
            itensFinal.add(helperService.row(
                "COMANDO|LISTA_PRODUTOS_PAG|" + idCategoria + "|" + quantidadeMultipla + "|" + nextOffset,
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

    public MensagemWhatsappSaidaDTO montarListaQuantidades(
        Estabelecimento estabelecimento,
        String whatsappCliente,
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
            BigDecimal preco = calcularPrecoPorQuantidade(produto, quantidade);

            itens.add(helperService.row(
                "COMANDO|ADICIONAR_PRODUTO|" + idProduto + "|" + quantidade,
                quantidade + " unidades",
                "Valor total: " + msg.formatarMoeda(preco)
            ));
        }

        itens.add(helperService.row(
            "COMANDO|SOLICITAR_QUANTIDADE|" + idProduto,
            "Outra quantidade",
            "Informar manualmente"
        ));

        String cabecalho =
            "Quantidades - " + msg.safe(produto.getNome()) + "\n" +
                "Escolha uma opção:";

        return msg.lista(whatsappCliente, cabecalho, "Quantidades", "Quantidades", itens);
    }

    public MensagemWhatsappSaidaDTO montarVisualizacaoCarrinho(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        Map<Long, Integer> qtdPorProduto = carrinhoService.montarCarrinhoAtual(idSessao);

        if (qtdPorProduto.isEmpty()) {

            String corpo = "Seu carrinho está vazio 🛒\n\nQuer incluir algum item?";

            return msg.botoes(
                whatsappCliente,
                msg.trunc(corpo, 1024),
                List.of(
                    helperService.btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Incluir outro item"),
                    helperService.btn("COMANDO|FAZER_PEDIDO", "Fazer um pedido")
                )
            );
        }

        BigDecimal total = BigDecimal.ZERO;

        StringBuilder sb = new StringBuilder();
        sb.append("*Seu carrinho* 🛒\n\n");

        for (var entry : qtdPorProduto.entrySet()) {

            Long idProduto = entry.getKey();
            int qtd = entry.getValue();

            Produto p = extracaoService.extrairProduto(estabelecimento, idProduto);

            if (p == null) {
                sb.append("- Produto #").append(idProduto).append(" x").append(qtd).append("\n");
                continue;
            }

            BigDecimal subtotal = calcularPrecoPorQuantidade(p, qtd);
            total = total.add(subtotal);

            String nome = msg.safe(p.getNome());
            String desc = msg.safe(p.getDescricao());

            sb.append("- ").append(nome);

            if (StringUtils.hasText(desc)) {
                sb.append(" (").append(msg.trunc(desc, 24)).append(")");
            }

            sb.append(" x").append(qtd)
                .append(" = ").append(msg.formatarMoeda(subtotal))
                .append("\n");
        }

        sb.append("\n*Total:* ").append(msg.formatarMoeda(total)).append("\n");

        return msg.botoes(
            whatsappCliente,
            msg.trunc(sb.toString(), 1024),
            List.of(
                helperService.btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Incluir outro item"),
                helperService.btn("COMANDO|LIMPAR_CARRINHO", "🗑️ Limpar carrinho"),
                helperService.btn("COMANDO|INFORMAR_ENDERECO", "🏍️ Ir para entrega")
            )
        );
    }

    public MensagemWhatsappSaidaDTO montarCarrinhoLimpo(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        String corpo =
            "Carrinho limpo ✅🛒\n\n" +
                "O que você deseja fazer agora?";

        return msg.botoes(
            whatsappCliente,
            msg.trunc(corpo, 1024),
            List.of(
                helperService.btn("COMANDO|INCLUIR_OUTRO_ITEM", "Incluir outro item"),
                helperService.btn("COMANDO|VISUALIZAR_CARRINHO", "Visualizar carrinho"),
                helperService.btn("COMANDO|INFORMAR_ENDERECO", "Concluir pedido")
            )
        );
    }

    public MensagemWhatsappSaidaDTO montarEscolhaFormaPagamento(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao
	) {

	    Map<Long, Integer> carrinho = carrinhoService.montarCarrinhoAtual(idSessao);

	    BigDecimal subtotalItens = BigDecimal.ZERO;

	    if (carrinho != null && !carrinho.isEmpty()) {

	        for (var entry : carrinho.entrySet()) {

	            Long idProduto = entry.getKey();
	            int qtd = entry.getValue() == null ? 0 : entry.getValue();

	            if (qtd <= 0) {
	                continue;
	            }

	            Produto p = extracaoService.extrairProduto(estabelecimento, idProduto);

	            if (p == null) {
	                continue;
	            }

	            subtotalItens = subtotalItens.add(calcularPrecoPorQuantidade(p, qtd));
	        }
	    }

	    // taxa de entrega: sessão > taxa padrão da loja > 0
	    BigDecimal taxaEntrega = BigDecimal.ZERO;

	    if (idSessao != null) {
	        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

	        if (s.getTaxaEntregaCalculada() != null) {
	            taxaEntrega = s.getTaxaEntregaCalculada();
	        } else if (estabelecimento != null && estabelecimento.getTaxaEntregaPadrao() != null) {
	            taxaEntrega = estabelecimento.getTaxaEntregaPadrao();
	        }
	    } else if (estabelecimento != null && estabelecimento.getTaxaEntregaPadrao() != null) {
	        taxaEntrega = estabelecimento.getTaxaEntregaPadrao();
	    }

	    if (taxaEntrega == null) {
	        taxaEntrega = BigDecimal.ZERO;
	    }

	    BigDecimal totalGeral = subtotalItens.add(taxaEntrega);

	    String corpo =
	        "💰 *Resumo do pedido*\n\n" +
	            "*Subtotal:* " + msg.formatarMoeda(subtotalItens) + "\n" +
	            "*Taxa de entrega:* " + msg.formatarMoeda(taxaEntrega) + "\n" +
	            "*Total:* " + msg.formatarMoeda(totalGeral) + "\n\n" +
	            "Como deseja pagar?\n\n" +
	            "Escolha uma opção:";

	    List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
	        helperService.btn("COMANDO|SELECIONAR_PAGAMENTO|DINHEIRO", "💵 Dinheiro"),
	        helperService.btn("COMANDO|SELECIONAR_PAGAMENTO|CREDITO", "💳 Cartão (Crédito)"),
	        helperService.btn("COMANDO|SELECIONAR_PAGAMENTO|DEBITO_PIX", "🏧 Débito/PIX")
	    );

	    return msg.botoes(whatsappCliente, msg.trunc(corpo, 1024), botoes);
	}

    public MensagemWhatsappSaidaDTO montarPerguntaTrocoSimNao(String whatsappCliente) {

        String corpo =
            "Você precisa de troco?\n\n" +
                "Escolha uma opção:";

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
            helperService.btn("COMANDO|TROCO|NAO", "✅ Não"),
            helperService.btn("COMANDO|TROCO|SIM", "💵 Sim, preciso")
        );

        return msg.botoes(whatsappCliente, msg.trunc(corpo, 1024), botoes);
    }

    public MensagemWhatsappSaidaDTO montarSolicitacaoTrocoValor(String whatsappCliente) {

        String corpo =
            "Troco para quanto?\n\n" +
                "Exemplos:\n" +
                "- 50\n" +
                "- 100,00\n" +
                "- R$ 20";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    public MensagemWhatsappSaidaDTO montarConfirmacaoFinalAntesDeEnviar(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    SessaoAtendimentoWhatsapp s
	) {

	    String pagamento = formatarPagamentoParaTexto(s);

	    Map<Long, Integer> carrinho = carrinhoService.montarCarrinhoAtual(s.getId());

	    String itensTexto = "(sem itens)";
	    BigDecimal subtotalItens = BigDecimal.ZERO;

	    if (carrinho != null && !carrinho.isEmpty()) {

	        StringBuilder sb = new StringBuilder();

	        for (var e : carrinho.entrySet()) {

	            Long idProduto = e.getKey();
	            int qtd = e.getValue() == null ? 0 : e.getValue();

	            Produto p = extracaoService.extrairProduto(estabelecimento, idProduto);

	            String nome = (p == null ? ("Produto #" + idProduto) : msg.safe(p.getNome()));

	            BigDecimal subtotalItem = (p == null ? BigDecimal.ZERO : calcularPrecoPorQuantidade(p, qtd));
	            subtotalItens = subtotalItens.add(subtotalItem);

	            sb.append("- ")
	                .append(nome)
	                .append(" x").append(qtd)
	                .append(" = ").append(msg.formatarMoeda(subtotalItem))
	                .append("\n");
	        }

	        itensTexto = sb.toString().trim();
	    }

	    BigDecimal taxaEntrega = s.getTaxaEntregaCalculada();

	    if (taxaEntrega == null) {
	        taxaEntrega = estabelecimento == null ? null : estabelecimento.getTaxaEntregaPadrao();
	    }

	    if (taxaEntrega == null) {
	        taxaEntrega = BigDecimal.ZERO;
	    }

	    BigDecimal totalGeral = subtotalItens.add(taxaEntrega);

	    String endereco = msg.trunc(msg.safe(s.getEnderecoEntrega()), 650);

	    String obs = msg.safe(s.getObservacoesEntrega());
	    String obsFmt = StringUtils.hasText(obs)
	        ? ("\n*Obs:* " + msg.trunc(obs, 250) + "\n")
	        : "\n";

	    String corpo =
	        "🔎 *Revise seu pedido antes de enviar*\n\n" +
	            "🛒 *Itens:*\n" +
	            msg.trunc(itensTexto, 650) + "\n\n" +
	            "*Subtotal:* " + msg.formatarMoeda(subtotalItens) + "\n" +
	            "*Taxa de entrega:* " + msg.formatarMoeda(taxaEntrega) + "\n" +
	            "*Total:* " + msg.formatarMoeda(totalGeral) + "\n\n" +
	            "📍 *Entrega:*\n" +
	            endereco + "\n" +
	            obsFmt +
	            "💳 *Pagamento:* " + pagamento + "\n\n" +
	            "Se estiver tudo certo, confirme o envio ✅";

	    List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
	        helperService.btn("COMANDO|ENVIAR_PEDIDO", "✅ Confirmar e enviar"),
	        helperService.btn("COMANDO|VISUALIZAR_CARRINHO", "✏️ Ajustar carrinho"),
	        helperService.btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Adicionar itens")
	    );

	    return msg.botoes(whatsappCliente, msg.trunc(corpo, 1024), botoes);
	}

    public String formatarPagamentoParaTexto(SessaoAtendimentoWhatsapp s) {

        FormaPagamentoPedido fp = s.getFormaPagamento();
        if (fp == null) return "Não informado";

        if (fp == FormaPagamentoPedido.CREDITO) return "Cartão (Crédito)";
        if (fp == FormaPagamentoPedido.DEBITO_PIX) return "Débito/PIX";

        if (Boolean.TRUE.equals(s.getPrecisaTroco())) {
            if (s.getTrocoPara() != null) {
                return "Dinheiro (troco para " + msg.formatarMoeda(s.getTrocoPara()) + ")";
            }
            return "Dinheiro (com troco)";
        }

        if (Boolean.FALSE.equals(s.getPrecisaTroco())) {
            return "Dinheiro (sem troco)";
        }

        return "Dinheiro";
    }

    public MensagemWhatsappSaidaDTO montarSugestaoEnderecoAnterior(
	    String whatsappCliente,
	    String enderecoAnterior,
	    BigDecimal taxaEntrega
	) {

	    BigDecimal taxa = (taxaEntrega == null) ? BigDecimal.ZERO : taxaEntrega;

	    String corpo =
	        "Encontrei um endereço usado no seu último pedido:\n\n" +
	            msg.trunc(enderecoAnterior, 900) + "\n\n" +
	            "🚚 Taxa de entrega para este pedido: " + msg.formatarMoeda(taxa) + "\n\n" +
	            "Deseja usar esse mesmo?";

	    return msg.botoes(
	        whatsappCliente,
	        msg.trunc(corpo, 1024),
	        List.of(
	            helperService.btn("COMANDO|FAZER_PEDIDO_COM_ENDERECO_ANTERIOR", "✅ Usar esse mesmo"),
	            helperService.btn("COMANDO|INFORMAR_OUTRO_ENDERECO", "✏️ Alterar endereço")
	        )
	    );
	}

    public MensagemWhatsappSaidaDTO montarSolicitacaoEnderecoEntrega(String whatsappCliente) {

        String corpo =
            "Perfeito! ✅ Agora me informe o *endereço de entrega*.\n\n" +
                "Inclua também *observações úteis pro entregador*, como:\n" +
                "- ponto de referência\n" +
                "- bloco/apto\n" +
                "- interfone\n" +
                "- portaria / instruções de acesso\n\n" +
                "Exemplo:\n" +
                "Rua X, 123 - Apto 45, Bairro Y. Obs: interfone 45, portaria 24h.";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    public MensagemWhatsappSaidaDTO montarConfirmacaoPedidoEnviado(
        String whatsappCliente,
        Long idPedido,
        SessaoAtendimentoWhatsapp sessao,
        String resumoItens,
        BigDecimal total
    ) {

        String endereco = sessao == null ? "" : msg.safe(sessao.getEnderecoEntrega());
        String pagamento = formatarPagamentoParaTexto(sessao);

        String itens = msg.safe(resumoItens);
        itens = StringUtils.hasText(itens) ? itens : "(sem itens)";

        String corpo =
            "✅ *Pedido enviado com sucesso!*\n\n" +
                "📌 *Pedido:* #" + idPedido + "\n" +
                "⏳ *Status:* aguardando confirmação do estabelecimento\n\n" +
                "🛒 *Itens:*\n" +
                msg.trunc(itens, 1400) + "\n\n" +
                "💰 *Total:* " + msg.formatarMoeda(total) + "\n\n" +
                "📍 *Entrega:*\n" +
                msg.trunc(endereco, 700) + "\n\n" +
                "💳 *Pagamento:* " + pagamento + "\n\n" +
                "Você vai receber atualizações quando:\n" +
                "- o pedido for *aceito*\n" +
                "- o pedido *sair para entrega*\n\n" +
                "Se precisar, envie *MENU* para voltar ao início.";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    public BigDecimal calcularPrecoPorQuantidade(Produto produto, int quantidade) {
        BigDecimal precoUnit = produto == null || produto.getPreco() == null ? BigDecimal.ZERO : produto.getPreco();
        return precoUnit.multiply(BigDecimal.valueOf(quantidade));
    }
    
    
    //============================================
    //ENDEREÇO DE ENTREGA DO CLIENTE
    //============================================
    public MensagemWhatsappSaidaDTO montarSolicitacaoCepEntrega(String whatsappCliente) {

        String corpo =
            "Perfeito! ✅\n\n" +
                "Agora me informe o *CEP de entrega*.\n\n" +
                "Exemplos:\n" +
                "24350-000\n" +
                "ou\n" +
                "24350000";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    public MensagemWhatsappSaidaDTO montarEnderecoEncontradoSolicitarComplemento(
        String whatsappCliente,
        String enderecoBase
    ) {

        String corpo =
            "Encontrei este endereço pelo CEP:\n\n" +
                "*" + msg.trunc(enderecoBase, 500) + "*\n\n" +
                "Agora me informe o *complemento* (número, apto/bloco, ponto de referência, etc.).\n\n" +
                "Você pode incluir observações assim:\n" +
                "- Obs: interfone 45\n" +
                "- Obs: portaria 24h";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    public MensagemWhatsappSaidaDTO montarSolicitacaoEnderecoCompletoFallback(String whatsappCliente) {

        String corpo =
            "Não consegui localizar o endereço pelo CEP 😕\n\n" +
                "Por favor, me envie o *endereço completo* (rua, número, bairro) e, se quiser, observações pro entregador.\n\n" +
                "Exemplo:\n" +
                "Rua X, 123 - Apto 45, Bairro Y. Obs: interfone 45, portaria 24h.";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }
}