package br.com.oraped.service.whatsapp.administrador;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import br.com.oraped.repository.produto.CategoriaProdutoRepository;
import br.com.oraped.service.produto.CategoriaProdutoService;
import br.com.oraped.service.produto.ProdutoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminProdutoService;
import lombok.RequiredArgsConstructor;

/**
 * Responsável pela navegação administrativa do cardápio via WhatsApp.
 *
 * Aplicação:
 * - listar categorias do cardápio
 * - listar produtos por categoria
 * - montar tela de ações de um produto
 *
 * Utilização:
 * Deve ser chamado pelo roteamento administrativo quando o administrador estiver
 * navegando pela estrutura de cardápio. O menu hub "Revisar cardápio" permanece
 * em MenuAdminService.
 */
@Service
@RequiredArgsConstructor
public class AdminCardapioService {

    private static final int LIST_MAX_ROWS = 10;
    private static final int LIST_PAGE_SIZE_WITH_PAGINATION = 8;
    private static final int LIST_PAGE_SIZE_WITHOUT_PAGINATION = 9;

    private final ProdutoService produtoService;
    private final CategoriaProdutoService categoriaProdutoService;
    private final AdminWhatsappUiHelper uiHelper;
    private final SessaoWhatsappAdminProdutoService sessaoAdminProdutoService;
    
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    
    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuCardapioCategorias(
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
                    "Nenhuma categoria ativa foi encontrada.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_cardapio_categorias_vazio",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    uiHelper.msg().trunc(corpo, 1024),
                    List.of(
                        uiHelper.btn("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Voltar"),
                        uiHelper.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
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

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuCardapioProdutosPorCategoria(
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
                "🧾 *Produtos da categoria*\n\n" +
                    "*" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
                    "Nenhum produto foi encontrado nesta categoria.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_cardapio_categoria_produtos_vazio",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    uiHelper.msg().trunc(corpo, 1024),
                    List.of(
                        uiHelper.btn("COMANDO|ADMIN_CARDAPIO_CATEGORIAS_MENU|0", "⬅️ Voltar às categorias"),
                        uiHelper.btn("COMANDO|ADMIN_CARDAPIO_MENU", "🛠️ Cardápio")
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

        String nomeCategoria = uiHelper.msg().safe(categoria.getNome());

        String cabecalho =
            "🧾 *Produtos - " + uiHelper.msg().trunc(nomeCategoria, 60) + "*\n" +
                montarLabelPagina(paginaAtual, paginasTotal);

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (Produto produto : page) {
            itens.add(
                MensagemInterativaItemListaWhatsappDTO.builder()
                    .id(montarComandoVoltarProduto(produto.getId(), idCategoria, safeOffset))
                    .title(uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 24))
                    .description(montarDescricaoProdutoLista(produto))
                    .build()
            );
        }

        if (temMais) {
            int nextOffset = safeOffset + page.size();

            itens.add(uiHelper.row(
                "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + nextOffset,
                "➡️ Mais produtos",
                "Ver próxima página"
            ));
        }

     // botão de criação direta dentro da categoria (melhora UX: evita voltar fluxo)
        itens.add(uiHelper.row(
            "COMANDO|ADMIN_PRODUTO_NOVO_MENU|" + idCategoria + "|" + safeOffset,
            "➕ Novo produto",
            "Cadastrar nesta categoria"
        ));
        
        itens.add(uiHelper.row(
    	    "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + idCategoria + "|" + safeOffset,
    	    "Complementos",
    	    "Itens extras para adicionar nos pedidos"
    	));

        // navegação padrão
        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CARDAPIO_CATEGORIAS_MENU|" + safeOffset,
            "⬅️ Voltar",
            "Lista de categorias"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cardapio_categoria_produtos_menu",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(cabecalho, 1024),
                "Produtos",
                "Produtos",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuAcoesProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        validarIdCategoria(idCategoria);

        int safeOffset = normalizarOffset(offsetLista);

        Produto produto = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, produto);
        validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

        String nome = uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80);

        String descricao = uiHelper.msg().safe(produto.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String preco = uiHelper.msg().formatarMoeda(produto.getPreco());
        boolean temFoto = StringUtils.hasText(produto.getUrlFoto());
        String statusFoto = temFoto ? "Com foto cadastrada" : "Sem foto cadastrada";

        String cabecalho =
            "*" + nome + "*\n" +
                uiHelper.msg().trunc(descricao, 500) + "\n\n" +
                "*Preço atual:* " + preco + "\n" +
                "*Foto:* " + statusFoto + "\n\n" +
                "O que deseja fazer?";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_PROD_PRECO_MENU|" + idProduto + "|" + idCategoria + "|" + safeOffset,
            "Ajustar preço",
            "Incrementos ou informar valor"
        ));

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_PROD_NOME_MENU|" + idProduto + "|" + idCategoria + "|" + safeOffset,
            "Ajustar nome",
            "Enviar 1 mensagem com o novo nome"
        ));

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_PROD_DESC_MENU|" + idProduto + "|" + idCategoria + "|" + safeOffset,
            "Ajustar descrição",
            "Enviar 1 mensagem com a nova descrição"
        ));

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_PROD_FOTO_MENU|" + idProduto + "|" + idCategoria + "|" + safeOffset,
            "Atualizar foto",
            "Enviar 1 foto do produto"
        ));

        if (temFoto) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_PROD_FOTO_REMOVER_CONFIRM|" + idProduto + "|" + idCategoria + "|" + safeOffset,
                "Remover foto",
                "Apagar a foto atual do produto"
            ));
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_PROD_EXCLUIR_CONFIRM|" + idProduto + "|" + idCategoria + "|" + safeOffset,
            "Excluir produto",
            "Remover do cardápio"
        ));

        itens.add(uiHelper.row(
            montarComandoVoltarListaProdutos(idCategoria, safeOffset),
            "⬅️ Voltar",
            "Lista de produtos"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cardapio_produto_acoes",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(cabecalho, 1024),
                "Ações",
                "Ações",
                itens
            )
        );
    }

    private String montarDescricaoProdutoLista(Produto produto) {

        String preco = uiHelper.msg()
            .formatarMoeda(produto.getPreco())
            .replace("R$", "")
            .trim();

        String descricao = uiHelper.msg().safe(produto.getDescricao());
        String sufixoPreco = " ┃ 💰 " + preco;

        // O WhatsApp limita a descrição da linha da lista; o preço precisa ser preservado.
        int limiteTotal = 72;
        int limiteDescricao = Math.max(limiteTotal - sufixoPreco.length(), 0);

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
            ? descricaoFinal + sufixoPreco
            : "💰 " + preco;
    }

    private String montarComandoVoltarListaProdutos(Long idCategoria, Integer offsetLista) {

        int safeOffset = normalizarOffset(offsetLista);
        validarIdCategoria(idCategoria);

        return "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + safeOffset;
    }

    private String montarComandoVoltarProduto(Long idProduto, Long idCategoria, Integer offsetLista) {

        int safeOffset = normalizarOffset(offsetLista);
        validarIdCategoria(idCategoria);

        return "COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + idCategoria + "|" + safeOffset;
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

    private void validarIdCategoria(Long idCategoria) {

        if (idCategoria == null || idCategoria <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }
    }

    private int contarProdutosDaCategoria(Estabelecimento estabelecimento, Long idCategoria) {

        return produtoService.listar(
            estabelecimento.getId(),
            idCategoria,
            null,
            false
        ).size();
    }

    private void validarCategoriaDoProduto(
        Estabelecimento estabelecimento,
        Produto produto,
        Long idCategoria
    ) {

        validarIdCategoria(idCategoria);

        CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
        validarCategoriaDoEstabelecimento(estabelecimento, categoria);

        if (produto == null || produto.getCategoria() == null || produto.getCategoria().getId() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Produto não possui a categoria informada"
            );
        }

        if (!Objects.equals(produto.getCategoria().getId(), idCategoria)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Produto não pertence à categoria informada"
            );
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

    private void validarProdutoDoEstabelecimento(
        Estabelecimento estabelecimento,
        Produto produto
    ) {

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
	 // CATEGORIA: CRIAÇÃO POR DIGITAÇÃO
	 // =========================================================
	
	 public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroCategoriaPorDigitacao(
	     Estabelecimento estabelecimento,
	     String whatsappAdmin,
	     Long idSessao,
	     Integer offsetCategorias
	 ) {
	     uiHelper.validarBasico(estabelecimento, whatsappAdmin);
	
	     sessaoAdminProdutoService.marcarAguardandoNovaCategoria(
	         idSessao,
	         offsetCategorias
	     );
	
	     String corpo =
	         "➕ *Nova categoria de produtos*\n\n" +
	             "Digite o *nome da categoria*.\n\n" +
	             "Exemplos:\n" +
	             "- Açaí\n" +
	             "- Bebidas\n" +
	             "- Hambúrgueres";
	
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
	
	     if (categoriaProdutoRepository.existsByEstabelecimentoAndNomeIgnoreCase(estabelecimento, nome)) {
	         return new AdministradorWhatsappResultados.ResultadoAdmin(
	             "admin_categoria_nova_duplicada",
	             uiHelper.msg().texto(
	                 whatsappAdmin,
	                 "Já existe uma categoria com esse nome.\n\nEnvie outro nome ou toque em *MENU* para cancelar."
	             )
	         );
	     }
	
	     Integer offsetCategorias = sessaoAdminProdutoService.getOffsetListaNovaCategoria(idSessao);
	
	     CategoriaProduto categoria = new CategoriaProduto();
	     categoria.setEstabelecimento(estabelecimento);
	     categoria.setNome(nome);
	     categoria.setAtiva(true);
	
	     // A nova categoria entra no final visual da listagem, sem reordenar categorias existentes.
	     categoria.setOrdem(null);
	
	     categoriaProdutoRepository.save(categoria);
	
	     sessaoAdminProdutoService.limparAguardandoNovaCategoria(idSessao);
	
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
	                     "COMANDO|ADMIN_PRODUTO_NOVO_MENU|" + categoria.getId() + "|0",
	                     "➕ Novo produto"
	                 ),
	                 uiHelper.btn(
	                     "COMANDO|ADMIN_CARDAPIO_CATEGORIAS_MENU|" + offsetCategorias,
	                     "📂 Ver categorias"
	                 )
	             )
	         )
	     );
	 }
}