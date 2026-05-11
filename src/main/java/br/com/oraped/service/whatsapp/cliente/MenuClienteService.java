package br.com.oraped.service.whatsapp.cliente;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.carrinho.Carrinho;
import br.com.oraped.domain.carrinho.ComplementoItemCarrinhoEmMontagem;
import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.domain.produto.complemento.Complemento;
import br.com.oraped.domain.produto.complemento.GrupoComplemento;
import br.com.oraped.domain.produto.tamanho.GradeTamanho;
import br.com.oraped.domain.produto.tamanho.OpcaoTamanhoProduto;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.repository.produto.complemento.ComplementoRepository;
import br.com.oraped.repository.produto.complemento.GrupoComplementoRepository;
import br.com.oraped.repository.produto.tamanho.OpcaoTamanhoProdutoRepository;
import br.com.oraped.service.produto.tamanho.GradeTamanhoService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.MenuAdminService;
import br.com.oraped.service.whatsapp.administrador.ValidadorAdminService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorExtracaoEstabelecimentoService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorMensagemHelperService;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.sessao.SessaoItemCarrinhoEmMontagemService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MenuClienteService {

	private final ValidadorAdminService validadorAdminService;
    private final MenuAdminService menuAdminService;
    
    private final WhatsappMensagemFactory msg;

    private final OrquestradorExtracaoEstabelecimentoService extracaoService;
    private final OrquestradorMensagemHelperService helperService;
    private final CarrinhoClienteService carrinhoService;
    private final SessaoAtendimentoWhatsappService sessaoService;
    private final GradeTamanhoService gradeTamanhoService;
    
    private final GrupoComplementoRepository grupoComplementoRepository;
    private final ComplementoRepository complementoRepository;
    private final SessaoItemCarrinhoEmMontagemService itemEmMontagemService;
    
    private final OpcaoTamanhoProdutoRepository opcaoTamanhoProdutoRepository;
    
    public MensagemWhatsappSaidaDTO montarMenuPrincipal(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    String nomeClienteWhatsapp
	) {

	    if (validadorAdminService.isAdminAtivo(estabelecimento, whatsappCliente)) {
	        return menuAdminService.montarMenuAdmin(estabelecimento, whatsappCliente).mensagem;
	    }

	    String nome = msg.safe(nomeClienteWhatsapp);

	    // Usa apenas o primeiro nome para manter naturalidade na saudação.
	    String saudacao;
	    if (StringUtils.hasText(nome)) {
	        String[] partes = nome.split("\\s+");
	        String primeiroNome = partes.length > 0 ? msg.safe(partes[0]) : "";

	        if (StringUtils.hasText(primeiroNome)) {
	            saudacao = "Olá, *" + msg.trunc(primeiroNome, 40) + "*! 👋";
	        } else {
	            saudacao = "Olá! 👋";
	        }
	    } else {
	        saudacao = "Olá! 👋";
	    }

	    String cabecalho =
	        saudacao + "\n\n" +
	            "Você está falando com *" + msg.safe(estabelecimento.getNome()) + "*.\n\n" +
	            "Vou te ajudar com seu pedido 😊\n" +
	            "Escolha uma opção abaixo:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = List.of(
	        helperService.row("COMANDO|FAZER_PEDIDO", "🛎️ Fazer um pedido", "Ver categorias e escolher produtos"),
	        helperService.row("COMANDO|ULTIMO_PEDIDO", "📄 Meu último pedido", "Status / dúvidas do pedido mais recente")
	    );

	    return msg.lista(whatsappCliente, cabecalho, "Ver opções", "Opções", itens);
	}

    public MensagemWhatsappSaidaDTO montarMenuPrincipalSemSaudacao(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao
	) {

	    if (validadorAdminService.isAdminAtivo(estabelecimento, whatsappCliente)) {
	        return menuAdminService.montarMenuAdmin(estabelecimento, whatsappCliente).mensagem;
	    }

	    String cabecalho = "Escolha uma opção:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(helperService.row(
	        "COMANDO|FAZER_PEDIDO",
	        "🛎️ Fazer um pedido",
	        "Ver categorias e escolher produtos"
	    ));

	    itens.add(helperService.row(
	        "COMANDO|ULTIMO_PEDIDO",
	        "📄 Meu último pedido",
	        "Status / dúvidas do pedido mais recente"
	    ));

	    if (idSessao != null) {
	        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

	        /*
	         * Quando a loja foi acessada pelo marketplace, oferecemos retornos distintos:
	         * trocar loja mantém a categoria/localização; trocar localização reinicia o discovery.
	         */
	        if (sessao.getIdMarketplace() != null) {
	            itens.add(helperService.row(
	                "COMANDO|TROCAR_ESTABELECIMENTO_MARKETPLACE",
	                "🔄 Trocar loja",
	                "Ver outras lojas desta categoria"
	            ));

	            itens.add(helperService.row(
	                "COMANDO|TROCAR_CATEGORIA_MARKETPLACE",
	                "🧭 Trocar categoria",
	                "Ver outros tipos de loja"
	            ));

	            itens.add(helperService.row(
	                "COMANDO|TROCAR_LOCALIZACAO_MARKETPLACE",
	                "📍 Trocar localização",
	                "Buscar lojas em outro local"
	            ));
	        }
	    }

	    return msg.lista(whatsappCliente, cabecalho, "Ver opções", "Opções", itens);
	}

    public MensagemWhatsappSaidaDTO montarListaCategoriasPaginada(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Integer offset
	) {

	    List<CategoriaProduto> categorias = extracaoService.extrairCategoriasDoEstabelecimento(estabelecimento);

	    if (categorias.isEmpty()) {
	        return msg.texto(whatsappCliente, "No momento não encontrei categorias disponíveis para este estabelecimento.");
	    }

	    int safeOffset = (offset == null || offset < 0) ? 0 : offset;
	    int pageSizeCategorias = 9;

	    List<CategoriaProduto> ordenadas = categorias.stream()
	        .filter(Objects::nonNull)
	        .sorted(Comparator.comparing(c -> msg.safe(c.getNome()), String.CASE_INSENSITIVE_ORDER))
	        .toList();

	    int total = ordenadas.size();

	    // Se o offset vier inválido ou ultrapassar o total, volta para a primeira página.
	    if (safeOffset >= total) {
	        safeOffset = 0;
	    }

	    int endExclusive = Math.min(safeOffset + pageSizeCategorias, total);
	    List<CategoriaProduto> page = ordenadas.subList(safeOffset, endExclusive);

	    int paginaAtual = (safeOffset / pageSizeCategorias) + 1;
	    int paginasTotal = (int) Math.ceil(total / (double) pageSizeCategorias);

	    String cabecalho = "Escolha uma categoria:";

	    if (paginasTotal > 1) {
	        cabecalho += "\nPágina " + paginaAtual + " de " + paginasTotal;
	    }

	    List<MensagemInterativaItemListaWhatsappDTO> itens = page.stream()
	        .map(c -> {
	            Integer qm = c.getQuantidadeMultipla() == null ? 1 : c.getQuantidadeMultipla();

	            return helperService.row(
	                "COMANDO|LISTA_PRODUTOS|" + c.getId() + "|" + qm,
	                msg.safe(c.getNome()),
	                ""
	            );
	        })
	        .toList();

	    List<MensagemInterativaItemListaWhatsappDTO> itensFinal = new ArrayList<>(itens);

	    // Reserva a última posição para navegação, mantendo o payload sempre válido para a Meta.
	    if (endExclusive < total) {
	        int nextOffset = endExclusive;

	        itensFinal.add(helperService.row(
	            "COMANDO|LISTA_CATEGORIAS_PAG|" + nextOffset,
	            "➡️ Mais categorias",
	            "Ver próxima página"
	        ));
	    }

	    return msg.lista(
	        whatsappCliente,
	        msg.truncWord(cabecalho, 1024),
	        msg.truncWord("Categorias", 20),
	        msg.truncWord("Categorias", 24),
	        itensFinal
	    );
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

	    //lista de produtos da categoria selecionada
	    boolean categoriaUsaTamanhos = gradeTamanhoService.categoriaPossuiGradeAtiva(idCategoria);
	    
	    List<Produto> ordenados = produtos.stream()
    	    .filter(Objects::nonNull)

    	    // Produtos sem preço configurado não devem aparecer para o cliente.
    	    .filter(p -> {
    	        if (categoriaUsaTamanhos) {
    	            // Em categorias com tamanhos, o produto só aparece se tiver ao menos um tamanho ativo com preço válido.
    	            return opcaoTamanhoProdutoRepository.existsByProdutoIdAndAtivoTrueAndPrecoGreaterThan(
    	                p.getId(),
    	                BigDecimal.ZERO
    	            );
    	        }

    	        return p.getPreco() != null
    	            && p.getPreco().compareTo(BigDecimal.ZERO) > 0;
    	    })

    	    .sorted(Comparator.comparing(p -> msg.safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
    	    .toList();

	    String nomeCategoria = extracaoService.extrairNomeCategoria(estabelecimento, idCategoria);
	    String tituloCategoria = (nomeCategoria == null ? ("Categoria #" + idCategoria) : nomeCategoria);

	    int total = ordenados.size();
	    if (safeOffset >= total) {
	        safeOffset = 0;
	    }

	    int endExclusive = Math.min(safeOffset + pageSizeProdutos, total);
	    List<Produto> page = ordenados.subList(safeOffset, endExclusive);

	    int paginaAtual = (safeOffset / pageSizeProdutos) + 1;
	    int paginasTotal = (int) Math.ceil(total / (double) pageSizeProdutos);

	    String cabecalho = tituloCategoria + ":";

	    if (paginasTotal > 1) {
	        cabecalho += "\nPágina " + paginaAtual + " de " + paginasTotal;
	    }

	    
	    List<MensagemInterativaItemListaWhatsappDTO> itens = page.stream()
	        .map(p -> {
	            String nome = msg.safe(p.getNome());

	            String descricao = msg.safe(p.getDescricao());

	            String sufixo = categoriaUsaTamanhos
	                ? ""
	                : " ┃ 💰 " + msg.formatarMoeda(p.getPreco()).replace("R$", "").trim();

	            int limiteTotal = 72;
	            int limiteDescricao = Math.max(limiteTotal - sufixo.length(), 0);

	            String descricaoFinal = "";

	            if (StringUtils.hasText(descricao)) {
	                String texto = descricao.trim();

	                if (texto.length() > limiteDescricao) {
	                    descricaoFinal = limiteDescricao > 3
	                        ? texto.substring(0, limiteDescricao - 3) + "..."
	                        : texto.substring(0, limiteDescricao);
	                } else {
	                    descricaoFinal = texto;
	                }
	            }

	            String description = StringUtils.hasText(descricaoFinal)
	                ? descricaoFinal + sufixo
	                : sufixo.trim();
	            
	            return MensagemInterativaItemListaWhatsappDTO.builder()
	                .id("COMANDO|SELECIONAR_PRODUTO|" + idCategoria + "|" + quantidadeMultipla + "|" + p.getId())
	                .title(msg.trunc(nome, 24))
	                .description(description)
	                .build();
	        })
	        .toList();

	    List<MensagemInterativaItemListaWhatsappDTO> itensFinal = new ArrayList<>(itens);

	    if (endExclusive < total) {
	        int nextOffset = endExclusive;
	        itensFinal.add(helperService.row(
	            "COMANDO|LISTA_PRODUTOS_PAG|" + idCategoria + "|" + quantidadeMultipla + "|" + nextOffset,
	            "➡️ Mais opções",
	            "Ver próxima página"
	        ));
	    }

	    return msg.lista(
	        whatsappCliente,
	        msg.truncWord(cabecalho, 1024),
	        msg.truncWord("Opções", 20),
	        msg.truncWord("Opções", 24),
	        itensFinal
	    );
	}

    public MensagemWhatsappSaidaDTO montarSelecaoProduto(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    Long idCategoria,
	    Integer quantidadeMultipla,
	    Long idProduto
	) {

	    Produto produto = extracaoService.extrairProduto(estabelecimento, idProduto);

	    if (produto == null) {
	        return msg.texto(whatsappCliente, "Produto não encontrado.");
	    }

		 // =========================================================
		 // CLIENTE — Quantidades após seleção direta do produto
		 // =========================================================
	
		 // Produtos sem complementos seguem direto para quantidade, sem criar montagem parcial.
		 return montarListaQuantidades(
		     estabelecimento,
		     whatsappCliente,
		     idSessao,
		     idCategoria,
		     quantidadeMultipla,
		     idProduto
		 );
	}
    
    
    public MensagemWhatsappSaidaDTO montarImagemProdutoAntesDasQuantidades(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idProduto
	) {

	    Produto produto = extracaoService.extrairProduto(estabelecimento, idProduto);

	    if (produto == null) {
	        return null;
	    }

	    if (!StringUtils.hasText(produto.getUrlFoto())) {
	        return null;
	    }

	    // A mensagem da imagem fica enxuta para evitar repetição com o menu de quantidades.
	    return msg.imagem(
	        whatsappCliente,
	        produto.getUrlFoto(),
	        null,
	        null
	    );
	}

        
    public Produto getProduto(Estabelecimento estabelecimento, Long idProduto) {
        return extracaoService.extrairProduto(estabelecimento, idProduto);
    }

    public MensagemWhatsappSaidaDTO montarListaQuantidades(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    Long idCategoria,
	    Integer quantidadeMultipla,
	    Long idProduto
	) {

	    Produto produto = extracaoService.extrairProduto(estabelecimento, idProduto);

	    if (produto == null) {
	        return msg.texto(whatsappCliente, "Produto não encontrado.");
	    }

	    SessaoAtendimentoWhatsapp sessao = idSessao == null
    	    ? null
    	    : sessaoService.buscarPorId(idSessao);
	    
	    int qm = (quantidadeMultipla == null || quantidadeMultipla < 1) ? 1 : quantidadeMultipla;

	    // =========================================================
	    // CLIENTE — Complementos escolhidos na montagem atual
	    // =========================================================

	    // Os complementos pertencem à sessão em montagem e impactam cabeçalho e cálculo das quantidades.
	    List<ComplementoItemCarrinhoEmMontagem> complementosSelecionados =
	        itemEmMontagemService.listarComplementos(idSessao);

	    BigDecimal totalComplementosUnitario = BigDecimal.ZERO;

	    if (complementosSelecionados != null) {
	        for (ComplementoItemCarrinhoEmMontagem complemento : complementosSelecionados) {
	            if (complemento == null || complemento.getQuantidade() == null || complemento.getQuantidade() < 1) {
	                continue;
	            }

	            BigDecimal precoUnitarioComplemento = complemento.getPrecoUnitario() == null
	                ? BigDecimal.ZERO
	                : complemento.getPrecoUnitario();

	            totalComplementosUnitario = totalComplementosUnitario.add(
	                precoUnitarioComplemento.multiply(BigDecimal.valueOf(complemento.getQuantidade()))
	            );
	        }
	    }

	    BigDecimal precoProduto = sessao != null && sessao.getPrecoTamanhoItemEmMontagem() != null
    	    ? sessao.getPrecoTamanhoItemEmMontagem()
    	    : produto.getPreco() == null ? BigDecimal.ZERO : produto.getPreco();
    
	    BigDecimal precoUnitarioFinal = precoProduto.add(totalComplementosUnitario);

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    for (int i = 1; i <= 9; i++) {
	        int quantidade = qm * i;

	        // Valor exibido deve refletir produto + complementos, multiplicado pela quantidade escolhida.
	        BigDecimal preco = precoUnitarioFinal.multiply(BigDecimal.valueOf(quantidade));

	        String labelQuantidade = quantidade == 1
	            ? "1 unidade"
	            : quantidade + " unidades";

	        itens.add(helperService.row(
	            "COMANDO|ADICIONAR_PRODUTO|" + idProduto + "|" + quantidade,
	            labelQuantidade,
	            "Valor total: " + msg.formatarMoeda(preco)
	        ));
	    }

	    itens.add(helperService.row(
	        "COMANDO|SOLICITAR_QUANTIDADE|" + idProduto,
	        "Outra quantidade",
	        "Informar manualmente"
	    ));

	    String nomeProduto = msg.safe(produto.getNome());
	    String descricaoProduto = msg.safe(produto.getDescricao());
	    String precoUnitario = msg.formatarMoeda(precoProduto);

	    // =========================================================
	    // CLIENTE — Cabeçalho do item em montagem
	    // =========================================================

	    StringBuilder cabecalho = new StringBuilder();
	    cabecalho.append("*").append(msg.trunc(nomeProduto, 80)).append("*");

	    if (StringUtils.hasText(descricaoProduto)) {
	        cabecalho.append("\n").append(msg.trunc(descricaoProduto, 700));
	    }

	    if (sessao != null && StringUtils.hasText(sessao.getNomeTamanhoItemEmMontagem())) {
	        cabecalho.append("\n*Tamanho:* ")
	            .append(msg.safe(sessao.getNomeTamanhoItemEmMontagem()));
	    }
	    
	    cabecalho.append("\n\n*Preço:* ").append(precoUnitario);

	    // Complementos escolhidos antes da quantidade devem aparecer no resumo parcial do item.
	    if (complementosSelecionados != null && !complementosSelecionados.isEmpty()) {
	        cabecalho.append("\n\n*Complementos:*");

	        for (ComplementoItemCarrinhoEmMontagem complemento : complementosSelecionados) {
	            if (complemento == null || complemento.getQuantidade() == null || complemento.getQuantidade() < 1) {
	                continue;
	            }

	            BigDecimal precoUnitarioComplemento = complemento.getPrecoUnitario() == null
	                ? BigDecimal.ZERO
	                : complemento.getPrecoUnitario();

	            BigDecimal subtotalComplemento = precoUnitarioComplemento.multiply(
	                BigDecimal.valueOf(complemento.getQuantidade())
	            );

	            cabecalho.append("\n- ")
	                .append(complemento.getQuantidade())
	                .append("x ")
	                .append(msg.safe(complemento.getNome()))
	                .append(": ")
	                .append(msg.formatarMoeda(subtotalComplemento));
	        }
	    }

	    cabecalho.append("\n\nEscolha uma quantidade:");

	    return msg.lista(
	        whatsappCliente,
	        msg.trunc(cabecalho.toString(), 1024),
	        "Quantidades",
	        "Quantidades",
	        itens
	    );
	}
    
    public MensagemWhatsappSaidaDTO montarVisualizacaoCarrinho(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao
	) {

	    Carrinho carrinho = carrinhoService.buscarCarrinhoAtual(idSessao);

	    if (carrinho == null || carrinho.getItens() == null || carrinho.getItens().isEmpty()) {

	        String corpo =
	            "Seu carrinho está vazio 🛒\n\n" +
	                "Para continuar, inclua um novo item.";

	        return msg.botoes(
	            whatsappCliente,
	            msg.trunc(corpo, 1024),
	            List.of(
	                helperService.btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Incluir item")
	            )
	        );
	    }

	    BigDecimal total = carrinhoService.calcularSubtotalCarrinho(carrinho);
	    String itensTexto = carrinhoService.montarResumoItensDoCarrinho(estabelecimento, carrinho);

	    String corpo =
	        "*Seu carrinho* 🛒\n\n" +
	            msg.trunc(itensTexto, 800) + "\n\n" +
	            "*Total:* " + msg.formatarMoeda(total);

	    return msg.botoes(
	        whatsappCliente,
	        msg.trunc(corpo, 1024),
	        List.of(
	            helperService.btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Incluir item"),
	            helperService.btn("COMANDO|LIMPAR_CARRINHO", "🗑️ Limpar"),
	            helperService.btn("COMANDO|INFORMAR_ENDERECO", "🏍️ Entrega")
	        )
	    );
	}

    public MensagemWhatsappSaidaDTO montarCarrinhoLimpo(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao
	) {

	    String corpo =
	        "Carrinho limpo ✅\n\n" +
	            "Seu carrinho está vazio agora.\n\n" +
	            "Para continuar, inclua um novo item.";

	    return msg.botoes(
	        whatsappCliente,
	        msg.trunc(corpo, 1024),
	        List.of(
	            helperService.btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Incluir item")
	        )
	    );
	}

    public MensagemWhatsappSaidaDTO montarEscolhaFormaPagamento(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao
	) {

	    Carrinho carrinho = carrinhoService.buscarCarrinhoAtual(idSessao);
	    BigDecimal subtotalItens = carrinhoService.calcularSubtotalCarrinho(carrinho);

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

    

    public String formatarPagamentoParaTexto(SessaoAtendimentoWhatsapp s) {

        FormaPagamentoPedido fp = s.getFormaPagamento();
        if (fp == null) {
            return "Não informado";
        }

        if (fp == FormaPagamentoPedido.CREDITO) {
            return "Cartão (Crédito)";
        }

        if (fp == FormaPagamentoPedido.DEBITO_PIX) {
            return "Débito/PIX";
        }

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
                "Se precisar, digite *MENU* para voltar ao início.";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    public BigDecimal calcularPrecoPorQuantidade(Produto produto, int quantidade) {
        BigDecimal precoUnit = produto == null || produto.getPreco() == null ? BigDecimal.ZERO : produto.getPreco();
        return precoUnit.multiply(BigDecimal.valueOf(quantidade));
    }

    
    public boolean produtoPossuiComplementos(Produto produto) {

        if (produto == null || produto.getId() == null) {
            return false;
        }

        return !buscarGruposComplementoAplicaveis(produto).isEmpty();
    }

    public MensagemWhatsappSaidaDTO montarListaComplementosEmMontagem(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        Produto produto = extracaoService.extrairProduto(
            estabelecimento,
            sessao.getIdProdutoItemEmMontagem()
        );

        if (produto == null || produto.getCategoria() == null || produto.getCategoria().getId() == null) {
            return msg.texto(whatsappCliente, "Não consegui identificar os complementos deste produto.");
        }

        List<GrupoComplemento> grupos = buscarGruposComplementoAplicaveis(produto);

        if (grupos.isEmpty()) {
        	// =========================================================
        	// CLIENTE — Fallback para quantidades
        	// =========================================================

        	// Se não houver grupo/complemento disponível, preserva o produto em montagem e segue para quantidade.
        	return montarListaQuantidades(
        	    estabelecimento,
        	    whatsappCliente,
        	    idSessao,
        	    sessao.getIdCategoriaItemEmMontagem(),
        	    sessao.getQuantidadeMultiplaItemEmMontagem(),
        	    produto.getId()
        	);
        }

        int posicaoAtual = sessao.getOrdemGrupoComplementoItemEmMontagem() == null
            ? 1
            : sessao.getOrdemGrupoComplementoItemEmMontagem();

        if (posicaoAtual < 1) {
            posicaoAtual = 1;
        }

        if (posicaoAtual > grupos.size()) {
        	return montarListaQuantidades(
    		    estabelecimento,
    		    whatsappCliente,
    		    idSessao,
    		    sessao.getIdCategoriaItemEmMontagem(),
    		    sessao.getQuantidadeMultiplaItemEmMontagem(),
    		    produto.getId()
    		);
        }

        GrupoComplemento grupoAtual = grupos.get(posicaoAtual - 1);

        List<Complemento> complementos = complementoRepository
            .findByGrupoIdAndAtivoTrueOrderByNomeAsc(grupoAtual.getId());

        if (complementos.isEmpty()) {
        	return montarListaQuantidades(
    		    estabelecimento,
    		    whatsappCliente,
    		    idSessao,
    		    sessao.getIdCategoriaItemEmMontagem(),
    		    sessao.getQuantidadeMultiplaItemEmMontagem(),
    		    produto.getId()
    		);
        }

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(helperService.row(
            "COMANDO|NAO_QUERO_COMPLEMENTO",
            "Avançar sem adicionar",
            ""
        ));

        List<ComplementoItemCarrinhoEmMontagem> complementosSelecionados =
    	    itemEmMontagemService.listarComplementos(idSessao);

    	for (Complemento complemento : complementos) {

    	    int quantidadeAdicionada = complementosSelecionados.stream()
    	        .filter(item -> item != null && item.getComplemento() != null)
    	        .filter(item -> complemento.getId().equals(item.getComplemento().getId()))
    	        .map(ComplementoItemCarrinhoEmMontagem::getQuantidade)
    	        .filter(qtd -> qtd != null && qtd > 0)
    	        .findFirst()
    	        .orElse(0);

    	    String statusAdicao = quantidadeAdicionada > 0
    	        ? "Já adicionado " + quantidadeAdicionada + "x"
    	        : "Ainda não adicionado";

    	    itens.add(helperService.row(
    	        "COMANDO|SELECIONAR_COMPLEMENTO|" + complemento.getId(),
    	        msg.trunc(complemento.getNome(), 24),
    	        msg.formatarMoeda(complemento.getPrecoAdicional()) + " - " + statusAdicao
    	    ));
    	}

    	Integer maximoSelecoes = grupoAtual.getMaximoSelecoes();

    	int quantidadeSelecionadaGrupoAtual = 0;

    	for (Complemento complemento : complementos) {
    	    if (complemento == null || complemento.getId() == null) {
    	        continue;
    	    }

    	    quantidadeSelecionadaGrupoAtual += complementosSelecionados.stream()
    	        .filter(item -> item != null && item.getComplemento() != null)
    	        .filter(item -> complemento.getId().equals(item.getComplemento().getId()))
    	        .map(ComplementoItemCarrinhoEmMontagem::getQuantidade)
    	        .filter(qtd -> qtd != null && qtd > 0)
    	        .findFirst()
    	        .orElse(0);
    	}

    	String orientacaoComplementos;

    	if (maximoSelecoes != null && maximoSelecoes > 0) {
    	    int restante = maximoSelecoes - quantidadeSelecionadaGrupoAtual;

    	    if (quantidadeSelecionadaGrupoAtual <= 0) {
    	        orientacaoComplementos = "Você pode adicionar até " + maximoSelecoes + " complementos.";
    	    } else if (restante == 1) {
    	        orientacaoComplementos = "Você pode adicionar mais 1 complemento.";
    	    } else {
    	        orientacaoComplementos = "Você pode adicionar mais " + restante + " complementos.";
    	    }
    	} else {
    	    orientacaoComplementos = quantidadeSelecionadaGrupoAtual <= 0
    	        ? "Escolha quantos complementos você quiser para adicionar ao seu pedido."
    	        : "Você pode continuar adicionando complementos ao seu pedido.";
    	}

    	StringBuilder cabecalho = new StringBuilder();

    	BigDecimal precoProduto = sessao.getPrecoTamanhoItemEmMontagem() != null
		    ? sessao.getPrecoTamanhoItemEmMontagem()
		    : produto.getPreco() == null ? BigDecimal.ZERO : produto.getPreco();

		cabecalho.append("*").append(msg.safe(produto.getNome())).append("*");

		if (StringUtils.hasText(sessao.getNomeTamanhoItemEmMontagem())) {
		    cabecalho.append("\n*Tamanho:* ")
		        .append(msg.safe(sessao.getNomeTamanhoItemEmMontagem()));
		}

		cabecalho.append("\n*Preço:* ")
		    .append(msg.formatarMoeda(precoProduto))
		    .append("\n\n")
		    .append("Complementos:\n")
		    .append(orientacaoComplementos);

    	if (quantidadeSelecionadaGrupoAtual > 0) {
    	    cabecalho.append("\n\n*Já adicionados:*");

    	    for (Complemento complemento : complementos) {
    	        if (complemento == null || complemento.getId() == null) {
    	            continue;
    	        }

    	        int quantidadeAdicionada = complementosSelecionados.stream()
    	            .filter(item -> item != null && item.getComplemento() != null)
    	            .filter(item -> complemento.getId().equals(item.getComplemento().getId()))
    	            .map(ComplementoItemCarrinhoEmMontagem::getQuantidade)
    	            .filter(qtd -> qtd != null && qtd > 0)
    	            .findFirst()
    	            .orElse(0);

    	        if (quantidadeAdicionada < 1) {
    	            continue;
    	        }

    	        BigDecimal precoUnitario = complemento.getPrecoAdicional() == null
	        	    ? BigDecimal.ZERO
	        	    : complemento.getPrecoAdicional();

	        	BigDecimal subtotal = precoUnitario.multiply(BigDecimal.valueOf(quantidadeAdicionada));

	        	cabecalho.append("\n• ")
	        	    .append(quantidadeAdicionada)
	        	    .append("x ")
	        	    .append(msg.safe(complemento.getNome()))
	        	    .append(": ")
	        	    .append(msg.formatarMoeda(subtotal));
    	    }
    	}

    	return msg.lista(
    	    whatsappCliente,
    	    msg.trunc(cabecalho.toString(), 1024),
    	    "Complementos",
    	    msg.trunc("Complementos", 24),
    	    itens
    	);
    }
    
    
    // =========================================================
 	// Tamanhos dos produtos
 	// =========================================================
    public boolean produtoPossuiTamanhos(Produto produto) {

        if (produto == null || produto.getId() == null) {
            return false;
        }

        GradeTamanho gradeAplicavel = gradeTamanhoService.buscarGradeAplicavelAoProduto(produto);

        if (gradeAplicavel == null) {
            return false;
        }

        return opcaoTamanhoProdutoRepository
            .findByProdutoIdAndAtivoTrueOrderByOpcaoTamanhoOrdemAscOpcaoTamanhoNomeAsc(produto.getId())
            .stream()
            .anyMatch(opcaoProduto -> opcaoProduto.getOpcaoTamanho() != null
                && opcaoProduto.getOpcaoTamanho().isAtivo()
                && opcaoProduto.getOpcaoTamanho().getGrade() != null
                && Objects.equals(opcaoProduto.getOpcaoTamanho().getGrade().getId(), gradeAplicavel.getId())
                && opcaoProduto.getPreco() != null);
    }

    public MensagemWhatsappSaidaDTO montarListaTamanhosProduto(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    Long idCategoria,
	    Integer quantidadeMultipla,
	    Long idProduto
	) {

        Produto produto = extracaoService.extrairProduto(estabelecimento, idProduto);

        if (produto == null || produto.getCategoria() == null || produto.getCategoria().getId() == null) {
            return msg.texto(whatsappCliente, "Produto não encontrado.");
        }

        GradeTamanho gradeAplicavel = gradeTamanhoService.buscarGradeAplicavelAoProduto(produto);

        if (gradeAplicavel == null) {
            return montarSelecaoProduto(
                estabelecimento,
                whatsappCliente,
                idSessao,
                idCategoria,
                quantidadeMultipla,
                idProduto
            );
        }

        List<OpcaoTamanhoProduto> opcoes = opcaoTamanhoProdutoRepository
    	    .findByProdutoIdAndAtivoTrueOrderByOpcaoTamanhoOrdemAscOpcaoTamanhoNomeAsc(idProduto)
    	    .stream()
    	    .filter(opcaoProduto -> opcaoProduto.getOpcaoTamanho() != null)
    	    .filter(opcaoProduto -> opcaoProduto.getOpcaoTamanho().isAtivo())
    	    .filter(opcaoProduto -> opcaoProduto.getOpcaoTamanho().getGrade() != null)
    	    .filter(opcaoProduto -> Objects.equals(opcaoProduto.getOpcaoTamanho().getGrade().getId(), gradeAplicavel.getId()))
    	    .filter(opcaoProduto -> opcaoProduto.getPreco() != null)

    	    // Exibe os tamanhos do mais caro para o mais barato para destacar versões premium primeiro.
    	    .sorted(
    	        Comparator.comparing(
    	            OpcaoTamanhoProduto::getPreco,
    	            Comparator.nullsLast(BigDecimal::compareTo)
    	        ).reversed()
    	    )

    	    .toList();

        if (opcoes.isEmpty()) {
            return montarSelecaoProduto(
                estabelecimento,
                whatsappCliente,
                idSessao,
                idCategoria,
                quantidadeMultipla,
                idProduto
            );
        }

        List<MensagemInterativaItemListaWhatsappDTO> itens = opcoes.stream()
            .map(opcaoProduto -> helperService.row(
                "COMANDO|SELECIONAR_TAMANHO|" + idProduto + "|" + opcaoProduto.getId(),
                msg.trunc(opcaoProduto.getOpcaoTamanho().getNome(), 24),
                "Preço: " + msg.formatarMoeda(opcaoProduto.getPreco())
            ))
            .toList();

        StringBuilder cabecalho = new StringBuilder();

        cabecalho.append("*").append(msg.trunc(msg.safe(produto.getNome()), 80)).append("*");

        if (StringUtils.hasText(produto.getDescricao())) {
            cabecalho.append("\n").append(msg.trunc(msg.safe(produto.getDescricao()), 700));
        }

        cabecalho.append("\n\nEscolha o tamanho:");

        return msg.lista(
            whatsappCliente,
            msg.trunc(cabecalho.toString(), 1024),
            "Tamanhos",
            "Tamanhos",
            itens
        );
    }
    
    private List<GrupoComplemento> buscarGruposComplementoAplicaveis(Produto produto) {

        if (produto == null || produto.getId() == null) {
            return List.of();
        }

        List<GrupoComplemento> gruposProduto = grupoComplementoRepository
            .findByProdutoIdAndAtivoTrueAndExcluidoFalseOrderByOrdemAscNomeAsc(produto.getId());

        // Complementos próprios do produto têm prioridade sobre os herdados da categoria.
        if (!gruposProduto.isEmpty()) {
            return gruposProduto;
        }

        if (produto.getCategoria() == null || produto.getCategoria().getId() == null) {
            return List.of();
        }

        return grupoComplementoRepository
            .findByCategoriaIdAndAtivoTrueAndExcluidoFalseOrderByOrdemAscNomeAsc(produto.getCategoria().getId());
    }
}