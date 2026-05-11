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
import br.com.oraped.domain.produto.tamanho.GradeTamanho;
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
 * Administrar tamanhos aplicados diretamente a produtos pelo WhatsApp.
 *
 * Aplicação:
 * - exibe variações de tamanho disponíveis para um produto
 * - configura preços do produto por tamanho
 * - conduz o cadastro guiado quando o produto usa grade de tamanhos
 *
 * Utilização:
 * Deve ser chamado pelo roteamento administrativo de produtos.
 */
@Service
@RequiredArgsConstructor
public class AdminTamanhoProdutoService {

    private final ProdutoService produtoService;
    private final CategoriaProdutoService categoriaProdutoService;
    private final GradeTamanhoService gradeTamanhoService;
    private final OpcaoTamanhoProdutoService opcaoTamanhoProdutoService;
    private final AdminCategoriaService adminCategoriaService;
    private final AdminWhatsappUiHelper uiHelper;
    private final SessaoWhatsappAdminTamanhoService sessaoAdminTamanhoService;
    private final SessaoWhatsappAdminProdutoService sessaoAdminProdutoService;

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuTamanhosProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetProdutos
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    int safeOffsetProdutos = normalizarOffset(offsetProdutos);

	    Produto produto = produtoService.buscar(idProduto);
	    validarProdutoDoEstabelecimento(estabelecimento, produto);
	    validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

	    GradeTamanho gradeProduto = gradeTamanhoService.buscarGradeAtivaDoProduto(idProduto);

	    List<OpcaoTamanhoResponseDTO> tamanhos = gradeProduto == null
	        ? List.of()
	        : gradeTamanhoService.listarOpcoes(gradeProduto.getId(), false);

	    List<OpcaoTamanhoProdutoResponseDTO> precosConfigurados =
	        opcaoTamanhoProdutoService.listarPorProduto(idProduto, false);

	    String corpo =
	        "📏 *Tamanhos exclusivos do produto*\n\n" +
	            "Produto: *" + uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80) + "*\n\n" +
	            "Escolha um tamanho para editar ou cadastre um novo.";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_PROD_TAMANHO_NOVO_MENU|" + idProduto + "|" + idCategoria + "|" + safeOffsetProdutos,
	        "➕ Novo tamanho",
	        "Criar tamanho só deste produto"
	    ));

	    for (OpcaoTamanhoResponseDTO tamanho : tamanhos) {
	        OpcaoTamanhoProdutoResponseDTO precoProduto = buscarPrecoProdutoTamanho(
	            precosConfigurados,
	            tamanho.getIdOpcaoTamanho()
	        );

	        String preco = precoProduto == null || precoProduto.getPreco() == null
	            ? "sem preço"
	            : uiHelper.msg().formatarMoeda(precoProduto.getPreco());

	        String status = tamanho.isAtivo() ? "Ativo" : "Inativo";

	        itens.add(uiHelper.row(
        	    "COMANDO|ADMIN_PROD_TAMANHO_MENU|" +
        	        idProduto + "|" +
        	        idCategoria + "|" +
        	        tamanho.getIdOpcaoTamanho() + "|" +
        	        safeOffsetProdutos,
        	    uiHelper.msg().trunc(tamanho.getNome(), 24),
        	    uiHelper.msg().trunc(status + " - " + preco, 72)
        	));
	    }

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + idCategoria + "|" + safeOffsetProdutos,
	        "⬅️ Voltar",
	        "Ações do produto"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_tamanhos_menu",
	        uiHelper.msg().lista(
	            whatsappAdmin,
	            uiHelper.msg().truncWord(corpo, 1024),
	            "Tamanhos",
	            "Opções",
	            itens
	        )
	    );
	}

    
    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuTamanhoProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Long idOpcaoTamanho,
	    Integer offsetProdutos,
	    String mensagemCabecalho
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    int safeOffsetProdutos = normalizarOffset(offsetProdutos);

	    Produto produto = produtoService.buscar(idProduto);
	    validarProdutoDoEstabelecimento(estabelecimento, produto);
	    validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

	    OpcaoTamanho opcao = validarContextoOpcaoTamanhoProduto(
	        estabelecimento,
	        produto,
	        idOpcaoTamanho
	    );

	    OpcaoTamanhoProdutoResponseDTO precoProduto =
	        opcaoTamanhoProdutoService.buscarPorProdutoEOpcao(idProduto, idOpcaoTamanho);

	    String preco = precoProduto == null || precoProduto.getPreco() == null
	        ? "Não configurado"
	        : uiHelper.msg().formatarMoeda(precoProduto.getPreco());

	    String status = opcao.isAtivo() ? "Ativo" : "Inativo";

	    String corpo =
	        (StringUtils.hasText(mensagemCabecalho)
	            ? mensagemCabecalho.trim() + "\n\n"
	            : "") +
	        "📏 *Tamanho do produto*\n\n" +
	            "Produto: *" + uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80) + "*\n" +
	            "Tamanho: *" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "*\n" +
	            "Preço: *" + preco + "*\n" +
	            "Status: *" + status + "*\n\n" +
	            "O que deseja fazer?";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_PROD_TAMANHO_NOME_MENU|" +
	            idProduto + "|" + idCategoria + "|" + idOpcaoTamanho + "|" + safeOffsetProdutos,
	        "✏️ Editar nome",
	        "Alterar nome do tamanho"
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_PROD_TAM_PRECO_MENU|" +
	            idProduto + "|" + idCategoria + "|" + idOpcaoTamanho + "|" + safeOffsetProdutos,
	        "💲 Editar preço",
	        "Alterar preço deste tamanho"
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_PROD_TAMANHO_" + (opcao.isAtivo() ? "DESATIVAR" : "ATIVAR") + "|" +
	            idProduto + "|" + idCategoria + "|" + idOpcaoTamanho + "|" + safeOffsetProdutos,
	        opcao.isAtivo() ? "⏸️ Desativar" : "▶️ Ativar",
	        opcao.isAtivo() ? "Ocultar este tamanho" : "Disponibilizar este tamanho"
	    ));

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_PROD_TAMANHOS_MENU|" + idProduto + "|" + idCategoria + "|" + safeOffsetProdutos,
	        "⬅️ Voltar",
	        "Lista de tamanhos"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_tamanho_menu",
	        uiHelper.msg().lista(
	            whatsappAdmin,
	            uiHelper.msg().truncWord(corpo, 1024),
	            "Tamanho",
	            "Ações",
	            itens
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

        GradeTamanho gradeAplicavel = gradeTamanhoService.buscarGradeAplicavelAoProduto(produto);

        if (gradeAplicavel == null) {
            String corpo =
                "⚠️ Este produto ainda não possui tamanhos cadastrados.\n\n" +
                    "Crie primeiro um tamanho exclusivo para este produto.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_prod_tamanhos_sem_grade",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    uiHelper.msg().trunc(corpo, 1024),
                    List.of(
                        uiHelper.btn(
                            "COMANDO|ADMIN_PROD_TAMANHO_NOVO_MENU|" + idProduto + "|" + idCategoria + "|" + safeOffsetProdutos,
                            "➕ Novo tamanho"
                        ),
                        uiHelper.btn(
                            "COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + idCategoria + "|" + safeOffsetProdutos,
                            "⬅️ Voltar"
                        )
                    )
                )
            );
        }

        List<OpcaoTamanhoResponseDTO> opcoes = gradeTamanhoService.listarOpcoes(
            gradeAplicavel.getId(),
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

    
    
    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoNomeTamanhoProdutoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Long idProduto,
	    Long idCategoria,
	    Long idOpcaoTamanho,
	    Integer offsetProdutos
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    if (idSessao == null) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
	    }

	    int safeOffsetProdutos = normalizarOffset(offsetProdutos);

	    Produto produto = produtoService.buscar(idProduto);
	    validarProdutoDoEstabelecimento(estabelecimento, produto);
	    validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

	    OpcaoTamanho opcao = validarContextoOpcaoTamanhoProduto(
	        estabelecimento,
	        produto,
	        idOpcaoTamanho
	    );

	    sessaoAdminTamanhoService.marcarAguardandoNovoNomeTamanhoProduto(
	        idSessao,
	        idProduto,
	        idCategoria,
	        idOpcaoTamanho,
	        safeOffsetProdutos
	    );

	    String corpo =
	        "✏️ *Editar nome do tamanho*\n\n" +
	            "Produto: *" + uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80) + "*\n" +
	            "Atual: *" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "*\n\n" +
	            "Digite o novo nome do tamanho.";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_tamanho_nome_digitacao",
	        uiHelper.msg().texto(
	            whatsappAdmin,
	            uiHelper.msg().trunc(corpo, 1024)
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoNomeTamanhoProdutoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String novoNome
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    if (idSessao == null) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
	    }

	    if (!StringUtils.hasText(novoNome)) {
	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_prod_tamanho_nome_invalido",
	            uiHelper.msg().texto(
	                whatsappAdmin,
	                "Não consegui identificar o novo nome do tamanho.\n\nEnvie apenas o nome.\n\nExemplo: *Grande*"
	            )
	        );
	    }

	    if (!sessaoAdminTamanhoService.isAguardandoNovoNomeTamanhoProduto(idSessao)) {
	        throw new ResponseStatusException(
	            HttpStatus.CONFLICT,
	            "Sessão não está aguardando novo nome do tamanho do produto"
	        );
	    }

	    Long idProduto = sessaoAdminTamanhoService.getIdProdutoNovoNomeTamanhoProduto(idSessao);
	    Long idCategoria = sessaoAdminTamanhoService.getIdCategoriaNovoNomeTamanhoProduto(idSessao);
	    Long idOpcaoTamanho = sessaoAdminTamanhoService.getIdOpcaoTamanhoNovoNomeProduto(idSessao);
	    Integer offsetProdutos = sessaoAdminTamanhoService.getOffsetListaNovoNomeTamanhoProduto(idSessao);

	    Produto produto = produtoService.buscar(idProduto);
	    validarProdutoDoEstabelecimento(estabelecimento, produto);
	    validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

	    OpcaoTamanho opcao = validarContextoOpcaoTamanhoProduto(
	        estabelecimento,
	        produto,
	        idOpcaoTamanho
	    );

	    String nomeLimpo = novoNome.trim();

	    OpcaoTamanhoRequestDTO dto = new OpcaoTamanhoRequestDTO();

	    dto.setIdGrade(opcao.getGrade().getId());
	    dto.setNome(nomeLimpo);
	    dto.setDescricao(opcao.getDescricao());
	    dto.setOrdem(opcao.getOrdem());
	    dto.setAtivo(opcao.isAtivo());

	    gradeTamanhoService.salvarOpcao(idOpcaoTamanho, dto);

	    sessaoAdminTamanhoService.limparAguardandoNovoNomeTamanhoProduto(idSessao);

	    String mensagemCabecalho =
	        "✅ Nome do tamanho atualizado.\n\n" +
	            "Produto: *" + uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80) + "*\n" +
	            "Tamanho: *" + uiHelper.msg().trunc(uiHelper.msg().safe(nomeLimpo), 80) + "*";

	    return montarMenuTamanhoProduto(
	        estabelecimento,
	        whatsappAdmin,
	        idProduto,
	        idCategoria,
	        idOpcaoTamanho,
	        offsetProdutos,
	        mensagemCabecalho
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

        OpcaoTamanho opcao = validarContextoOpcaoTamanhoProduto(
            estabelecimento,
            produto,
            idOpcaoTamanho
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

        OpcaoTamanho opcao = validarContextoOpcaoTamanhoProduto(
            estabelecimento,
            produto,
            idOpcaoTamanho
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
            GradeTamanho grade = gradeTamanhoService.buscarGradeAplicavelAoProduto(produto);

            if (grade == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto não possui grade de tamanhos ativa");
            }

            List<OpcaoTamanhoResponseDTO> opcoesAtivas = gradeTamanhoService.listarOpcoes(
                grade.getId(),
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
                offsetProdutos,
                null
            );
        }

        String mensagemCabecalho =
            "✅ Preço do tamanho atualizado.\n\n" +
                "Produto: *" + uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80) + "*\n" +
                "Tamanho: *" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "*\n" +
                "Novo preço: *" + uiHelper.msg().formatarMoeda(novoPreco) + "*";

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

    
    public AdministradorWhatsappResultados.ResultadoAdmin alterarStatusTamanhoProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Long idOpcaoTamanho,
	    Integer offsetProdutos,
	    boolean ativo
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    Produto produto = produtoService.buscar(idProduto);
	    validarProdutoDoEstabelecimento(estabelecimento, produto);
	    validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

	    OpcaoTamanho opcao = validarContextoOpcaoTamanhoProduto(
	        estabelecimento,
	        produto,
	        idOpcaoTamanho
	    );

	    gradeTamanhoService.atualizarStatusOpcao(idOpcaoTamanho, ativo);

	    String mensagemCabecalho =
	        (ativo ? "✅ Tamanho ativado." : "⏸️ Tamanho desativado.") + "\n\n" +
	            "Tamanho: *" + uiHelper.msg().trunc(uiHelper.msg().safe(opcao.getNome()), 80) + "*";

	    return montarMenuTamanhoProduto(
	        estabelecimento,
	        whatsappAdmin,
	        idProduto,
	        idCategoria,
	        idOpcaoTamanho,
	        offsetProdutos,
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

    
    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroTamanhoProdutoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetProdutos
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    if (idSessao == null) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
	    }

	    int safeOffsetProdutos = normalizarOffset(offsetProdutos);

	    Produto produto = produtoService.buscar(idProduto);
	    validarProdutoDoEstabelecimento(estabelecimento, produto);
	    validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

	    sessaoAdminTamanhoService.marcarAguardandoNovoTamanhoProduto(
	        idSessao,
	        idProduto,
	        idCategoria,
	        safeOffsetProdutos
	    );

	    String corpo =
	        "➕ *Novo tamanho do produto*\n\n" +
	            "Produto: *" + uiHelper.msg().trunc(uiHelper.msg().safe(produto.getNome()), 80) + "*\n\n" +
	            "Digite o nome do tamanho que será usado somente neste produto.\n\n" +
	            "Exemplos:\n" +
	            "- Pequena\n" +
	            "- Média\n" +
	            "- Grande\n" +
	            "- 500ml\n" +
	            "- 1 litro";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_tamanho_novo_digitacao",
	        uiHelper.msg().botoes(
	            whatsappAdmin,
	            uiHelper.msg().trunc(corpo, 1024),
	            List.of(
	                uiHelper.btn(
	                    "COMANDO|ADMIN_PROD_TAMANHOS_MENU|" + idProduto + "|" + idCategoria + "|" + safeOffsetProdutos,
	                    "⬅️ Cancelar"
	                )
	            )
	        )
	    );
	}

    
    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroTamanhoProdutoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String nomeTamanho
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    if (idSessao == null) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
	    }

	    if (!StringUtils.hasText(nomeTamanho)) {
	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_prod_tamanho_nome_invalido",
	            uiHelper.msg().texto(
	                whatsappAdmin,
	                "Não consegui identificar o nome do tamanho.\n\nEnvie apenas o nome.\n\nExemplo: *Grande*"
	            )
	        );
	    }

	    if (!sessaoAdminTamanhoService.isAguardandoNovoTamanhoProduto(idSessao)) {
	        throw new ResponseStatusException(
	            HttpStatus.CONFLICT,
	            "Sessão não está aguardando novo tamanho do produto"
	        );
	    }

	    Long idProduto = sessaoAdminTamanhoService.getIdProdutoNovoTamanhoProduto(idSessao);
	    Long idCategoria = sessaoAdminTamanhoService.getIdCategoriaNovoTamanhoProduto(idSessao);
	    Integer offsetProdutos = sessaoAdminTamanhoService.getOffsetListaNovoTamanhoProduto(idSessao);

	    Produto produto = produtoService.buscar(idProduto);
	    validarProdutoDoEstabelecimento(estabelecimento, produto);
	    validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

	    String nomeLimpo = nomeTamanho.trim();

	    // Cria o tamanho em uma grade exclusiva do produto, sem reaproveitar a grade da categoria.
	    OpcaoTamanho opcaoTamanho = gradeTamanhoService.criarOpcaoExclusivaProduto(
	        estabelecimento.getId(),
	        idProduto,
	        nomeLimpo
	    );

	    sessaoAdminTamanhoService.limparAguardandoNovoTamanhoProduto(idSessao);

	    // Após criar o tamanho, o próximo passo natural é definir o preço desse tamanho no produto.
	    return iniciarAlteracaoPrecoTamanhoProduto(
	        estabelecimento,
	        whatsappAdmin,
	        idSessao,
	        idProduto,
	        idCategoria,
	        opcaoTamanho.getId(),
	        offsetProdutos
	    );
	}

    private OpcaoTamanho validarContextoOpcaoTamanhoProduto(
        Estabelecimento estabelecimento,
        Produto produto,
        Long idOpcao
    ) {

        uiHelper.validarBasico(estabelecimento, "admin");

        OpcaoTamanho opcao = gradeTamanhoService.buscarOpcaoObrigatoria(idOpcao);

        if (opcao.getGrade() == null || opcao.getGrade().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Opção de tamanho sem grade associada");
        }

        GradeTamanho gradeAplicavel = gradeTamanhoService.buscarGradeAplicavelAoProduto(produto);

        if (gradeAplicavel == null || !Objects.equals(gradeAplicavel.getId(), opcao.getGrade().getId())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Opção de tamanho não pertence à grade ativa do produto"
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