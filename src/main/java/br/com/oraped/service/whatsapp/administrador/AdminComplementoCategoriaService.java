package br.com.oraped.service.whatsapp.administrador;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.domain.produto.complemento.GrupoComplemento;
import br.com.oraped.dto.produto.complemento.ComplementoResponseDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoCategoriaProdutoResponseDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.repository.produto.CategoriaProdutoRepository;
import br.com.oraped.service.produto.complemento.GrupoComplementoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import lombok.RequiredArgsConstructor;

/**
 * Serviço administrativo para associação de grupos de complementos às categorias de produtos.
 *
 * Aplicação:
 * - permite definir complementos herdados por todos os produtos de uma categoria
 * - reduz a necessidade de configurar grupos produto por produto
 * - mantém a associação direta no produto para exceções futuras
 */
@Service
@RequiredArgsConstructor
public class AdminComplementoCategoriaService {

    private static final int LIST_MAX_ROWS = 10;

    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final GrupoComplementoService grupoComplementoService;
    private final AdminWhatsappUiHelper uiHelper;

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
        Integer offsetCategorias
    ) {

        CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);

        List<GrupoComplementoCategoriaProdutoResponseDTO> associados =
            grupoComplementoService.listarGruposDaCategoriaProduto(idCategoria, true);

        String resumo = associados.isEmpty()
            ? "Nenhum grupo associado."
            : associados.size() == 1
                ? "1 grupo associado."
                : associados.size() + " grupos associados.";

        String corpo =
            "🧩 *Complementos da categoria*\n\n" +
                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
                resumo + "\n\n" +
                "O que deseja fazer?";

        
        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        if (!associados.isEmpty()) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_CAT_COMP_ASSOCIADOS|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|0",
                "Complementos associados",
                "Grupos aplicados à categoria"
            ));
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CAT_COMP_ASSOCIAR_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|0",
            "Associar existentes",
            "Associar grupos já cadastrados"
        ));

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_COMP_GRUPO_NOVO_MENU|0",
            "➕ Novos complementos",
            "Criar grupo de complementos"
        ));

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CAT_COMP_CATEGORIAS|" + normalizarOffset(offsetCategorias),
            "⬅️ Voltar",
            "Categorias"
        ));
        
        return new AdministradorWhatsappResultados.ResultadoAdmin(
    	    "admin_cat_comp_menu",
    	    uiHelper.msg().lista(
    	        whatsappAdmin,
    	        uiHelper.msg().truncWord(corpo, 1024),
    	        "Complementos",
    	        "Opções",
    	        itens
    	    )
    	);
    }

    public AdministradorWhatsappResultados.ResultadoAdmin listarGruposAssociadosCategoria(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idCategoria,
        Integer offsetCategorias,
        Integer offsetGrupos
    ) {

        CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);

        List<GrupoComplementoCategoriaProdutoResponseDTO> associados =
            grupoComplementoService.listarGruposDaCategoriaProduto(idCategoria, true);

        if (associados.isEmpty()) {
            String corpo =
                "🧩 *Grupos associados à categoria*\n\n" +
                    "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
                    "Esta categoria ainda não possui grupos de complementos associados.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_cat_comp_associados_vazio",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    uiHelper.msg().trunc(corpo, 1024),
                    List.of(
                        uiHelper.btn(
                            "COMANDO|ADMIN_CAT_COMP_ASSOCIAR_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|0",
                            "➕ Associar"
                        ),
                        uiHelper.btn(
                            "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias),
                            "⬅️ Voltar"
                        )
                    )
                )
            );
        }

        int safeOffset = normalizarOffset(offsetGrupos);
        if (safeOffset >= associados.size()) {
            safeOffset = 0;
        }

        int pageSize = associados.size() > LIST_MAX_ROWS ? 8 : 9;
        int endExclusive = Math.min(safeOffset + pageSize, associados.size());
        List<GrupoComplementoCategoriaProdutoResponseDTO> page = associados.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < associados.size();

        String corpo =
            "🧩 *Grupos associados à categoria*\n\n" +
                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
                "Escolha um grupo para gerenciar.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (GrupoComplementoCategoriaProdutoResponseDTO item : page) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_CAT_COMP_GRUPO_DETALHE|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|" + item.getIdGrupo(),
                uiHelper.msg().trunc(item.getNomeGrupo(), 24),
                item.getQuantidadeComplementos() == 1
                    ? "1 item extra"
                    : item.getQuantidadeComplementos() + " itens extras"
            ));
        }

        if (temMais) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_CAT_COMP_ASSOCIADOS|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|" + endExclusive,
                "➡️ Mais grupos",
                "Ver próxima página"
            ));
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias),
            "⬅️ Voltar",
            "Complementos da categoria"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cat_comp_associados",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Grupos",
                "Grupos",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin listarGruposDisponiveisParaCategoria(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idCategoria,
        Integer offsetCategorias,
        Integer offsetGrupos
    ) {

        CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);

        List<GrupoComplementoResponseDTO> todosAtivos =
            grupoComplementoService.listarGrupos(estabelecimento.getId(), true);

        List<GrupoComplementoCategoriaProdutoResponseDTO> associados =
            grupoComplementoService.listarGruposDaCategoriaProduto(idCategoria, false);

        List<Long> idsAssociadosAtivos = associados.stream()
            .filter(GrupoComplementoCategoriaProdutoResponseDTO::isAtivo)
            .map(GrupoComplementoCategoriaProdutoResponseDTO::getIdGrupo)
            .filter(Objects::nonNull)
            .toList();

        List<GrupoComplementoResponseDTO> disponiveis = todosAtivos.stream()
            .filter(grupo -> !idsAssociadosAtivos.contains(grupo.getId()))
            .toList();

        if (disponiveis.isEmpty()) {
            String corpo =
                "🧩 *Associar grupo à categoria*\n\n" +
                    "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
                    "Não há grupos disponíveis para associação.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_cat_comp_associar_vazio",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    uiHelper.msg().trunc(corpo, 1024),
                    List.of(
                        uiHelper.btn(
                            "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias),
                            "⬅️ Voltar"
                        ),
                        uiHelper.btn("COMANDO|ADMIN_COMP_GRUPOS_MENU|0", "🧩 Ver grupos")
                    )
                )
            );
        }

        int safeOffset = normalizarOffset(offsetGrupos);
        if (safeOffset >= disponiveis.size()) {
            safeOffset = 0;
        }

        int pageSize = disponiveis.size() > LIST_MAX_ROWS ? 8 : 9;
        int endExclusive = Math.min(safeOffset + pageSize, disponiveis.size());
        List<GrupoComplementoResponseDTO> page = disponiveis.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < disponiveis.size();

        String corpo =
    	    "🧩 *Associar grupo à categoria*\n\n" +
	        "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n\n" +
	        "*Como funciona?*\n" +
	        "Ao associar um grupo de complementos, *todos os produtos da categoria " + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) +"* passam a oferecer automaticamente os complementos contidos no grupo.\n\n" +
	        "Escolha o grupo que deseja aplicar.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (GrupoComplementoResponseDTO grupo : page) {
            String descricao =
                grupo.getMinimoSelecoes() + " mín. / " +
                    grupo.getMaximoSelecoes() + " máx.";

            itens.add(uiHelper.row(
                "COMANDO|ADMIN_CAT_COMP_ASSOCIAR|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|" + grupo.getId(),
                uiHelper.msg().trunc(grupo.getNome(), 24),
                uiHelper.msg().trunc(descricao, 72)
            ));
        }

        if (temMais) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_CAT_COMP_ASSOCIAR_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|" + endExclusive,
                "➡️ Mais grupos",
                "Ver próxima página"
            ));
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias),
            "⬅️ Voltar",
            "Complementos da categoria"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cat_comp_associar_menu",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Grupos",
                "Grupos",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin associarGrupoCategoria(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idCategoria,
	    Integer offsetCategorias,
	    Long idGrupo
	) {

	    CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);
	    GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);

	    int proximaOrdem = grupoComplementoService
	        .listarGruposDaCategoriaProduto(idCategoria, true)
	        .size() + 1;

	    GrupoComplementoCategoriaProdutoResponseDTO associacao =
	        grupoComplementoService.associarGrupoACategoriaProduto(
	            idCategoria,
	            idGrupo,
	            proximaOrdem
	        );

	    List<ComplementoResponseDTO> complementos =
	        grupoComplementoService.listarComplementos(idGrupo, true);

	    String regraSelecao = montarTextoRegraSelecao(
	        grupo.getMinimoSelecoes(),
	        grupo.getMaximoSelecoes()
	    );

	    StringBuilder itensExtras = new StringBuilder();

	    for (ComplementoResponseDTO complemento : complementos) {
	        itensExtras
	            .append("- ")
	            .append(uiHelper.msg().trunc(uiHelper.msg().safe(complemento.getNome()), 60))
	            .append("\n");
	    }

	    String nomeCategoria = uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80);
	    String nomeGrupo = uiHelper.msg().trunc(uiHelper.msg().safe(associacao.getNomeGrupo()), 80);

	    String corpo =
	        "✅ Grupo associado à categoria.\n\n" +
	            "Categoria: *" + nomeCategoria + "*\n" +
	            "Grupo: *" + nomeGrupo + "*\n\n" +
	            "A partir de agora, todos os clientes que comprarem produtos da categoria *" +
	            nomeCategoria + "* poderão incluir *" + regraSelecao + "* dos seguintes itens extras:\n\n" +
	            (itensExtras.length() > 0 ? itensExtras.toString() : "- Nenhum complemento ativo cadastrado neste grupo.");

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cat_comp_associado",
	        uiHelper.msg().botoes(
	            whatsappAdmin,
	            uiHelper.msg().trunc(corpo, 1024),
	            List.of(
	                uiHelper.btn(
	                    "COMANDO|ADMIN_CAT_COMP_ASSOCIADOS|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|0",
	                    "🧩 Ver grupos"
	                ),
	                uiHelper.btn(
	                    "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias),
	                    "⬅️ Voltar"
	                )
	            )
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin montarDetalheGrupoCategoria(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idCategoria,
	    Integer offsetCategorias,
	    Long idGrupo
	) {

	    CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);
	    GrupoComplementoCategoriaProdutoResponseDTO associacao = buscarAssociacaoCategoria(idCategoria, idGrupo);
	    GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);

	    String nomeCategoria = uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80);
	    String nomeGrupo = uiHelper.msg().trunc(uiHelper.msg().safe(associacao.getNomeGrupo()), 80);

	    String regraSelecao = montarTextoRegraSelecao(
	        grupo.getMinimoSelecoes(),
	        grupo.getMaximoSelecoes()
	    );

	    List<ComplementoResponseDTO> complementos = grupoComplementoService.listarComplementos(idGrupo, true);

	    StringBuilder itensExtras = new StringBuilder();

	    for (int i = 0; i < complementos.size() && i < 10; i++) {
	        itensExtras
	            .append("- ")
	            .append(uiHelper.msg().trunc(uiHelper.msg().safe(complementos.get(i).getNome()), 60))
	            .append("\n");
	    }

	    if (complementos.size() > 10) {
	        itensExtras
	            .append("... e mais ")
	            .append(complementos.size() - 10)
	            .append(" itens");
	    }

	    String corpo =
	        "Categoria: *" + nomeCategoria + "*\n" +
	            "Grupo: *" + nomeGrupo + "*\n\n" +
	            "Todos os clientes que comprarem produtos da categoria *" + nomeCategoria +
	            "* podem incluir *" + regraSelecao + "* dos seguintes itens extras:\n\n" +
	            (itensExtras.length() > 0
	                ? itensExtras.toString()
	                : "- Nenhum complemento ativo cadastrado neste grupo.");

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cat_comp_grupo_detalhe",
	        uiHelper.msg().botoes(
	            whatsappAdmin,
	            uiHelper.msg().trunc(corpo, 1024),
	            List.of(
	                uiHelper.btn(
	                    "COMANDO|ADMIN_CAT_COMP_GRUPO_DESASSOCIAR_CONFIRM|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|" + idGrupo,
	                    "Remover grupo"
	                ),
	                uiHelper.btn(
	                    "COMANDO|ADMIN_CAT_COMP_ASSOCIADOS|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|0",
	                    "⬅️ Voltar"
	                )
	            )
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin confirmarDesassociacaoGrupoCategoria(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idCategoria,
        Integer offsetCategorias,
        Long idGrupo
    ) {

        CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);
        GrupoComplementoCategoriaProdutoResponseDTO associacao =
            buscarAssociacaoCategoria(idCategoria, idGrupo);

        String corpo =
            "⚠️ *Remover grupo da categoria*\n\n" +
                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(associacao.getNomeGrupo()), 80) + "*\n\n" +
                "Os produtos desta categoria deixarão de herdar este grupo.\n\n" +
                "O cadastro do grupo e dos complementos será mantido.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cat_comp_desassociar_confirm",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn(
                        "COMANDO|ADMIN_CAT_COMP_GRUPO_DESASSOCIAR|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|" + idGrupo,
                        "Remover"
                    ),
                    uiHelper.btn(
                        "COMANDO|ADMIN_CAT_COMP_GRUPO_DETALHE|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|" + idGrupo,
                        "Cancelar"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin desassociarGrupoCategoria(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idCategoria,
        Integer offsetCategorias,
        Long idGrupo
    ) {

        CategoriaProduto categoria = buscarCategoriaValidada(estabelecimento, idCategoria);
        GrupoComplementoCategoriaProdutoResponseDTO associacao =
            buscarAssociacaoCategoria(idCategoria, idGrupo);

        grupoComplementoService.desassociarGrupoDaCategoriaProduto(idCategoria, idGrupo);

        String corpo =
            "✅ Grupo removido da categoria.\n\n" +
                "Categoria: *" + uiHelper.msg().trunc(uiHelper.msg().safe(categoria.getNome()), 80) + "*\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(associacao.getNomeGrupo()), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cat_comp_desassociado",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn(
                        "COMANDO|ADMIN_CAT_COMP_ASSOCIADOS|" + idCategoria + "|" + normalizarOffset(offsetCategorias) + "|0",
                        "🧩 Ver grupos"
                    ),
                    uiHelper.btn(
                        "COMANDO|ADMIN_CAT_COMPLEMENTOS_MENU|" + idCategoria + "|" + normalizarOffset(offsetCategorias),
                        "⬅️ Voltar"
                    )
                )
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

    private GrupoComplementoCategoriaProdutoResponseDTO buscarAssociacaoCategoria(
        Long idCategoria,
        Long idGrupo
    ) {

        return grupoComplementoService.listarGruposDaCategoriaProduto(idCategoria, false)
            .stream()
            .filter(item -> Objects.equals(item.getIdGrupo(), idGrupo))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não está associado à categoria"));
    }

    private int normalizarOffset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }
    
    
    /**
     * Monta um texto amigável para representar a regra de seleção de complementos
     * (mínimo e máximo) configurada em um grupo.
     *
     * Objetivo:
     * - Traduzir valores numéricos técnicos (min/max) em uma frase compreensível
     *   para o usuário final no WhatsApp.
     * - Evitar mensagens confusas quando os limites não estão definidos.
     *
     * Regras aplicadas:
     * - min = 0 e max = 0 → "itens extras" (sem restrição explícita)
     * - min = 0 e max > 0 → "até X"
     * - min = max → "X" (quantidade fixa obrigatória)
     * - min > 0 e max > min → "de X até Y"
     *
     * Observações:
     * - Valores nulos são tratados como 0 para simplificar a lógica.
     * - Nunca retorna texto técnico (ex.: "min=1 max=3"), sempre linguagem natural.
     * - Usado principalmente em mensagens de confirmação para explicar o impacto
     *   da configuração ao administrador.
     */
    private String montarTextoRegraSelecao(Integer minimoSelecoes, Integer maximoSelecoes) {

        int minimo = minimoSelecoes == null ? 0 : Math.max(0, minimoSelecoes);
        int maximo = maximoSelecoes == null ? 0 : Math.max(0, maximoSelecoes);

        if (minimo == 0 && maximo == 0) {
            return "itens extras";
        }

        if (minimo == 0) {
            return "até " + maximo;
        }

        if (minimo == maximo) {
            return String.valueOf(minimo);
        }

        return "de " + minimo + " até " + maximo;
    }
}