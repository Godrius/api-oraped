package br.com.oraped.service.whatsapp.administrador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.integration.HostingerClient;
import br.com.oraped.integration.WhatsappCloudMediaClient;
import br.com.oraped.repository.produto.CategoriaProdutoRepository;
import br.com.oraped.repository.produto.ProdutoRepository;
import br.com.oraped.service.produto.CategoriaProdutoService;
import br.com.oraped.service.produto.ProdutoService;
import br.com.oraped.service.produto.tamanho.GradeTamanhoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminProdutoService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Concentrar a administração direta dos produtos do cardápio via WhatsApp.
 *
 * Aplicação:
 * - cadastra novos produtos
 * - monta o menu de ações do produto
 * - altera preço único, nome, descrição e foto
 * - remove foto
 * - exclui produto
 *
 * Regra:
 * - quando houver grade aplicável ao produto, o preço é configurado por tamanho
 * - a grade aplicável pode vir do próprio produto ou da categoria
 * - quando não houver grade aplicável, Produto.preco é usado como preço único
 *
 * Utilização:
 * Deve ser chamado pelo roteamento administrativo de produtos.
 * A navegação de categorias/listas fica em AdminCategoriaService.
 */
@Service
@RequiredArgsConstructor
public class AdminProdutoService {

    private final ProdutoService produtoService;
    private final CategoriaProdutoService categoriaProdutoService;
    private final SessaoWhatsappAdminProdutoService sessaoAdminProdutoService;
    private final WhatsappCloudMediaClient whatsappCloudMediaClient;
    private final HostingerClient hostingerClient;
    private final AdminCategoriaService adminCategoriaService;
    private final AdminTamanhoProdutoService adminTamanhoProdutoService;
    private final GradeTamanhoService gradeTamanhoService;
    private final AdminWhatsappUiHelper sup;

    private final ProdutoRepository produtoRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuAcoesProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetLista,
	    String mensagemCabecalho
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    int safeOffset = normalizarOffset(offsetLista);

	    Produto produto = buscarProdutoValidado(
	        estabelecimento,
	        idProduto,
	        idCategoria
	    );

	    boolean produtoUsaGrade =
	    	    gradeTamanhoService.buscarGradeAplicavelAoProduto(produto) != null;

	    String nome =
	        sup.msg().trunc(
	            sup.msg().safe(produto.getNome()),
	            80
	        );

	    String descricao = sup.msg().safe(produto.getDescricao());

	    if (!StringUtils.hasText(descricao)) {
	        descricao = "Sem descrição.";
	    }

	    String preco = produtoUsaGrade
	        ? "Por tamanho"
	        : sup.msg().formatarMoeda(produto.getPreco());

	    boolean temFoto = StringUtils.hasText(produto.getUrlFoto());

	    String statusFoto = temFoto
	        ? "Com foto cadastrada"
	        : "Sem foto cadastrada";

	    StringBuilder cabecalho = new StringBuilder();

	    // Permite reutilizar o menu exibindo mensagens de confirmação acima da lista.
	    if (StringUtils.hasText(mensagemCabecalho)) {
	        cabecalho.append(
	            sup.msg().trunc(
	                mensagemCabecalho.trim(),
	                400
	            )
	        ).append("\n\n");
	    }

	    cabecalho
	        .append("*")
	        .append(nome)
	        .append("*\n")
	        .append(sup.msg().trunc(descricao, 500))
	        .append("\n\n")
	        .append("*Preço atual:* ")
	        .append(preco)
	        .append("\n")
	        .append("*Foto:* ")
	        .append(statusFoto)
	        .append("\n\n")
	        .append("O que deseja fazer?");

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(sup.row(
	        "COMANDO|ADMIN_PROD_NOME_MENU|" +
	            idProduto + "|" +
	            idCategoria + "|" +
	            safeOffset,
	        "✏️ Ajustar nome",
	        "Enviar novo nome do produto"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_PROD_DESC_MENU|" +
	            idProduto + "|" +
	            idCategoria + "|" +
	            safeOffset,
	        "📝 Ajustar descrição",
	        "Enviar nova descrição"
	    ));

	    if (produtoUsaGrade) {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_PROD_TAMANHOS_PRECOS|" +
	                idProduto + "|" +
	                idCategoria + "|" +
	                safeOffset + "|0",
	            "💲 Ajustar preços",
	            "Preço por tamanho"
	        ));
	    } else {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_PROD_PRECO_MANUAL|" +
	                idProduto + "|" +
	                idCategoria + "|" +
	                safeOffset,
	            "💲 Ajustar preço",
	            "Enviar novo valor"
	        ));
	    }
	    
	    itens.add(sup.row(
    	    "COMANDO|ADMIN_PROD_TAMANHOS_MENU|" +
    	        idProduto + "|" +
    	        idCategoria + "|" +
    	        safeOffset,
    	    "📏 Variações de tamanhos",
    	    "Tamanhos só deste produto"
    	));

    	itens.add(sup.row(
    	    "COMANDO|ADMIN_PROD_COMPLEMENTOS_MENU|" +
    	        idProduto + "|" +
    	        idCategoria + "|" +
    	        safeOffset,
    	    "➕ Associar complementos",
    	    "Complementos só deste produto"
    	));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_PROD_FOTO_MENU|" +
	            idProduto + "|" +
	            idCategoria + "|" +
	            safeOffset,
	        "🖼️ Atualizar foto",
	        "Enviar nova foto do produto"
	    ));

	    if (temFoto) {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_PROD_FOTO_REMOVER_CONFIRM|" +
	                idProduto + "|" +
	                idCategoria + "|" +
	                safeOffset,
	            "🗑️ Remover foto",
	            "Apagar foto atual"
	        ));
	    }

	    itens.add(sup.row(
	        "COMANDO|ADMIN_PROD_EXCLUIR_CONFIRM|" +
	            idProduto + "|" +
	            idCategoria + "|" +
	            safeOffset,
	        "❌ Excluir produto",
	        "Remover produto do cardápio"
	    ));

	    itens.add(sup.row(
	        montarComandoVoltarListaProdutos(idCategoria, safeOffset),
	        "⬅️ Voltar",
	        "Lista de produtos"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cardapio_produto_acoes",
	        sup.msg().lista(
	            whatsappAdmin,
	            sup.msg().truncWord(cabecalho.toString(), 1024),
	            "Ações",
	            "Ações",
	            itens
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuAjustePrecoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        String descricao = sup.msg().safe(produto.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String cabecalho =
            "💲 Ajustar preço\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                sup.msg().trunc(descricao, 500) + "\n\n" +
                "*Preço atual:* " + sup.msg().formatarMoeda(produto.getPreco()) + "\n\n" +
                "Escolha um ajuste:";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_preco_menu",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(cabecalho, 1024),
                "Preço",
                "Preço",
                List.of(
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|100|" + idCategoria + "|" + safeOffset, "+ R$ 1,00", "Aumentar"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|200|" + idCategoria + "|" + safeOffset, "+ R$ 2,00", "Aumentar"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|500|" + idCategoria + "|" + safeOffset, "+ R$ 5,00", "Aumentar"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-100|" + idCategoria + "|" + safeOffset, "- R$ 1,00", "Diminuir"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-200|" + idCategoria + "|" + safeOffset, "- R$ 2,00", "Diminuir"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-500|" + idCategoria + "|" + safeOffset, "- R$ 5,00", "Diminuir"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_MANUAL|" + idProduto + "|" + idCategoria + "|" + safeOffset, "Outro valor", "Enviar 1 mensagem"),
                    sup.row(montarComandoVoltarProduto(idProduto, idCategoria, safeOffset), "⬅️ Voltar", "Ações do produto")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin listarCategoriasParaNovoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offsetCategorias
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        List<CategoriaProduto> categorias =
            categoriaProdutoRepository.findByEstabelecimentoIdAndAtivaTrueOrderByNomeAsc(estabelecimento.getId());

        if (categorias.isEmpty()) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_produto_novo_sem_categorias",
                sup.msg().botoes(
                    whatsappAdmin,
                    "➕ *Novo produto*\n\nAntes de cadastrar produtos, cadastre pelo menos uma categoria.",
                    List.of(
                        sup.btn("COMANDO|ADMIN_CATEGORIA_NOVA_MENU|0", "➕ Nova categoria"),
                        sup.btn("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Cardápio")
                    )
                )
            );
        }

        int safeOffset = normalizarOffset(offsetCategorias);
        if (safeOffset >= categorias.size()) {
            safeOffset = 0;
        }

        int pageSize = categorias.size() > 10 ? 8 : 9;
        int endExclusive = Math.min(safeOffset + pageSize, categorias.size());
        List<CategoriaProduto> page = categorias.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < categorias.size();

        String corpo =
            "➕ *Novo produto*\n\n" +
                "Escolha em qual categoria o produto será cadastrado.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (CategoriaProduto categoria : page) {
            itens.add(sup.row(
                "COMANDO|ADMIN_PRODUTO_NOVO_MENU|" + categoria.getId() + "|" + safeOffset,
                sup.msg().trunc(sup.msg().safe(categoria.getNome()), 24),
                "Cadastrar produto aqui"
            ));
        }

        if (temMais) {
            itens.add(sup.row(
                "COMANDO|ADMIN_PRODUTO_NOVO_CATEGORIA_MENU|" + endExclusive,
                "➡️ Mais categorias",
                "Ver próxima página"
            ));
        }

        itens.add(sup.row(
            "COMANDO|ADMIN_CARDAPIO_MENU",
            "⬅️ Voltar",
            "Menu do cardápio"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_produto_novo_categorias",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(corpo, 1024),
                "Categorias",
                "Categorias",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idCategoria,
        Integer offsetCategorias
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);
        validarSessao(idSessao);

        CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
        validarCategoriaDoEstabelecimento(estabelecimento, categoria);

        sessaoAdminProdutoService.marcarAguardandoNovoProduto(
            idSessao,
            idCategoria,
            offsetCategorias
        );

        String corpo =
    	    "➕ *Novo produto*\n\n" +
    	        "Categoria: *" + sup.msg().trunc(sup.msg().safe(categoria.getNome()), 80) + "*\n\n" +
    	        "Para melhorar a organização do cardápio, informe apenas o *nome principal do produto*.\n\n" +
    	        "Depois, use a *descrição* para complementar informações como:\n" +
    	        "- tamanho\n" +
    	        "- volume\n" +
    	        "- ingredientes\n" +
    	        "- detalhes do produto\n\n" +
    	        "*Exemplo:*\n" +
    	        "- Nome: Coca-Cola\n" +
    	        "- Descrição: Lata 350ml\n\n" +
    	        "Outros exemplos de nome:\n" +
    	        "- X-Burger\n" +
    	        "- Açaí\n" +
    	        "- Batata frita\n\n" +
    	        "Digite o nome agora.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_produto_novo_digitacao",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn(
                        "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_LISTA|" + idCategoria + "|" + normalizarOffset(offsetCategorias),
                        "⬅️ Cancelar"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroProdutoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String nomeProduto
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);
	    validarSessao(idSessao);

	    if (!StringUtils.hasText(nomeProduto)) {
	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_produto_nome_invalido",
	            sup.msg().texto(
	                whatsappAdmin,
	                "Não consegui identificar o nome do produto.\n\nEnvie apenas o nome."
	            )
	        );
	    }

	    Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovoProduto(idSessao);
	    Integer offset = sessaoAdminProdutoService.getOffsetListaNovoProduto(idSessao);

	    CategoriaProduto categoria = categoriaProdutoRepository.findById(idCategoria)
	        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));

	    validarCategoriaDoEstabelecimento(estabelecimento, categoria);

	    Produto produto = new Produto();
	    produto.setEstabelecimento(estabelecimento);
	    produto.setCategoria(categoria);
	    produto.setNome(nomeProduto.trim());

	    // O produto nasce sem preço e sem descrição; o cadastro guiado coleta esses dados em seguida.
	    produto.setPreco(BigDecimal.ZERO);

	    produtoRepository.save(produto);

	    sessaoAdminProdutoService.limparAguardandoNovoProduto(idSessao);
	    sessaoAdminProdutoService.marcarCadastroGuiadoProduto(idSessao);
	    sessaoAdminProdutoService.marcarAguardandoNovaDescricaoProduto(
	        idSessao,
	        produto.getId(),
	        idCategoria,
	        normalizarOffset(offset)
	    );

	    String corpo =
	        "✅ Produto cadastrado.\n\n" +
	            "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
	            "Agora envie a *descrição do produto*.\n\n" +
	            "Use a descrição para complementar o nome.\n\n" +
	            "Exemplo:\n" +
	            "- Nome: *Coca-Cola*\n" +
	            "- Descrição: *Lata 350ml*";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_produto_novo_descricao_digitacao",
	        sup.msg().texto(
	            whatsappAdmin,
	            sup.msg().trunc(corpo, 1024)
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdminPreco aplicarDeltaPrecoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer deltaCentavos,
        Long idCategoria,
        Integer offsetLista
    ) {

        if (deltaCentavos == null || deltaCentavos == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deltaCentavos é obrigatório");
        }

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        BigDecimal atual = produto.getPreco() == null ? BigDecimal.ZERO : produto.getPreco();
        BigDecimal novo = atual.add(BigDecimal.valueOf(deltaCentavos).movePointLeft(2));

        if (novo.compareTo(BigDecimal.ZERO) < 0) {
            novo = BigDecimal.ZERO;
        }

        produtoService.atualizarPreco(idProduto, novo);

        // Após alteração rápida, volta para a lista de produtos da categoria.
        AdministradorWhatsappResultados.ResultadoAdmin lista =
        	    adminCategoriaService.montarListaProdutosPorCategoria(estabelecimento, whatsappAdmin, idCategoria, safeOffset, null);

        String descricao = sup.msg().safe(produto.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        return new AdministradorWhatsappResultados.ResultadoAdminPreco(
            lista,
            novo,
            sup.msg().trunc(sup.msg().safe(produto.getNome()), 80),
            descricao
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarPrecoManualProdutoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetLista
	) {

	    validarSessao(idSessao);

	    int safeOffset = normalizarOffset(offsetLista);

	    Produto produto = buscarProdutoValidado(
	        estabelecimento,
	        idProduto,
	        idCategoria
	    );

	    sessaoAdminProdutoService.marcarAguardandoNovoPreco(
	        idSessao,
	        idProduto,
	        idCategoria,
	        safeOffset
	    );

	    String precoAtual = sup.msg().formatarMoeda(
	        produto.getPreco()
	    );

	    String descricao = StringUtils.hasText(produto.getDescricao())
	        ? sup.msg().safe(produto.getDescricao())
	        : "Sem descrição.";

	    String corpo =
	        "💲 *Ajustar preço*\n\n" +
	            "*" +
	            sup.msg().trunc(
	                sup.msg().safe(produto.getNome()),
	                80
	            ) +
	            "*\n" +
	            sup.msg().trunc(descricao, 300) +
	            "\n\n" +
	            "*Preço atual:* " + precoAtual + "\n\n" +
	            "Agora envie apenas o *novo preço*.\n\n" +
	            "Exemplos:\n" +
	            "- 10\n" +
	            "- 10,50\n" +
	            "- R$ 10,50";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_preco_digitacao",
	        sup.msg().botoes(
	            whatsappAdmin,
	            sup.msg().trunc(corpo, 1024),
	            List.of(
	                sup.btn(
	                    montarComandoVoltarProduto(
	                        idProduto,
	                        idCategoria,
	                        safeOffset
	                    ),
	                    "⬅️ Cancelar"
	                )
	            )
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin concluirPrecoManualProdutoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String textoDigitado
	) {

	    validarSessao(idSessao);

	    if (!StringUtils.hasText(textoDigitado)) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "textoDigitado é obrigatório");
	    }

	    if (!sessaoAdminProdutoService.isAguardandoNovoPreco(idSessao)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo preço");
	    }

	    Long idProduto = sessaoAdminProdutoService.getIdProdutoNovoPreco(idSessao);
	    Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovoPreco(idSessao);
	    int safeOffset = sessaoAdminProdutoService.getOffsetListaNovoPreco(idSessao);

	    BigDecimal novoPreco = parsePrecoDigitado(textoDigitado);
	    if (novoPreco.compareTo(BigDecimal.ZERO) < 0) {
	        novoPreco = BigDecimal.ZERO;
	    }

	    produtoService.atualizarPreco(idProduto, novoPreco);

	    // Captura o estado guiado antes de limpar o estado de preço, pois ele define o encerramento do cadastro.
	    boolean cadastroGuiado = sessaoAdminProdutoService.isCadastroGuiadoProduto(idSessao);

	    sessaoAdminProdutoService.limparAguardandoNovoPreco(idSessao);

	    if (cadastroGuiado) {
	        // O preço único é a última etapa do cadastro guiado para categorias sem grade de tamanhos.
	        sessaoAdminProdutoService.limparCadastroGuiadoProduto(idSessao);
	    }

	    // Após definir o preço, volta para a lista de ações do prórpio produto.
	    String mensagemCabecalho =
    	    "✅ Preço atualizado com sucesso!\n\n";

	    return montarMenuAcoesProduto(
    	    estabelecimento,
    	    whatsappAdmin,
    	    idProduto,
    	    idCategoria,
    	    safeOffset,
    	    mensagemCabecalho
    	);
	}

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoNomeProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        validarSessao(idSessao);
        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        sessaoAdminProdutoService.marcarAguardandoNovoNomeProduto(idSessao, idProduto, idCategoria, safeOffset);

        String corpo =
            "✏️ *Ajustar nome*\n\n" +
                "Atual: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                "Agora envie apenas o *novo nome*.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_nome_digitacao",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoNomeProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novoNome
    ) {

        validarSessao(idSessao);

        if (!StringUtils.hasText(novoNome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoNome é obrigatório");
        }

        if (!sessaoAdminProdutoService.isAguardandoNovoNomeProduto(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo nome do produto");
        }

        Long idProduto = sessaoAdminProdutoService.getIdProdutoNovoNome(idSessao);
        Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovoNome(idSessao);
        int safeOffset = sessaoAdminProdutoService.getOffsetListaNovoNome(idSessao);

        buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        String nomeLimpo = novoNome.trim();
        produtoService.atualizarNome(idProduto, nomeLimpo);
        sessaoAdminProdutoService.limparAguardandoNovoNomeProduto(idSessao);

        String mensagemCabecalho =
    	    "✅ Nome atualizado com sucesso!\n\n" +
    	        "Produto: *" + sup.msg().trunc(sup.msg().safe(nomeLimpo), 80) + "*";

    	return montarMenuAcoesProduto(
    	    estabelecimento,
    	    whatsappAdmin,
    	    idProduto,
    	    idCategoria,
    	    safeOffset,
    	    mensagemCabecalho
    	);
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoDescricaoProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        validarSessao(idSessao);
        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        sessaoAdminProdutoService.marcarAguardandoNovaDescricaoProduto(idSessao, idProduto, idCategoria, safeOffset);

        String descricaoAtual = StringUtils.hasText(produto.getDescricao())
    	    ? sup.msg().safe(produto.getDescricao())
    	    : "Sem descrição.";

    	String corpo =
    	    "📝 *Ajustar descrição*\n\n" +
    	        "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
    	        "*Descrição atual:*\n" +
    	        sup.msg().trunc(descricaoAtual, 400) + "\n\n" +
    	        "Agora envie apenas a *nova descrição*.";

    	return new AdministradorWhatsappResultados.ResultadoAdmin(
		    "admin_prod_desc_digitacao",
		    sup.msg().botoes(
		        whatsappAdmin,
		        sup.msg().trunc(corpo, 1024),
		        List.of(
		            sup.btn(
		                montarComandoVoltarProduto(idProduto, idCategoria, safeOffset),
		                "⬅️ Cancelar"
		            )
		        )
		    )
		);
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoDescricaoProdutoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String novaDesc
	) {

	    validarSessao(idSessao);

	    if (!StringUtils.hasText(novaDesc)) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novaDesc é obrigatória");
	    }

	    if (!sessaoAdminProdutoService.isAguardandoNovaDescricaoProduto(idSessao)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando nova descrição do produto");
	    }

	    Long idProduto = sessaoAdminProdutoService.getIdProdutoNovaDescricao(idSessao);
	    Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovaDescricao(idSessao);
	    int safeOffset = sessaoAdminProdutoService.getOffsetListaNovaDescricao(idSessao);

	    String descricaoAtualizada = novaDesc.trim();

	    produtoService.atualizarDescricao(idProduto, descricaoAtualizada);
	    sessaoAdminProdutoService.limparAguardandoNovaDescricaoProduto(idSessao);

	    boolean cadastroGuiado = sessaoAdminProdutoService.isCadastroGuiadoProduto(idSessao);
	    Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);
	    var gradeAplicavel = gradeTamanhoService.buscarGradeAplicavelAoProduto(produto);

	    if (cadastroGuiado && gradeAplicavel != null) {
	        var primeiraOpcao = gradeTamanhoService.listarOpcoes(gradeAplicavel.getId(), true)
	            .stream()
	            .filter(Objects::nonNull)
	            .findFirst()
	            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Nenhum tamanho ativo encontrado"));

	        return adminTamanhoProdutoService.iniciarAlteracaoPrecoTamanhoProduto(
	            estabelecimento,
	            whatsappAdmin,
	            idSessao,
	            idProduto,
	            idCategoria,
	            primeiraOpcao.getIdOpcaoTamanho(),
	            safeOffset
	        );
	    }

	    if (cadastroGuiado) {
	        return iniciarPrecoManualProdutoPorDigitacao(
	            estabelecimento,
	            whatsappAdmin,
	            idSessao,
	            idProduto,
	            idCategoria,
	            safeOffset
	        );
	    }

	    String mensagemCabecalho =
    	    "✅ Descrição atualizada com sucesso!\n\n";

    	return montarMenuAcoesProduto(
    	    estabelecimento,
    	    whatsappAdmin,
    	    idProduto,
    	    idCategoria,
    	    safeOffset,
    	    mensagemCabecalho
    	);
	}

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoFotoProdutoPorEnvioImagem(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        validarSessao(idSessao);
        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        sessaoAdminProdutoService.marcarAguardandoNovaFotoProduto(idSessao, idProduto, idCategoria, safeOffset);

        String corpo =
            "🖼️ *Atualizar foto do produto*\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                "Agora envie *uma foto* do produto nesta conversa.\n\n" +
                "Regras:\n" +
                "- envie apenas 1 imagem\n" +
                "- prefira foto nítida e bem iluminada\n" +
                "- após o envio, a foto atual será substituída";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_foto_aguardando_envio",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoFotoProdutoPorImagem(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String idMidia,
        String mimeTypeMidia
    ) {

        validarSessao(idSessao);

        if (!StringUtils.hasText(idMidia)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMidia é obrigatório");
        }

        if (!sessaoAdminProdutoService.isAguardandoNovaFotoProduto(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando nova foto do produto");
        }

        Long idProduto = sessaoAdminProdutoService.getIdProdutoNovaFoto(idSessao);
        Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovaFoto(idSessao);
        int safeOffset = sessaoAdminProdutoService.getOffsetListaNovaFoto(idSessao);

        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        WhatsappCloudMediaClient.MediaMetadata metadata = whatsappCloudMediaClient.buscarMetadata(idMidia);

        String mimeTypeFinal = StringUtils.hasText(metadata.mimeType())
            ? metadata.mimeType()
            : mimeTypeMidia;

        byte[] bytesImagem = whatsappCloudMediaClient.baixarMidia(metadata.url());
        String urlFotoAnterior = produto.getUrlFoto();

        String novaUrlFoto = hostingerClient.uploadFotoProduto(
            estabelecimento.getId(),
            produto.getId(),
            bytesImagem,
            mimeTypeFinal
        );

        produtoService.atualizarUrlFoto(produto.getId(), novaUrlFoto);
        sessaoAdminProdutoService.limparAguardandoNovaFotoProduto(idSessao);

        if (StringUtils.hasText(urlFotoAnterior) && !urlFotoAnterior.equals(novaUrlFoto)) {
            try {
                hostingerClient.deleteByUrl(urlFotoAnterior);
            } catch (Exception ignored) {
                // A atualização do produto não deve falhar por erro de limpeza do arquivo antigo.
            }
        }

        String mensagemCabecalho =
    	    "✅ Foto atualizada com sucesso!\n\n" +
    	        "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*";

    	return montarMenuAcoesProduto(
    	    estabelecimento,
    	    whatsappAdmin,
    	    idProduto,
    	    idCategoria,
    	    safeOffset,
    	    mensagemCabecalho
    	);
    }

    public AdministradorWhatsappResultados.ResultadoAdmin confirmarRemocaoFotoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        if (!StringUtils.hasText(produto.getUrlFoto())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto não possui foto cadastrada");
        }

        String corpo =
            "🗑️ *Remover foto do produto*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                "Tem certeza que deseja remover a foto atual?";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_foto_remover_confirm",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_PROD_FOTO_REMOVER|" + idProduto + "|" + idCategoria + "|" + safeOffset, "🗑️ Remover"),
                    sup.btn(montarComandoVoltarProduto(idProduto, idCategoria, safeOffset), "⬅️ Cancelar")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin removerFotoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        String urlFotoAtual = produto.getUrlFoto();

        if (!StringUtils.hasText(urlFotoAtual)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto não possui foto cadastrada");
        }

        try {
            hostingerClient.deleteByUrl(urlFotoAtual);
        } catch (Exception ignored) {
            // A remoção lógica da foto não deve falhar por erro de exclusão física no storage.
        }

        produtoService.atualizarUrlFoto(idProduto, null);

        String corpo =
            "✅ Foto removida com sucesso!\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*";

        return montarRetornoProdutoLista(
            whatsappAdmin,
            "admin_prod_foto_removida",
            corpo,
            idProduto,
            idCategoria,
            safeOffset
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin confirmarExclusaoProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetLista
	) {

	    int safeOffset = normalizarOffset(offsetLista);

	    Produto produto = buscarProdutoValidado(
	        estabelecimento,
	        idProduto,
	        idCategoria
	    );

	    String descricao = StringUtils.hasText(produto.getDescricao())
	        ? sup.msg().safe(produto.getDescricao())
	        : "Sem descrição.";

	    String corpo =
	        "⚠️ *Excluir produto*\n\n" +
	            "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
	            sup.msg().trunc(descricao, 300) + "\n\n" +
	            "Preço: " + sup.msg().formatarMoeda(produto.getPreco()) + "\n\n" +
	            "Tem certeza que deseja excluir?";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_excluir_confirm",
	        sup.msg().botoes(
	            whatsappAdmin,
	            sup.msg().trunc(corpo, 1024),
	            List.of(
	                sup.btn(
	                    "COMANDO|ADMIN_PROD_EXCLUIR|" +
	                        idProduto + "|" +
	                        idCategoria + "|" +
	                        safeOffset,
	                    "🗑️ Excluir"
	                ),
	                sup.btn(
	                    montarComandoVoltarProduto(
	                        idProduto,
	                        idCategoria,
	                        safeOffset
	                    ),
	                    "⬅️ Cancelar"
	                )
	            )
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin excluirProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetLista
	) {

	    int safeOffset = normalizarOffset(offsetLista);

	    Produto produto = buscarProdutoValidado(
	        estabelecimento,
	        idProduto,
	        idCategoria
	    );

	    String nomeProduto = sup.msg().trunc(
	        sup.msg().safe(produto.getNome()),
	        80
	    );

	    produtoService.excluir(idProduto);

	    // Após excluir, retorna diretamente para a lista da categoria
	    // exibindo a confirmação no cabeçalho da própria listagem.
	    String mensagemCabecalho =
	        "🗑️ Produto excluído com sucesso!\n\n" +
	            "Produto: *" + nomeProduto + "*";

	    return adminCategoriaService.montarListaProdutosPorCategoria(
	        estabelecimento,
	        whatsappAdmin,
	        idCategoria,
	        safeOffset,
	        mensagemCabecalho
	    );
	}

    private Produto buscarProdutoValidado(Estabelecimento estabelecimento, Long idProduto, Long idCategoria) {

        sup.validarBasico(estabelecimento, "admin");

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        if (idCategoria == null || idCategoria <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        Produto produto = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, produto);
        validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

        return produto;
    }

    private AdministradorWhatsappResultados.ResultadoAdmin montarRetornoProdutoLista(
        String whatsappAdmin,
        String chave,
        String corpo,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        int safeOffset = normalizarOffset(offsetLista);

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            chave,
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn(montarComandoVoltarProduto(idProduto, idCategoria, safeOffset), "🧾 Voltar ao produto"),
                    sup.btn(montarComandoVoltarListaProdutos(idCategoria, safeOffset), "📦 Voltar à lista")
                )
            )
        );
    }

    
    private void validarSessao(Long idSessao) {

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
    }

    private int normalizarOffset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }

    private String montarComandoVoltarListaProdutos(Long idCategoria, Integer offsetLista) {
        return "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_LISTA|" + idCategoria + "|" + normalizarOffset(offsetLista);
    }

    private String montarComandoVoltarProduto(Long idProduto, Long idCategoria, Integer offsetLista) {
        return "COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetLista);
    }

    private void validarCategoriaDoProduto(Estabelecimento estabelecimento, Produto produto, Long idCategoria) {

        CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
        validarCategoriaDoEstabelecimento(estabelecimento, categoria);

        if (produto == null || produto.getCategoria() == null || produto.getCategoria().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto não possui a categoria informada");
        }

        if (!Objects.equals(produto.getCategoria().getId(), idCategoria)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto não pertence à categoria informada");
        }
    }

    private void validarCategoriaDoEstabelecimento(Estabelecimento estabelecimento, CategoriaProduto categoria) {

        if (categoria == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada");
        }

        if (categoria.getEstabelecimento() == null || categoria.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria sem estabelecimento associado");
        }

        if (!Objects.equals(categoria.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria não pertence ao estabelecimento");
        }
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

    private BigDecimal parsePrecoDigitado(String texto) {

        String valor = texto == null ? "" : texto.trim();

        if (!StringUtils.hasText(valor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }

        valor = valor.replace("R$", "")
            .replace("r$", "")
            .replace(" ", "")
            .replace(",", ".")
            .replaceAll("[^0-9.\\-+]", "");

        if (!StringUtils.hasText(valor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }

        try {
            return new BigDecimal(valor).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }
    }
}