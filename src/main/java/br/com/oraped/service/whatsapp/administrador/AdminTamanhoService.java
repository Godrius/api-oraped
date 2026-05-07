package br.com.oraped.service.whatsapp.administrador;

import java.math.BigDecimal;
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
import br.com.oraped.domain.produto.tamanho.OpcaoTamanho;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoProdutoRequestDTO;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoProdutoResponseDTO;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoRequestDTO;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.produto.CategoriaProdutoService;
import br.com.oraped.service.produto.ProdutoService;
import br.com.oraped.service.produto.tamanho.GradeTamanhoService;
import br.com.oraped.service.produto.tamanho.OpcaoTamanhoProdutoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminProdutoService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminTamanhoService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Concentrar a administração de tamanhos do cardápio via WhatsApp.
 *
 * Aplicação:
 * - ativa/desativa tamanhos em uma categoria
 * - cadastra e gerencia opções de tamanho
 * - altera descrição das opções de tamanho
 * - configura preço de produto por tamanho
 *
 * Utilização:
 * Deve ser chamado pelo roteamento administrativo de tamanhos.
 */
@Service
@RequiredArgsConstructor
public class AdminTamanhoService {

    private final ProdutoService produtoService;
    private final CategoriaProdutoService categoriaProdutoService;
    private final GradeTamanhoService gradeTamanhoService;
    private final OpcaoTamanhoProdutoService opcaoTamanhoProdutoService;
    
    private final AdminWhatsappUiHelper uiHelper;
    private final AdminCategoriaService adminCategoriaService;
    
    private final SessaoWhatsappAdminTamanhoService sessaoAdminTamanhoService;
    private final SessaoWhatsappAdminProdutoService sessaoAdminProdutoService;
    
    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuTamanhosCategoria(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idCategoria,
	    Integer offsetProdutos
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);
	    validarIdCategoria(idCategoria);

	    int safeOffset = normalizarOffset(offsetProdutos);

	    CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
	    validarCategoriaDoEstabelecimento(estabelecimento, categoria);

	    boolean categoriaUsaGrade = gradeTamanhoService.categoriaPossuiGradeAtiva(categoria.getId());
	    String nomeCategoria = uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80);

	    if (!categoriaUsaGrade) {
	        String corpo =
	            "📏 *Tamanhos da categoria*\n\n" +
	                "*" + nomeCategoria + "*\n\n" +
	                "Atualmente esta categoria *NÃO* oferece opções de tamanhos.";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_cat_tamanhos_menu_sem_grade",
	            uiHelper.msg().botoes(
	                whatsappAdmin,
	                uiHelper.msg().trunc(corpo, 1024),
	                List.of(
	                    uiHelper.btn(
	                        "COMANDO|ADMIN_TAM_OPCAO_NOVA_MENU|" + idCategoria + "|" + safeOffset,
	                        "Cadastrar tamanhos"
	                    ),
	                    uiHelper.btn(
	                        "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + safeOffset,
	                        "Voltar"
	                    )
	                )
	            )
	        );
	    }

	    var gradeResp = gradeTamanhoService.buscarGradeDaCategoria(idCategoria);

	    List<OpcaoTamanhoResponseDTO> tamanhos = gradeResp == null
	        ? List.of()
	        : gradeTamanhoService.listarOpcoes(gradeResp.getIdGrade(), false);

	    String corpo =
	        "📏 *Tamanhos da categoria*\n\n" +
	            "*" + nomeCategoria + "*\n\n" +
	            "Atualmente esta categoria *OFERECE* as seguintes opções de tamanhos:\n\n" +
	            montarTextoTamanhosCadastrados(tamanhos, null);

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CAT_TAMANHOS_DESATIVAR|" + idCategoria + "|" + safeOffset,
	        "Desativar tamanhos",
	        "Produto volta ao preço único"
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_TAM_OPCAO_NOVA_MENU|" + idCategoria + "|" + safeOffset,
	        "Cadastrar novo tamanho",
	        "Adicionar nova opção"
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CAT_TAMANHOS_OPCOES|" + idCategoria + "|" + safeOffset + "|0",
	        "Tamanhos cadastrados",
	        "Ver e gerenciar opções"
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + safeOffset,
	        "⬅️ Voltar",
	        "Menu da categoria"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cat_tamanhos_menu",
	        uiHelper.msg().lista(
	            whatsappAdmin,
	            uiHelper.msg().truncWord(corpo, 1024),
	            "Opções",
	            "Opções",
	            itens
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin ativarTamanhosCategoria(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idCategoria,
        Integer offsetProdutos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);
        validarIdCategoria(idCategoria);

        int safeOffset = normalizarOffset(offsetProdutos);

        CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
        validarCategoriaDoEstabelecimento(estabelecimento, categoria);

        gradeTamanhoService.associarGradeUnicaACategoria(estabelecimento, categoria.getId());

        String corpo =
            "✅ Tamanhos ativados.\n\n" +
                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
                "A partir de agora, os produtos desta categoria usarão a grade de tamanhos da loja.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cat_tamanhos_ativados",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn(
                        "COMANDO|ADMIN_CAT_TAMANHOS_MENU|" + idCategoria + "|" + safeOffset,
                        "📏 Ver tamanhos"
                    ),
                    uiHelper.btn(
                        "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + safeOffset,
                        "⬅️ Voltar"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin desativarTamanhosCategoria(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idCategoria,
        Integer offsetProdutos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);
        validarIdCategoria(idCategoria);

        int safeOffset = normalizarOffset(offsetProdutos);

        CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
        validarCategoriaDoEstabelecimento(estabelecimento, categoria);

        gradeTamanhoService.desassociarGradeUnicaDaCategoria(estabelecimento, categoria.getId());

        String corpo =
            "✅ Tamanhos desativados.\n\n" +
                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
                "Os produtos desta categoria voltam a usar o preço próprio do cadastro do produto.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cat_tamanhos_desativados",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn(
                        "COMANDO|ADMIN_CAT_TAMANHOS_MENU|" + idCategoria + "|" + safeOffset,
                        "📏 Ver tamanhos"
                    ),
                    uiHelper.btn(
                        "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + safeOffset,
                        "⬅️ Voltar"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuOpcoesTamanhoCategoria(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idCategoria,
	    Integer offsetProdutos,
	    Integer offsetOpcoes,
	    String cabecalhoExtra
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);
	    validarIdCategoria(idCategoria);

	    int safeOffsetProdutos = normalizarOffset(offsetProdutos);

	    CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
	    validarCategoriaDoEstabelecimento(estabelecimento, categoria);

	    var gradeResp = gradeTamanhoService.buscarGradeDaCategoria(idCategoria);

	    if (gradeResp == null) {
	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_cat_tamanhos_sem_grade",
	            uiHelper.msg().botoes(
	                whatsappAdmin,
	                "Esta categoria ainda não possui tamanhos ativos.\n\nAtive os tamanhos primeiro.",
	                List.of(
	                    uiHelper.btn(
	                        "COMANDO|ADMIN_CAT_TAMANHOS_ATIVAR|" + idCategoria + "|" + safeOffsetProdutos,
	                        "Ativar tamanhos"
	                    ),
	                    uiHelper.btn(
	                        "COMANDO|ADMIN_CAT_TAMANHOS_MENU|" + idCategoria + "|" + safeOffsetProdutos,
	                        "⬅️ Voltar"
	                    )
	                )
	            )
	        );
	    }

	    List<OpcaoTamanhoResponseDTO> opcoes =
	        gradeTamanhoService.listarOpcoes(gradeResp.getIdGrade(), false);

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    for (OpcaoTamanhoResponseDTO opcao : opcoes) {
	        String status = opcao.isAtivo() ? "Ativo" : "Inativo";

	        itens.add(
	            MensagemInterativaItemListaWhatsappDTO.builder()
	                .id("COMANDO|ADMIN_TAM_OPCAO_MENU|" + opcao.getIdOpcaoTamanho() + "|" + idCategoria + "|" + safeOffsetProdutos)
	                .title(uiHelper.msg().trunc(opcao.getNome(), 24))
	                .description(uiHelper.msg().trunc(status, 72))
	                .build()
	        );
	    }

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_TAM_OPCAO_NOVA_MENU|" + idCategoria + "|" + safeOffsetProdutos,
	        "➕ Novo tamanho",
	        "Cadastrar P, M, G..."
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CAT_TAMANHOS_MENU|" + idCategoria + "|" + safeOffsetProdutos,
	        "⬅️ Voltar",
	        "Tamanhos da categoria"
	    ));

	    String corpo = "";

	    // Permite reutilizar a mesma lista exibindo mensagens de confirmação no topo.
	    if (StringUtils.hasText(cabecalhoExtra)) {
	        corpo += cabecalhoExtra.trim() + "\n\n";
	    }

	    corpo +=
	        "📏 *Opções de tamanho*\n\n" +
	            "*" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cat_tamanhos_opcoes",
	        uiHelper.msg().lista(
	            whatsappAdmin,
	            uiHelper.msg().truncWord(corpo, 1024),
	            "Tamanhos",
	            "Tamanhos",
	            itens
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuOpcaoTamanho(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idOpcao,
        Long idCategoria,
        Integer offsetProdutos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = normalizarOffset(offsetProdutos);

        OpcaoTamanho opcao = validarContextoOpcaoTamanho(
            estabelecimento,
            idOpcao,
            idCategoria
        );

        String status = opcao.isAtivo() ? "Ativo" : "Inativo";

        String corpo =
            "📏 *" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "*\n\n" +
                "*Status:* " + status + "\n\n" +
                "Esta opção representa apenas o tamanho.\n\n" +
                "O preço será configurado individualmente em cada produto.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        if (opcao.isAtivo()) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_TAM_OPCAO_DESATIVAR|" + idOpcao + "|" + idCategoria + "|" + safeOffset,
                "Desativar",
                "Ocultar tamanho"
            ));
        } else {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_TAM_OPCAO_ATIVAR|" + idOpcao + "|" + idCategoria + "|" + safeOffset,
                "Ativar",
                "Disponibilizar tamanho"
            ));
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_TAM_OPCAO_DESC_MENU|" + idOpcao + "|" + idCategoria + "|" + safeOffset,
            "Editar descrição",
            "Medidas, porções ou detalhes"
        ));

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CAT_TAMANHOS_OPCOES|" + idCategoria + "|" + safeOffset + "|0",
            "⬅️ Voltar",
            "Lista de tamanhos"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_tam_opcao_menu",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Opção",
                "Opção",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroOpcaoTamanho(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Long idCategoria,
	    Integer offsetProdutos
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);
	    validarIdCategoria(idCategoria);

	    int safeOffset = normalizarOffset(offsetProdutos);

	    CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
	    validarCategoriaDoEstabelecimento(estabelecimento, categoria);

	    if (!gradeTamanhoService.categoriaPossuiGradeAtiva(categoria.getId())) {
	        gradeTamanhoService.associarGradeUnicaACategoria(estabelecimento, categoria.getId());
	    }

	    var grade = gradeTamanhoService.buscarGradeDaCategoria(categoria.getId());

	    List<OpcaoTamanhoResponseDTO> tamanhosCadastrados = grade == null
	        ? List.of()
	        : gradeTamanhoService.listarOpcoes(grade.getIdGrade(), false);

	    sessaoAdminTamanhoService.marcarAguardandoNovaOpcaoTamanho(
	        idSessao,
	        categoria.getId(),
	        safeOffset
	    );

	    String tamanhosAtuais = montarTextoTamanhosCadastrados(
    	    tamanhosCadastrados,
    	    null
    	);

	    String corpo =
	        "➕ *Novo tamanho da categoria*\n\n" +
	            "*" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
	            tamanhosAtuais +
	            "\n\n" +
	            "Digite apenas o *nome do novo tamanho*.\n\n" +
	            "Exemplos:\n" +
	            "- Pequena\n" +
	            "- Média\n" +
	            "- Grande\n" +
	            "- Família\n\n" +
	            "O preço será definido depois, individualmente em cada produto.";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
    	    "admin_tam_opcao_nova",
    	    uiHelper.msg().botoes(
    	        whatsappAdmin,
    	        uiHelper.msg().trunc(corpo, 1024),
    	        List.of(
	        		uiHelper.btn(
        			    "COMANDO|ADMIN_TAM_OPCAO_CANCELAR|" + idCategoria + "|" + safeOffset,
        			    "⬅️ Cancelar"
        			)
    	        )
    	    )
    	);
	}

    
    private String montarTextoTamanhosCadastrados(
	    List<OpcaoTamanhoResponseDTO> tamanhos,
	    String nomeNovoTamanho
	) {

	    if (tamanhos == null || tamanhos.isEmpty()) {
	        return "*Tamanhos cadastrados:*\nNenhum tamanho cadastrado ainda.";
	    }

	    StringBuilder sb = new StringBuilder();
	    sb.append("*Tamanhos cadastrados:*\n");

	    // Centraliza a renderização da lista para evitar divergência visual entre fluxos.
	    for (OpcaoTamanhoResponseDTO tamanho : tamanhos) {

	        boolean tamanhoNovo =
	            StringUtils.hasText(nomeNovoTamanho)
	                && nomeNovoTamanho.equalsIgnoreCase(
	                    uiHelper.msg().safe(tamanho.getNome())
	                );

	        String status = tamanho.isAtivo() ? "" : " _(inativo)_";

	        if (tamanhoNovo) {
	            sb.append("- *")
	                .append(uiHelper.msg().trunc(uiHelper.msg().safe(tamanho.getNome()), 60))
	                .append(" (novo)*")
	                .append(status);
	        } else {
	            sb.append("- ")
	                .append(uiHelper.msg().trunc(uiHelper.msg().safe(tamanho.getNome()), 60))
	                .append(status);
	        }

	        sb.append("\n");
	    }

	    return sb.toString().trim();
	}
    
    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroOpcaoTamanho(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String texto
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        if (!StringUtils.hasText(texto)) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_tam_opcao_invalido",
                uiHelper.msg().texto(
                    whatsappAdmin,
                    "Não consegui identificar o tamanho.\n\nExemplo: *Média*"
                )
            );
        }

        String nome = texto.trim();

        Long idCategoria = sessaoAdminTamanhoService.getCategoriaOpcaoTamanho(idSessao);
        Integer offsetProdutos = sessaoAdminTamanhoService.getOffsetProdutosOpcaoTamanho(idSessao);

        CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
        validarCategoriaDoEstabelecimento(estabelecimento, categoria);

        var grade = gradeTamanhoService.buscarGradeDaCategoria(idCategoria);

        if (grade == null) {
            gradeTamanhoService.associarGradeUnicaACategoria(estabelecimento, idCategoria);
            grade = gradeTamanhoService.buscarGradeDaCategoria(idCategoria);
        }

        // A opção define apenas o tamanho; preço será configurado por produto.
        gradeTamanhoService.salvarOpcao(
            null,
            new OpcaoTamanhoRequestDTO(
                grade.getIdGrade(),
                nome,
                1,
                true
            )
        );

        sessaoAdminTamanhoService.limparAguardandoNovaOpcaoTamanho(idSessao);

        List<OpcaoTamanhoResponseDTO> tamanhosAtualizados =
    	    gradeTamanhoService.listarOpcoes(grade.getIdGrade(), false);

    	String corpo =
    	    "✅ Tamanho *" + uiHelper.msg().trunc(uiHelper.msg().safe(nome), 80) + "* cadastrado.\n\n" +
    	        montarTextoTamanhosCadastrados(
    	            tamanhosAtualizados,
    	            nome
    	        );

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_tam_opcao_cadastrada",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn(
                        "COMANDO|ADMIN_TAM_OPCAO_NOVA_MENU|" + idCategoria + "|" + normalizarOffset(offsetProdutos),
                        "➕ Novo tamanho"
                    ),
                    uiHelper.btn(
                        "COMANDO|ADMIN_CAT_TAMANHOS_MENU|" + idCategoria + "|" + normalizarOffset(offsetProdutos),
                        "⬅️ Voltar"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin ativarOpcaoTamanho(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idOpcao,
	    Long idCategoria,
	    Integer offsetProdutos
	) {

	    OpcaoTamanho opcao = validarContextoOpcaoTamanho(
	        estabelecimento,
	        idOpcao,
	        idCategoria
	    );

	    int safeOffset = normalizarOffset(offsetProdutos);

	    // Reativa o tamanho sem alterar preços já configurados nos produtos.
	    gradeTamanhoService.atualizarStatusOpcao(idOpcao, true);

	    return montarMenuOpcoesTamanhoCategoria(
	        estabelecimento,
	        whatsappAdmin,
	        idCategoria,
	        safeOffset,
	        0,
	        "✅ Tamanho ativado.\n\n" +
	            "*" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "* voltou a aparecer para seleção nos pedidos."
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin desativarOpcaoTamanho(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idOpcao,
	    Long idCategoria,
	    Integer offsetProdutos
	) {

	    OpcaoTamanho opcao = validarContextoOpcaoTamanho(
	        estabelecimento,
	        idOpcao,
	        idCategoria
	    );

	    int safeOffset = normalizarOffset(offsetProdutos);

	    // O tamanho permanece cadastrado, apenas deixa de aparecer nos pedidos.
	    gradeTamanhoService.atualizarStatusOpcao(idOpcao, false);

	    return montarMenuOpcoesTamanhoCategoria(
	        estabelecimento,
	        whatsappAdmin,
	        idCategoria,
	        safeOffset,
	        0,
	        "✅ Tamanho desativado.\n\n" +
	            "*" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "* não aparecerá mais para seleção nos pedidos."
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoDescricaoOpcaoTamanho(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idOpcao,
        Long idCategoria,
        Integer offsetProdutos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = normalizarOffset(offsetProdutos);

        OpcaoTamanho opcao = validarContextoOpcaoTamanho(
            estabelecimento,
            idOpcao,
            idCategoria
        );

        sessaoAdminTamanhoService.marcarAguardandoDescricaoOpcaoTamanho(
            idSessao,
            idOpcao,
            idCategoria,
            safeOffset
        );

        String descricaoAtual = uiHelper.msg().safe(opcao.getDescricao());

        if (!StringUtils.hasText(descricaoAtual)) {
            descricaoAtual = "Sem descrição cadastrada.";
        }

        String corpo =
            "📝 *Descrição do tamanho*\n\n" +
                "Tamanho: *" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "*\n\n" +
                "Descrição atual:\n" +
                uiHelper.msg().trunc(descricaoAtual, 500) + "\n\n" +
                "Digite a nova descrição.\n\n" +
                "Exemplos:\n" +
                "- Serve 1 pessoa\n" +
                "- 25 cm\n" +
                "- 8 fatias";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_tam_opcao_descricao_digitacao",
            uiHelper.msg().texto(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024)
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoDescricaoOpcaoTamanho(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String texto
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        if (!StringUtils.hasText(texto)) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_tam_opcao_descricao_invalida",
                uiHelper.msg().texto(
                    whatsappAdmin,
                    "Não consegui identificar a descrição.\n\nExemplo: *25 cm, serve 1 pessoa*."
                )
            );
        }

        Long idOpcao = sessaoAdminTamanhoService.getIdOpcaoTamanhoNovaDescricao(idSessao);
        Long idCategoria = sessaoAdminTamanhoService.getIdCategoriaOpcaoTamanhoNovaDescricao(idSessao);
        Integer offsetProdutos = sessaoAdminTamanhoService.getOffsetProdutosOpcaoTamanhoNovaDescricao(idSessao);

        OpcaoTamanho opcao = validarContextoOpcaoTamanho(
            estabelecimento,
            idOpcao,
            idCategoria
        );

        String descricaoAtualizada = texto.trim();

        gradeTamanhoService.atualizarDescricaoOpcao(
            idOpcao,
            descricaoAtualizada
        );

        sessaoAdminTamanhoService.limparAguardandoDescricaoOpcaoTamanho(idSessao);

        String corpo =
            "✅ Descrição atualizada.\n\n" +
                "Tamanho: *" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "*\n\n" +
                "Nova descrição:\n" +
                uiHelper.msg().trunc(descricaoAtualizada, 500);

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_tam_opcao_descricao_atualizada",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn(
                        "COMANDO|ADMIN_TAM_OPCAO_MENU|" + idOpcao + "|" + idCategoria + "|" + offsetProdutos,
                        "📏 Ver tamanho"
                    ),
                    uiHelper.btn(
                        "COMANDO|ADMIN_CAT_TAMANHOS_OPCOES|" + idCategoria + "|" + offsetProdutos + "|0",
                        "⬅️ Lista"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuPrecosTamanhoProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetProdutos,
	    Integer offsetTamanhos,
	    String mensagemCabecalho
	) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffsetProdutos = normalizarOffset(offsetProdutos);

        Produto produto = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, produto);
        validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

        if (!gradeTamanhoService.categoriaPossuiGradeAtiva(idCategoria)) {
            String corpo =
                "⚠️ Esta categoria não usa tamanhos.\n\n" +
                    "Ative os tamanhos na categoria antes de configurar preços por tamanho.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_prod_tamanhos_sem_grade",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    uiHelper.msg().trunc(corpo, 1024),
                    List.of(
                        uiHelper.btn(
                            "COMANDO|ADMIN_CAT_TAMANHOS_MENU|" + idCategoria + "|" + safeOffsetProdutos,
                            "📏 Tamanhos"
                        ),
                        uiHelper.btn(
                            "COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + idCategoria + "|" + safeOffsetProdutos,
                            "⬅️ Voltar"
                        )
                    )
                )
            );
        }

        var gradeCategoria = gradeTamanhoService.buscarGradeDaCategoria(idCategoria);

        List<OpcaoTamanhoResponseDTO> opcoes = gradeTamanhoService.listarOpcoes(
            gradeCategoria.getIdGrade(),
            false
        );

        List<OpcaoTamanhoProdutoResponseDTO> precosConfigurados =
            opcaoTamanhoProdutoService.listarPorProduto(idProduto, false);

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (OpcaoTamanhoResponseDTO opcao : opcoes) {
            OpcaoTamanhoProdutoResponseDTO precoProduto = buscarPrecoProdutoTamanho(
                precosConfigurados,
                opcao.getIdOpcaoTamanho()
            );

            String descricao = precoProduto == null
                ? "Preço não configurado"
                : uiHelper.msg().formatarMoeda(precoProduto.getPreco());

            if (!opcao.isAtivo()) {
                descricao = "Tamanho inativo";
            }

            itens.add(
                MensagemInterativaItemListaWhatsappDTO.builder()
                    .id(
                        "COMANDO|ADMIN_PROD_TAM_PRECO_MENU|" +
                            idProduto + "|" +
                            idCategoria + "|" +
                            opcao.getIdOpcaoTamanho() + "|" +
                            safeOffsetProdutos
                    )
                    .title(uiHelper.msg().trunc(opcao.getNome(), 24))
                    .description(uiHelper.msg().trunc(descricao, 72))
                    .build()
            );
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + idCategoria + "|" + safeOffsetProdutos,
            "⬅️ Voltar",
            "Ações do produto"
        ));

        String corpo =
    	    (StringUtils.hasText(mensagemCabecalho)
    	        ? mensagemCabecalho.trim() + "\n\n"
    	        : "") +
    	    "💰 *Preços por tamanho*\n\n" +
    	        "*" + uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80) + "*\n\n" +
    	        "Escolha um tamanho para definir o preço deste produto.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_tamanhos_precos",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Tamanhos",
                "Tamanhos",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoPrecoTamanhoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Long idOpcaoTamanho,
        Integer offsetProdutos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = normalizarOffset(offsetProdutos);

        Produto produto = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, produto);
        validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

        OpcaoTamanho opcao = validarContextoOpcaoTamanho(
            estabelecimento,
            idOpcaoTamanho,
            idCategoria
        );

        OpcaoTamanhoProdutoResponseDTO precoAtual =
            opcaoTamanhoProdutoService.buscarPorProdutoEOpcao(idProduto, idOpcaoTamanho);

        sessaoAdminTamanhoService.marcarAguardandoNovoPrecoProdutoTamanho(
            idSessao,
            idProduto,
            idCategoria,
            idOpcaoTamanho,
            safeOffset
        );

        String valorAtual = precoAtual == null
            ? "Não configurado"
            : uiHelper.msg().formatarMoeda(precoAtual.getPreco());

        String corpo =
            "💰 *Preço por tamanho*\n\n" +
                "Produto: *" + uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80) + "*\n" +
                "Tamanho: *" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "*\n" +
                "Preço atual: *" + valorAtual + "*\n\n" +
                "Digite o novo preço.\n\n" +
                "Exemplos:\n" +
                "- 39,90\n" +
                "- 42";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_tamanho_preco_digitacao",
            uiHelper.msg().texto(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024)
            )
        );
    }

    // Após salvar um preço por tamanho no cadastro guiado, tenta avançar para o próximo tamanho sem preço.
    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoPrecoTamanhoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String texto
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        if (!StringUtils.hasText(texto)) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_prod_tamanho_preco_invalido",
                uiHelper.msg().texto(
                    whatsappAdmin,
                    "Não consegui identificar o preço.\n\nExemplo: *39,90*"
                )
            );
        }

        BigDecimal novoPreco;

        try {
            String valor = texto.trim()
                .replace("R$", "")
                .replace("r$", "")
                .replace(" ", "")
                .replace(",", ".");

            novoPreco = new BigDecimal(valor);
        } catch (NumberFormatException ex) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_prod_tamanho_preco_invalido",
                uiHelper.msg().texto(
                    whatsappAdmin,
                    "Preço inválido.\n\nExemplo: *39,90*"
                )
            );
        }

        Long idProduto = sessaoAdminTamanhoService.getIdProdutoNovoPrecoTamanho(idSessao);
        Long idCategoria = sessaoAdminTamanhoService.getIdCategoriaNovoPrecoTamanho(idSessao);
        Long idOpcaoTamanho = sessaoAdminTamanhoService.getIdOpcaoTamanhoProdutoNovoPreco(idSessao);
        Integer offsetProdutos = sessaoAdminTamanhoService.getOffsetListaNovoPrecoTamanho(idSessao);

        Produto produto = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, produto);
        validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

        OpcaoTamanho opcao = validarContextoOpcaoTamanho(
            estabelecimento,
            idOpcaoTamanho,
            idCategoria
        );

        opcaoTamanhoProdutoService.salvarPrecoProdutoTamanho(
            new OpcaoTamanhoProdutoRequestDTO(
                idProduto,
                idOpcaoTamanho,
                novoPreco,
                true
            )
        );

        sessaoAdminTamanhoService.limparAguardandoNovoPrecoProdutoTamanho(idSessao);

        boolean cadastroGuiado = sessaoAdminProdutoService.isCadastroGuiadoProduto(idSessao);

        if (cadastroGuiado) {
            var grade = gradeTamanhoService.buscarGradeDaCategoria(idCategoria);

            List<OpcaoTamanhoResponseDTO> opcoesAtivas = gradeTamanhoService.listarOpcoes(
                grade.getIdGrade(),
                true
            );

            List<OpcaoTamanhoProdutoResponseDTO> precosConfigurados =
                opcaoTamanhoProdutoService.listarPorProduto(idProduto, false);

            // No cadastro guiado, cada tamanho ativo precisa receber preço antes de concluir o produto.
            for (OpcaoTamanhoResponseDTO opcaoAtiva : opcoesAtivas) {
                OpcaoTamanhoProdutoResponseDTO precoConfigurado = buscarPrecoProdutoTamanho(
                    precosConfigurados,
                    opcaoAtiva.getIdOpcaoTamanho()
                );

                if (precoConfigurado == null) {
                    return iniciarAlteracaoPrecoTamanhoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        idCategoria,
                        opcaoAtiva.getIdOpcaoTamanho(),
                        offsetProdutos
                    );
                }
            }

            // Se todos os tamanhos ativos já possuem preço, o cadastro guiado do produto está completo.
            sessaoAdminProdutoService.limparCadastroGuiadoProduto(idSessao);

            return adminCategoriaService.montarListaProdutosPorCategoria(
                estabelecimento,
                whatsappAdmin,
                idCategoria,
                offsetProdutos
            );
        }

        String mensagemCabecalho =
    	    "✅ Preço do tamanho atualizado.\n\n" +
    	        "Produto: *" + uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80) + "*\n" +
    	        "Tamanho: *" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "*\n" +
    	        "Novo preço: *" + uiHelper.msg().formatarMoeda(novoPreco) + "*";

    	// Após alteração individual, mantém o admin no contexto da lista de tamanhos do produto.
    	return montarMenuPrecosTamanhoProduto(
    	    estabelecimento,
    	    whatsappAdmin,
    	    idProduto,
    	    idCategoria,
    	    offsetProdutos,
    	    0,
    	    mensagemCabecalho
    	);
    }

    private OpcaoTamanhoProdutoResponseDTO buscarPrecoProdutoTamanho(
        List<OpcaoTamanhoProdutoResponseDTO> precos,
        Long idOpcaoTamanho
    ) {

        if (precos == null || idOpcaoTamanho == null) {
            return null;
        }

        return precos.stream()
            .filter(p -> Objects.equals(p.getIdOpcaoTamanho(), idOpcaoTamanho))
            .findFirst()
            .orElse(null);
    }

    private OpcaoTamanho validarContextoOpcaoTamanho(
        Estabelecimento estabelecimento,
        Long idOpcao,
        Long idCategoria
    ) {

        uiHelper.validarBasico(estabelecimento, "admin");
        validarIdCategoria(idCategoria);

        CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
        validarCategoriaDoEstabelecimento(estabelecimento, categoria);

        OpcaoTamanho opcao = gradeTamanhoService.buscarOpcaoObrigatoria(idOpcao);

        if (opcao.getGrade() == null || opcao.getGrade().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Opção de tamanho sem grade associada");
        }

        var gradeCategoria = gradeTamanhoService.buscarGradeDaCategoria(idCategoria);

        if (gradeCategoria == null || !Objects.equals(gradeCategoria.getIdGrade(), opcao.getGrade().getId())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Opção de tamanho não pertence à grade ativa da categoria"
            );
        }

        return opcao;
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

    private void validarIdCategoria(Long idCategoria) {

        if (idCategoria == null || idCategoria <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }
    }

    private int normalizarOffset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }
}