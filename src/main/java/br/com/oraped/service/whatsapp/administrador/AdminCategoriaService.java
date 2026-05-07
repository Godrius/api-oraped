package br.com.oraped.service.whatsapp.administrador;

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
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.produto.CategoriaProdutoService;
import br.com.oraped.service.produto.ProdutoService;
import br.com.oraped.service.produto.tamanho.GradeTamanhoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminCategoriaService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Concentrar a administração de categorias do cardápio via WhatsApp.
 *
 * Aplicação:
 * - lista categorias do estabelecimento
 * - cadastra novas categorias por digitação
 * - lista produtos dentro de uma categoria
 *
 * Utilização:
 * Deve ser chamado pelo roteamento administrativo de categorias.
 */
@Service
@RequiredArgsConstructor
public class AdminCategoriaService {

    private static final int LIST_MAX_ROWS = 10;
    private static final int LIST_PAGE_SIZE_WITH_PAGINATION = 8;
    private static final int LIST_PAGE_SIZE_WITHOUT_PAGINATION = 9;

    private final ProdutoService produtoService;
    private final CategoriaProdutoService categoriaProdutoService;
    private final GradeTamanhoService gradeTamanhoService;
    private final AdminWhatsappUiHelper uiHelper;
    private final SessaoWhatsappAdminCategoriaService sessaoAdminCategoriaService;

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuCategorias(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offset
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = normalizarOffset(offset);

        List<CategoriaProduto> categorias = categoriaProdutoService.listar(estabelecimento.getId());
        if (categorias == null) {
            categorias = List.of();
        }

        List<CategoriaProduto> ordenadas = categorias.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(c -> uiHelper.msg().safe(c.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        int total = ordenadas.size();

        if (total == 0) {
            String corpo =
                "🧾 *Categorias do cardápio*\n\n" +
                    "Nenhuma categoria ativa foi encontrada.\n\n" +
                    montarTextoAjudaCategoriasPorMarketplace(estabelecimento) +
                    "\n\n" +
                    "Cadastre uma categoria antes de adicionar produtos ao cardápio.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_cardapio_categorias_vazio",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    uiHelper.msg().trunc(corpo, 1024),
                    List.of(
                        uiHelper.btn("COMANDO|ADMIN_CATEGORIA_NOVA_MENU|0", "➕ Nova categoria"),
                        uiHelper.btn("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Voltar")
                    )
                )
            );
        }

        if (safeOffset >= total) {
            safeOffset = 0;
        }

        int pageSize = calcularPageSize(total);
        int paginasTotal = calcularTotalPaginas(total, pageSize);
        int paginaAtual = (safeOffset / pageSize) + 1;

        int endExclusive = Math.min(safeOffset + pageSize, total);
        List<CategoriaProduto> page = ordenadas.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < total;

        String cabecalho =
            "🧾 *Categorias do cardápio*\n" +
                montarLabelPagina(paginaAtual, paginasTotal) +
                "\n\n" +
                "Escolha uma categoria para ver os produtos.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (CategoriaProduto categoria : page) {
            int quantidadeProdutos = contarProdutosDaCategoria(estabelecimento, categoria.getId());

            String nomeCategoria = uiHelper.msg().safe(categoria.getNome());
            String descricao = quantidadeProdutos == 1
                ? "1 produto"
                : quantidadeProdutos + " produtos";

            itens.add(
                MensagemInterativaItemListaWhatsappDTO.builder()
                    .id("COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + categoria.getId() + "|0")
                    .title(uiHelper.msg().trunc(nomeCategoria, 24))
                    .description(uiHelper.msg().trunc(descricao, 72))
                    .build()
            );
        }

        if (temMais) {
            int nextOffset = safeOffset + page.size();

            itens.add(uiHelper.row(
                "COMANDO|ADMIN_CARDAPIO_CATEGORIAS_MENU|" + nextOffset,
                "➡️ Mais categorias",
                "Ver próxima página"
            ));
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CARDAPIO_MENU",
            "⬅️ Voltar",
            "Revisar cardápio"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cardapio_categorias_menu",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(cabecalho, 1024),
                "Categorias",
                "Categorias",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuCategoria(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idCategoria,
	    Integer offset
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);
	    validarIdCategoria(idCategoria);

	    int safeOffset = normalizarOffset(offset);

	    CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
	    validarCategoriaDoEstabelecimento(estabelecimento, categoria);

	    int totalProdutos = contarProdutosDaCategoria(estabelecimento, idCategoria);
	    String nomeCategoria = uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80);

	    String descricaoProdutos = totalProdutos == 1
	        ? "1 produto cadastrado"
	        : totalProdutos + " produtos cadastrados";

	    String cabecalho =
	        "🧾 *" + nomeCategoria + "*\n\n" +
	            "Escolha o que deseja administrar nesta categoria.";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_LISTA|" + idCategoria + "|0",
	        "📦 Produtos",
	        descricaoProdutos
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CAT_TAMANHOS_MENU|" + idCategoria + "|" + safeOffset,
	        "📏 Tamanhos e preços",
	        "Ex.: P, M, G, Família"
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + idCategoria + "|" + safeOffset,
	        "🧩 Complementos",
	        "Adicionais e opcionais"
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CARDAPIO_CATEGORIAS_MENU|" + safeOffset,
	        "⬅️ Voltar",
	        "Lista de categorias"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cardapio_categoria_menu",
	        uiHelper.msg().lista(
	            whatsappAdmin,
	            uiHelper.msg().truncWord(cabecalho, 1024),
	            "Opções",
	            "Categoria",
	            itens
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin montarListaProdutosPorCategoria(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idCategoria,
	    Integer offset
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);
	    validarIdCategoria(idCategoria);

	    int safeOffset = normalizarOffset(offset);

	    CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
	    validarCategoriaDoEstabelecimento(estabelecimento, categoria);

	    boolean categoriaUsaGrade = gradeTamanhoService.categoriaPossuiGradeAtiva(categoria.getId());

	    List<Produto> produtos = produtoService.listarPorEstabelecimentoECategoria(
	        estabelecimento.getId(),
	        idCategoria
	    );

	    if (produtos == null) {
	        produtos = List.of();
	    }

	    List<Produto> ordenados = produtos.stream()
	        .filter(Objects::nonNull)
	        .sorted(Comparator.comparing(p -> uiHelper.msg().safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
	        .collect(Collectors.toList());

	    int total = ordenados.size();

	    if (total == 0) {
	        String corpo =
	            "📦 *Produtos da categoria*\n\n" +
	                "*" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
	                "Nenhum produto foi encontrado nesta categoria.";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_cardapio_categoria_produtos_vazio",
	            uiHelper.msg().botoes(
	                whatsappAdmin,
	                uiHelper.msg().trunc(corpo, 1024),
	                List.of(
	                    uiHelper.btn(
	                        "COMANDO|ADMIN_PRODUTO_NOVO_MENU|" + idCategoria + "|" + safeOffset,
	                        "➕ Novo produto"
	                    ),
	                    uiHelper.btn(
	                        "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + safeOffset,
	                        "⬅️ Voltar"
	                    )
	                )
	            )
	        );
	    }

	    if (safeOffset >= total) {
	        safeOffset = 0;
	    }

	    int pageSize = calcularPageSize(total);
	    int paginasTotal = calcularTotalPaginas(total, pageSize);
	    int paginaAtual = (safeOffset / pageSize) + 1;

	    int endExclusive = Math.min(safeOffset + pageSize, total);
	    List<Produto> page = ordenados.subList(safeOffset, endExclusive);
	    boolean temMais = endExclusive < total;

	    String cabecalho =
	        "📦 *Produtos - " + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 60) + "*\n" +
	            montarLabelPagina(paginaAtual, paginasTotal);

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    for (Produto produto : page) {
	        itens.add(uiHelper.row(
	            montarComandoVoltarProduto(produto.getId(), idCategoria, safeOffset),
	            uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 24),
	            montarDescricaoProdutoLista(produto, categoriaUsaGrade)
	        ));
	    }

	    if (temMais) {
	        itens.add(uiHelper.row(
	            "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_LISTA|" + idCategoria + "|" + endExclusive,
	            "➡️ Mais produtos",
	            "Ver próxima página"
	        ));
	    }

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_PRODUTO_NOVO_MENU|" + idCategoria + "|" + safeOffset,
	        "➕ Novo produto",
	        "Cadastrar nesta categoria"
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + safeOffset,
	        "⬅️ Voltar",
	        "Menu da categoria"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cardapio_categoria_produtos_lista",
	        uiHelper.msg().lista(
	            whatsappAdmin,
	            uiHelper.msg().truncWord(cabecalho, 1024),
	            "Produtos",
	            "Produtos",
	            itens
	        )
	    );
	}

    private String montarDescricaoProdutoLista(Produto produto, boolean categoriaUsaGrade) {

        if (categoriaUsaGrade) {
            String descricao = uiHelper.msg().safe(produto.getDescricao());
            String sufixo = " ┃ 📏 por tamanho";

            return montarDescricaoComSufixo(descricao, sufixo);
        }

        String preco = uiHelper.msg()
            .formatarMoeda(produto.getPreco())
            .replace("R$", "")
            .trim();

        String descricao = uiHelper.msg().safe(produto.getDescricao());
        String sufixoPreco = " ┃ 💰 " + preco;

        return montarDescricaoComSufixo(descricao, sufixoPreco);
    }

    private String montarDescricaoComSufixo(String descricao, String sufixo) {

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

        return StringUtils.hasText(descricaoFinal)
            ? descricaoFinal + sufixo
            : sufixo.trim();
    }

    private String montarComandoVoltarProduto(Long idProduto, Long idCategoria, Integer offsetLista) {

        int safeOffset = normalizarOffset(offsetLista);
        validarIdCategoria(idCategoria);

        return "COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + idCategoria + "|" + safeOffset;
    }
    
    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroCategoriaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Integer offsetCategorias
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        sessaoAdminCategoriaService.marcarAguardandoNovaCategoria(
    	    idSessao,
    	    offsetCategorias
    	);

        String corpo =
            "➕ *Nova categoria de produtos*\n\n" +
                montarTextoExemplosCategoriasPorMarketplace(estabelecimento);

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_categoria_nova_digitacao",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_CARDAPIO_CATEGORIAS_MENU|0", "⬅️ Cancelar")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroCategoriaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String nomeCategoria
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        if (!StringUtils.hasText(nomeCategoria)) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_categoria_nova_nome_invalido",
                uiHelper.msg().texto(
                    whatsappAdmin,
                    "Não consegui identificar o nome da categoria.\n\nEnvie apenas o nome, por exemplo: *Bebidas*."
                )
            );
        }

        String nome = nomeCategoria.trim();

        if (categoriaProdutoService.existePorNome(estabelecimento, nome)) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_categoria_nova_duplicada",
                uiHelper.msg().texto(
                    whatsappAdmin,
                    "Já existe uma categoria com esse nome.\n\nEnvie outro nome ou toque em *MENU* para cancelar."
                )
            );
        }

        Integer offsetCategorias = sessaoAdminCategoriaService.getOffsetListaNovaCategoria(idSessao);

        CategoriaProduto categoria = categoriaProdutoService.criarPorNomeDigitado(estabelecimento, nome);

        sessaoAdminCategoriaService.limparAguardandoNovaCategoria(idSessao);

        String corpo =
            "✅ Categoria cadastrada.\n\n" +
                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_categoria_nova_ok",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn(
                        "COMANDO|ADMIN_CATEGORIA_NOVA_MENU|" + offsetCategorias,
                        "➕ Nova categoria"
                    ),
                    uiHelper.btn(
                        "COMANDO|ADMIN_PRODUTO_NOVO_MENU|" + categoria.getId() + "|0",
                        "➕ Inserir produtos"
                    ),
                    uiHelper.btn(
                        "COMANDO|ADMIN_CARDAPIO_CATEGORIAS_MENU|" + offsetCategorias,
                        "📂 Ver categorias"
                    )
                )
            )
        );
    }

    private String montarTextoAjudaCategoriasPorMarketplace(Estabelecimento estabelecimento) {

        String categoriaMarketplace = obterCategoriaMarketplace(estabelecimento);

        String exemplos;

        switch (categoriaMarketplace) {
            case "FARMÁCIA":
            case "FARMACIA":
                exemplos =
                    "- Analgésicos\n" +
                        "- Antialérgicos\n" +
                        "- Higiene pessoal\n" +
                        "- Cosméticos\n" +
                        "- Infantil";
                break;

            case "MERCADO":
                exemplos =
                    "- Hortifruti\n" +
                        "- Açougue\n" +
                        "- Padaria\n" +
                        "- Bebidas\n" +
                        "- Limpeza";
                break;

            case "RESTAURANTE":
                exemplos =
                    "- Pratos principais\n" +
                        "- Pizzas\n" +
                        "- Bebidas\n" +
                        "- Sobremesas\n" +
                        "- Combos";
                break;

            case "BEBIDAS":
                exemplos =
                    "- Cervejas\n" +
                        "- Refrigerantes\n" +
                        "- Destilados\n" +
                        "- Vinhos\n" +
                        "- Energéticos";
                break;

            case "PET SHOP":
                exemplos =
                    "- Rações\n" +
                        "- Petiscos\n" +
                        "- Higiene\n" +
                        "- Brinquedos\n" +
                        "- Acessórios";
                break;

            case "TAXI":
            case "TÁXI":
                exemplos =
                    "- Corridas locais\n" +
                        "- Aeroporto\n" +
                        "- Viagens\n" +
                        "- Entregas rápidas";
                break;

            default:
                exemplos =
                    "- Produtos principais\n" +
                        "- Promoções\n" +
                        "- Combos\n" +
                        "- Mais vendidos";
                break;
        }

        return "*O que são categorias?*\n" +
            "Categorias organizam os produtos do cardápio em grupos, facilitando a navegação do cliente.\n\n" +
            "*Como elas são usadas?*\n" +
            "Quando o cliente acessa o cardápio pelo WhatsApp, ele primeiro escolhe uma categoria e depois visualiza os produtos cadastrados nela.\n\n" +
            "*Exemplos:*\n" +
            exemplos;
    }

    private String montarTextoExemplosCategoriasPorMarketplace(Estabelecimento estabelecimento) {

        String categoriaMarketplace = obterCategoriaMarketplace(estabelecimento);

        String exemplos;

        switch (categoriaMarketplace) {
            case "FARMÁCIA":
            case "FARMACIA":
                exemplos =
                    "- Analgésicos\n" +
                        "- Antialérgicos\n" +
                        "- Cosméticos\n" +
                        "- Higiene pessoal";
                break;

            case "MERCADO":
                exemplos =
                    "- Hortifruti\n" +
                        "- Açougue\n" +
                        "- Padaria\n" +
                        "- Bebidas";
                break;

            case "RESTAURANTE":
                exemplos =
                    "- Pratos principais\n" +
                        "- Bebidas\n" +
                        "- Sobremesas\n" +
                        "- Combos";
                break;

            case "BEBIDAS":
                exemplos =
                    "- Cervejas\n" +
                        "- Refrigerantes\n" +
                        "- Destilados\n" +
                        "- Vinhos";
                break;

            case "PET SHOP":
                exemplos =
                    "- Rações\n" +
                        "- Petiscos\n" +
                        "- Higiene\n" +
                        "- Acessórios";
                break;

            case "TAXI":
            case "TÁXI":
                exemplos =
                    "- Corridas locais\n" +
                        "- Aeroporto\n" +
                        "- Viagens\n" +
                        "- Entregas";
                break;

            default:
                exemplos =
                    "- Produtos principais\n" +
                        "- Promoções\n" +
                        "- Combos";
                break;
        }

        return "Digite o *nome da categoria*.\n\n" +
            "As categorias organizam seu cardápio para o cliente navegar melhor.\n\n" +
            "*Exemplos para seu tipo de negócio:*\n" +
            exemplos +
            "\n\n" +
            "Use nomes curtos e claros.";
    }

    private String obterCategoriaMarketplace(Estabelecimento estabelecimento) {

        if (estabelecimento == null
            || estabelecimento.getCategoriaMarketplace() == null
            || !StringUtils.hasText(estabelecimento.getCategoriaMarketplace().getNome())) {

            return "";
        }

        return estabelecimento.getCategoriaMarketplace().getNome().trim().toUpperCase(Locale.ROOT);
    }

    

    

    private int normalizarOffset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }

    private int calcularPageSize(int total) {
        return total > LIST_MAX_ROWS
            ? LIST_PAGE_SIZE_WITH_PAGINATION
            : LIST_PAGE_SIZE_WITHOUT_PAGINATION;
    }

    private int calcularTotalPaginas(int total, int pageSize) {
        return Math.max(1, (int) Math.ceil(total / (double) pageSize));
    }

    private String montarLabelPagina(int paginaAtual, int paginasTotal) {
        return paginasTotal > 1
            ? "Página " + paginaAtual + " de " + paginasTotal
            : "Página 1";
    }

    private int contarProdutosDaCategoria(Estabelecimento estabelecimento, Long idCategoria) {

        return produtoService.listar(
            estabelecimento.getId(),
            idCategoria,
            null,
            false
        ).size();
    }

    private void validarIdCategoria(Long idCategoria) {

        if (idCategoria == null || idCategoria <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }
    }

    private void validarCategoriaDoEstabelecimento(
        Estabelecimento estabelecimento,
        CategoriaProduto categoria
    ) {

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
}