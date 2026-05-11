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
import br.com.oraped.dto.produto.complemento.ComplementoRequestDTO;
import br.com.oraped.dto.produto.complemento.ComplementoResponseDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoRequestDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.repository.produto.CategoriaProdutoRepository;
import br.com.oraped.service.produto.complemento.GrupoComplementoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminComplementoService;
import lombok.RequiredArgsConstructor;

/**
 * Serviço administrativo para grupos de complementos das categorias.
 *
 * Aplicação:
 * - permite criar e gerenciar grupos de complementos próprios de uma categoria
 * - os grupos criados aqui são herdados pelos produtos da categoria
 * - não reutiliza grupos de outras categorias ou produtos
 */
@Service
@RequiredArgsConstructor
public class AdminComplementoCategoriaService {

	private record ComplementoCategoriaAdmin(
	    Long idGrupo,
	    ComplementoResponseDTO complemento
	) {
	}
	
    private static final int LIST_MAX_ROWS = 10;

    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final AdminWhatsappUiHelper uiHelper;
    
    private final GrupoComplementoService grupoComplementoService;
    private final SessaoWhatsappAdminComplementoService sessaoAdminComplementoService;
    
    public AdministradorWhatsappResultados.ResultadoAdmin listarCategoriasParaComplementos(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offsetCategorias
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        List<CategoriaProduto> categorias =
            categoriaProdutoRepository.findByEstabelecimentoIdAndAtivaTrueOrderByNomeAsc(estabelecimento.getId());

        if (categorias.isEmpty()) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_cat_comp_categorias_vazio",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    "🧩 *Complementos por categoria*\n\nNenhuma categoria ativa encontrada.",
                    List.of(
                        uiHelper.btn("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Cardápio"),
                        uiHelper.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                    )
                )
            );
        }

        int safeOffset = normalizarOffset(offsetCategorias);
        if (safeOffset >= categorias.size()) {
            safeOffset = 0;
        }

        int pageSize = categorias.size() > LIST_MAX_ROWS ? 8 : 9;
        int endExclusive = Math.min(safeOffset + pageSize, categorias.size());
        List<CategoriaProduto> page = categorias.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < categorias.size();

        String corpo =
            "🧩 *Complementos por categoria*\n\n" +
                "Escolha a categoria que receberá grupos de complementos.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (CategoriaProduto categoria : page) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + categoria.getId() + "|" + safeOffset,
                uiHelper.msg().trunc(categoria.getNome(), 24),
                "Configurar complementos"
            ));
        }

        if (temMais) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_CAT_COMP_CATEGORIAS|" + endExclusive,
                "➡️ Mais categorias",
                "Ver próxima página"
            ));
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CARDAPIO_MENU",
            "⬅️ Voltar",
            "Menu do cardápio"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cat_comp_categorias",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Categorias",
                "Categorias",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuComplementosCategoria(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idCategoria,
	    Integer offsetCategorias,
	    Integer offsetComplementos
	) {

	    CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);

	    List<ComplementoCategoriaAdmin> complementos = listarComplementosDaCategoria(idCategoria);

	    int safeOffsetComplementos = normalizarOffset(offsetComplementos);

	    if (safeOffsetComplementos >= complementos.size()) {
	        safeOffsetComplementos = 0;
	    }

	    int pageSize = complementos.size() > LIST_MAX_ROWS ? 7 : 8;
	    int endExclusive = Math.min(safeOffsetComplementos + pageSize, complementos.size());
	    List<ComplementoCategoriaAdmin> page = complementos.subList(safeOffsetComplementos, endExclusive);
	    boolean temMais = endExclusive < complementos.size();

	    String nomeCategoria = uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 60);

	    String corpo =
	        "🧩 *Complementos para " + nomeCategoria + "*\n\n" +
	            "Estes complementos serão oferecidos automaticamente para *todos os produtos desta categoria*.\n\n" +
	            "Se quiser criar complementos apenas para um produto específico, configure diretamente no produto.\n\n" +
	            (complementos.isEmpty()
	                ? "Nenhum complemento cadastrado ainda.\n\n"
	                : complementos.size() == 1
	                    ? "1 complemento cadastrado.\n\n"
	                    : complementos.size() + " complementos cadastrados.\n\n") +
	            "Escolha uma opção.";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CAT_COMP_GRUPO_NOVO|" +
	            idCategoria + "|" +
	            normalizarOffset(offsetCategorias),
	        "➕ Novo complemento",
	        "Disponível para toda a categoria"
	    ));

	    for (ComplementoCategoriaAdmin item : page) {
	        ComplementoResponseDTO complemento = item.complemento();

	        String status = complemento.isAtivo() ? "Ativo" : "Inativo";
	        String preco = uiHelper.msg().formatarMoeda(complemento.getPrecoAdicional());

	        itens.add(uiHelper.row(
        	    "COMANDO|ADMIN_COMP_COMPLEMENTO_DETALHE|" +
        	        item.idGrupo() + "|" +
        	        safeOffsetComplementos + "|" +
        	        complemento.getId(),
        	    uiHelper.msg().trunc(complemento.getNome(), 24),
        	    uiHelper.msg().trunc(status + " ┃ +" + preco, 72)
        	));
	    }

	    if (temMais) {
	        itens.add(uiHelper.row(
	            "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" +
	                idCategoria + "|" +
	                normalizarOffset(offsetCategorias) + "|" +
	                endExclusive,
	            "➡️ Mais opções",
	            "Ver próxima página"
	        ));
	    }

	    itens.add(uiHelper.row(
	        "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" +
	            idCategoria + "|" +
	            normalizarOffset(offsetCategorias),
	        "⬅️ Voltar",
	        "Menu da categoria"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cat_comp_menu",
	        uiHelper.msg().lista(
	            whatsappAdmin,
	            uiHelper.msg().truncWord(corpo, 1024),
	            "Complementos",
	            "Complementos",
	            itens
	        )
	    );
	}
    
    
    private CategoriaProduto buscarCategoriaValidada(Estabelecimento estabelecimento, Long idCategoria) {

        uiHelper.validarBasico(estabelecimento, "admin");

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        CategoriaProduto categoria = categoriaProdutoRepository.findById(idCategoria)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));

        if (categoria.getEstabelecimento() == null || categoria.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria sem estabelecimento associado");
        }

        if (!Objects.equals(categoria.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria não pertence ao estabelecimento");
        }

        return categoria;
    }

    
    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroGuiadoComplementoCategoria(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Long idCategoria,
	    Integer offsetCategorias
	) {

	    CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);

	    List<GrupoComplementoResponseDTO> grupos =
	        grupoComplementoService.listarGruposDaCategoria(idCategoria, true);

	    GrupoComplementoResponseDTO grupo;

	    if (grupos == null || grupos.isEmpty()) {
	        int ordem = grupoComplementoService
	            .listarGruposDaCategoria(idCategoria, false)
	            .size() + 1;

	        GrupoComplementoRequestDTO dto = new GrupoComplementoRequestDTO();
	        dto.setIdEstabelecimento(estabelecimento.getId());
	        dto.setIdCategoria(idCategoria);
	        dto.setNome("Complementos - " + categoria.getNome());
	        dto.setDescricao("Complementos disponíveis para a categoria " + categoria.getNome());
	        dto.setMinimoSelecoes(0);
	        dto.setMaximoSelecoes(1);
	        dto.setOrdem(ordem);
	        dto.setAtivo(true);

	        // O grupo técnico é criado automaticamente para manter a navegação simples.
	        grupo = grupoComplementoService.salvarGrupo(null, dto);
	    } else {
	        grupo = grupos.get(0);
	    }

	    sessaoAdminComplementoService.marcarAguardandoNovoComplementoCategoria(
    	    idSessao,
    	    idCategoria,
    	    grupo.getId(),
    	    normalizarOffset(offsetCategorias)
    	);

	    String corpo =
	        "➕ *Novo complemento para " + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
	            "Este complemento será oferecido para todos os produtos desta categoria.\n\n" +
	            "Digite o *nome do complemento*.\n\n" +
	            "Exemplos:\n" +
	            "- Borda recheada\n" +
	            "- Molho extra\n" +
	            "- Leite condensado\n" +
	            "- Granola";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cat_comp_novo_nome",
	        uiHelper.msg().botoes(
	            whatsappAdmin,
	            uiHelper.msg().trunc(corpo, 1024),
	            List.of(
	                uiHelper.btn(
	                    "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" +
	                        idCategoria + "|" +
	                        normalizarOffset(offsetCategorias),
	                    "⬅️ Cancelar"
	                )
	            )
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroGuiadoComplementoCategoria(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String texto
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    if (!sessaoAdminComplementoService.isAguardandoNovoComplementoCategoria(idSessao)) {
	        throw new ResponseStatusException(
	            HttpStatus.CONFLICT,
	            "Sessão não está aguardando cadastro de complemento da categoria"
	        );
	    }

	    String etapa = sessaoAdminComplementoService.getEtapaNovoComplementoCategoria(idSessao);
	    String valor = texto == null ? "" : texto.trim();

	    Long idCategoria = sessaoAdminComplementoService.getIdCategoriaNovoComplementoCategoria(idSessao);
	    Long idGrupo = sessaoAdminComplementoService.getIdGrupoNovoComplementoCategoria(idSessao);
	    int offsetCategorias = sessaoAdminComplementoService.getOffsetListaCategoriaNovoComplemento(idSessao);

	    CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);

	    if (SessaoWhatsappAdminComplementoService.ETAPA_PRODUTO_COMPLEMENTO_NOME.equals(etapa)) {

	        if (!StringUtils.hasText(valor)) {
	            return new AdministradorWhatsappResultados.ResultadoAdmin(
	                "admin_cat_comp_nome_invalido",
	                uiHelper.msg().texto(
	                    whatsappAdmin,
	                    "Não consegui identificar o nome do complemento.\n\nExemplo: *Borda recheada*"
	                )
	            );
	        }

	        sessaoAdminComplementoService.salvarNomeNovoComplementoCategoria(
	            idSessao,
	            uiHelper.msg().trunc(valor, 120)
	        );

	        String corpo =
	            "📝 *Descrição do complemento*\n\n" +
	                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n" +
	                "Complemento: *" + uiHelper.msg().trunc(valor, 80) + "*\n\n" +
	                "Agora envie uma *descrição curta*.\n\n" +
	                "Se não quiser descrição, envie: *sem descrição*";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_cat_comp_novo_descricao",
	            uiHelper.msg().texto(whatsappAdmin, uiHelper.msg().trunc(corpo, 1024))
	        );
	    }

	    if (SessaoWhatsappAdminComplementoService.ETAPA_PRODUTO_COMPLEMENTO_DESCRICAO.equals(etapa)) {

	        String descricao = "sem descrição".equalsIgnoreCase(valor)
	            ? ""
	            : uiHelper.msg().trunc(valor, 600);

	        sessaoAdminComplementoService.salvarDescricaoNovoComplementoCategoria(idSessao, descricao);

	        String corpo =
	            "💲 *Preço do complemento*\n\n" +
	                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n" +
	                "Complemento: *" + uiHelper.msg().trunc(uiHelper.msg().safe(sessaoAdminComplementoService.getNomeNovoComplementoCategoria(idSessao)), 80) + "*\n\n" +
	                "Agora envie o *preço adicional*.\n\n" +
	                "Exemplos:\n" +
	                "- 0\n" +
	                "- 2,50\n" +
	                "- 5";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_cat_comp_novo_preco",
	            uiHelper.msg().texto(whatsappAdmin, uiHelper.msg().trunc(corpo, 1024))
	        );
	    }

	    if (SessaoWhatsappAdminComplementoService.ETAPA_PRODUTO_COMPLEMENTO_PRECO.equals(etapa)) {

	        BigDecimal preco;

	        try {
	            String valorNormalizado = valor
	                .replace("R$", "")
	                .replace("r$", "")
	                .replace(" ", "")
	                .replace(",", ".");

	            preco = new BigDecimal(valorNormalizado).setScale(2, RoundingMode.HALF_UP);
	        } catch (Exception ex) {
	            return new AdministradorWhatsappResultados.ResultadoAdmin(
	                "admin_cat_comp_preco_invalido",
	                uiHelper.msg().texto(
	                    whatsappAdmin,
	                    "Preço inválido.\n\nExemplos válidos:\n- 0\n- 2,50\n- 5"
	                )
	            );
	        }

	        if (preco.compareTo(BigDecimal.ZERO) < 0) {
	            return new AdministradorWhatsappResultados.ResultadoAdmin(
	                "admin_cat_comp_preco_negativo",
	                uiHelper.msg().texto(
	                    whatsappAdmin,
	                    "O preço do complemento não pode ser negativo.\n\nExemplo: *2,50*"
	                )
	            );
	        }

	        sessaoAdminComplementoService.salvarPrecoNovoComplementoCategoria(idSessao, preco);

	        String corpo =
	            "📋 *Regra de consumo*\n\n" +
	                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n" +
	                "Complemento: *" + uiHelper.msg().trunc(uiHelper.msg().safe(sessaoAdminComplementoService.getNomeNovoComplementoCategoria(idSessao)), 80) + "*\n" +
	                "Preço adicional: *" + uiHelper.msg().formatarMoeda(preco) + "*\n\n" +
	                "Quantas vezes esse complemento pode ser adicionado em cada produto?\n\n" +
	                "Envie apenas um número.\n\n" +
	                "Exemplos:\n" +
	                "- 1\n" +
	                "- 2\n" +
	                "- 3";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_cat_comp_novo_regras",
	            uiHelper.msg().texto(whatsappAdmin, uiHelper.msg().trunc(corpo, 1024))
	        );
	    }

	    if (SessaoWhatsappAdminComplementoService.ETAPA_PRODUTO_COMPLEMENTO_REGRAS.equals(etapa)) {

	        Integer maximoSelecoes;

	        try {
	            maximoSelecoes = Integer.valueOf(valor.replaceAll("\\D", ""));
	        } catch (Exception ex) {
	            return new AdministradorWhatsappResultados.ResultadoAdmin(
	                "admin_cat_comp_regra_invalida",
	                uiHelper.msg().texto(
	                    whatsappAdmin,
	                    "Não consegui identificar a regra de consumo.\n\nEnvie apenas um número.\n\nExemplo: *1*"
	                )
	            );
	        }

	        if (maximoSelecoes == null || maximoSelecoes < 1) {
	            return new AdministradorWhatsappResultados.ResultadoAdmin(
	                "admin_cat_comp_regra_invalida",
	                uiHelper.msg().texto(
	                    whatsappAdmin,
	                    "A quantidade máxima precisa ser maior que zero.\n\nExemplo: *1*"
	                )
	            );
	        }

	        String nome = sessaoAdminComplementoService.getNomeNovoComplementoCategoria(idSessao);
	        String descricao = sessaoAdminComplementoService.getDescricaoNovoComplementoCategoria(idSessao);
	        BigDecimal preco = sessaoAdminComplementoService.getPrecoNovoComplementoCategoria(idSessao);

	        ComplementoRequestDTO dto = new ComplementoRequestDTO();
	        dto.setIdGrupo(idGrupo);
	        dto.setNome(nome);
	        dto.setDescricao(descricao);
	        dto.setPrecoAdicional(preco == null ? BigDecimal.ZERO : preco);
	        dto.setAtivo(true);

	        ComplementoResponseDTO complemento = grupoComplementoService.salvarComplemento(null, dto);

	        GrupoComplementoRequestDTO grupoDto = new GrupoComplementoRequestDTO();
	        grupoDto.setIdEstabelecimento(estabelecimento.getId());
	        grupoDto.setIdCategoria(idCategoria);
	        grupoDto.setNome("Complementos - " + categoria.getNome());
	        grupoDto.setDescricao("Complementos disponíveis para a categoria " + categoria.getNome());
	        grupoDto.setMinimoSelecoes(0);
	        grupoDto.setMaximoSelecoes(maximoSelecoes);
	        grupoDto.setOrdem(1);
	        grupoDto.setAtivo(true);

	        // A regra fica no grupo técnico da categoria, sem expor esse conceito ao admin.
	        grupoComplementoService.salvarGrupo(idGrupo, grupoDto);

	        sessaoAdminComplementoService.limparAguardandoNovoComplementoCategoria(idSessao);

	        String corpo =
	            "✅ Complemento cadastrado.\n\n" +
	                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n" +
	                "Complemento: *" + uiHelper.msg().trunc(uiHelper.msg().safe(complemento.getNome()), 80) + "*\n" +
	                "Preço adicional: *" + uiHelper.msg().formatarMoeda(complemento.getPrecoAdicional()) + "*\n" +
	                "Máximo por item: *" + maximoSelecoes + "*";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_cat_comp_novo_ok",
	            uiHelper.msg().botoes(
	                whatsappAdmin,
	                uiHelper.msg().trunc(corpo, 1024),
	                List.of(
	                    uiHelper.btn(
	                        "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias),
	                        "🧩 Ver complementos"
	                    ),
	                    uiHelper.btn(
	                        "COMANDO|ADMIN_CAT_COMP_GRUPO_NOVO|" + idCategoria + "|" + normalizarOffset(offsetCategorias),
	                        "➕ Novo complemento"
	                    )
	                )
	            )
	        );
	    }

	    throw new ResponseStatusException(
	        HttpStatus.CONFLICT,
	        "Etapa inválida do cadastro guiado de complemento da categoria"
	    );
	}
    
    

    private List<ComplementoCategoriaAdmin> listarComplementosDaCategoria(Long idCategoria) {

        List<GrupoComplementoResponseDTO> grupos =
            grupoComplementoService.listarGruposDaCategoria(idCategoria, true);

        if (grupos == null || grupos.isEmpty()) {
            return List.of();
        }

        List<ComplementoCategoriaAdmin> itens = new ArrayList<>();

        for (GrupoComplementoResponseDTO grupo : grupos) {
            if (grupo == null || grupo.getId() == null) {
                continue;
            }

            List<ComplementoResponseDTO> complementos =
                grupoComplementoService.listarComplementos(grupo.getId(), false);

            if (complementos == null || complementos.isEmpty()) {
                continue;
            }

            for (ComplementoResponseDTO complemento : complementos) {
                if (complemento == null || complemento.getId() == null) {
                    continue;
                }

                itens.add(new ComplementoCategoriaAdmin(grupo.getId(), complemento));
            }
        }

        return itens.stream()
            .sorted((a, b) -> uiHelper.msg().safe(a.complemento().getNome())
                .compareToIgnoreCase(uiHelper.msg().safe(b.complemento().getNome())))
            .toList();
    }
    
    
    private int normalizarOffset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }
    
    
    
    
}