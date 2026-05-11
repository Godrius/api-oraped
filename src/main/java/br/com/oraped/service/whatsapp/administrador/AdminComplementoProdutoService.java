package br.com.oraped.service.whatsapp.administrador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.domain.produto.complemento.Complemento;
import br.com.oraped.domain.produto.complemento.GrupoComplemento;
import br.com.oraped.dto.produto.complemento.ComplementoRequestDTO;
import br.com.oraped.dto.produto.complemento.ComplementoResponseDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoRequestDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.produto.ProdutoService;
import br.com.oraped.service.produto.complemento.GrupoComplementoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminComplementoService;
import lombok.RequiredArgsConstructor;

/**
 * Serviço administrativo para complementos próprios de produtos pelo WhatsApp.
 *
 * Aplicação:
 * - cria grupos de complementos diretamente no produto
 * - lista grupos cadastrados no produto
 * - permite consultar e configurar complementos dentro do contexto do produto
 * - comandos atendidos por este service seguem o padrão ADMIN_PROD_COMP_*
 */
@Service
@RequiredArgsConstructor
public class AdminComplementoProdutoService {

    private static final int LIST_MAX_ROWS = 10;

    private final ProdutoService produtoService;
    private final GrupoComplementoService grupoComplementoService;
    private final AdminWhatsappUiHelper sup;
    private final SessaoWhatsappAdminComplementoService sessaoAdminComplementoService;
    
    // =========================================================
    // PRODUTO: MENU DE COMPLEMENTOS
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuComplementosProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetListaProduto,
	    Integer offsetComplementos
	) {

	    Produto produto = buscarProdutoValidado(estabelecimento, idProduto);

	    List<ComplementoProdutoAdmin> complementos = listarComplementosDoProduto(idProduto);

	    int safeOffsetComplementos = normalizarOffset(offsetComplementos);

	    if (safeOffsetComplementos >= complementos.size()) {
	        safeOffsetComplementos = 0;
	    }

	    int pageSize = complementos.size() > LIST_MAX_ROWS ? 7 : 8;
	    int endExclusive = Math.min(safeOffsetComplementos + pageSize, complementos.size());
	    List<ComplementoProdutoAdmin> page = complementos.subList(safeOffsetComplementos, endExclusive);
	    boolean temMais = endExclusive < complementos.size();

	    String corpo =
	        "🧩 *Complementos do produto*\n\n" +
	            "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
	            (complementos.isEmpty()
	                ? "Nenhum complemento cadastrado ainda.\n\n"
	                : complementos.size() == 1
	                    ? "1 complemento cadastrado.\n\n"
	                    : complementos.size() + " complementos cadastrados.\n\n") +
	            "Escolha uma opção.";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new java.util.ArrayList<>();

	    itens.add(sup.row(
    	    "COMANDO|ADMIN_PROD_COMP_GRUPO_NOVO|" +
    	        idProduto + "|" +
    	        idCategoria + "|" +
    	        normalizarOffset(offsetListaProduto),
    	    "➕ Novo complemento",
    	    "Cadastrar adicional"
    	));

	    for (ComplementoProdutoAdmin item : page) {
	        ComplementoResponseDTO complemento = item.complemento();

	        String status = complemento.isAtivo() ? "Ativo" : "Inativo";
	        String preco = sup.msg().formatarMoeda(complemento.getPrecoAdicional());

	        itens.add(sup.row(
	            "COMANDO|ADMIN_PROD_COMP_COMPLEMENTO_DETALHE|" +
	                idProduto + "|" +
	                idCategoria + "|" +
	                normalizarOffset(offsetListaProduto) + "|" +
	                item.idGrupo() + "|" +
	                complemento.getId(),
	            sup.msg().trunc(complemento.getNome(), 24),
	            sup.msg().trunc(status + " ┃ +" + preco, 72)
	        ));
	    }

	    if (temMais) {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_PROD_COMPLEMENTOS_MENU|" +
	                idProduto + "|" +
	                idCategoria + "|" +
	                normalizarOffset(offsetListaProduto) + "|" +
	                endExclusive,
	            "➡️ Mais opções",
	            "Ver próxima página"
	        ));
	    }

	    itens.add(sup.row(
	        montarComandoVoltarProduto(idProduto, idCategoria, offsetListaProduto),
	        "⬅️ Voltar",
	        "Ações do produto"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_complementos_menu",
	        sup.msg().lista(
	            whatsappAdmin,
	            sup.msg().truncWord(corpo, 1024),
	            "Complementos",
	            "Complementos",
	            itens
	        )
	    );
	}

    // =========================================================
    // PRODUTO: GRUPOS ASSOCIADOS
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin listarGruposProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetListaProduto,
	    Integer offsetGrupos
	) {

	    Produto produto = buscarProdutoValidado(estabelecimento, idProduto);

	    List<GrupoComplementoResponseDTO> grupos =
	        grupoComplementoService.listarGruposDoProduto(idProduto, true);

	    if (grupos.isEmpty()) {
	        String corpo =
	            "🧩 *Grupos de complementos*\n\n" +
	                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
	                "Este produto ainda não possui grupos de complementos cadastrados.";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_prod_comp_grupos_vazio",
	            sup.msg().botoes(
	                whatsappAdmin,
	                sup.msg().trunc(corpo, 1024),
	                List.of(
	                    sup.btn(
	                        "COMANDO|ADMIN_PROD_COMP_GRUPO_NOVO|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto),
	                        "➕ Novo grupo"
	                    ),
	                    sup.btn(
	                        "COMANDO|ADMIN_PROD_COMPLEMENTOS_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto),
	                        "⬅️ Voltar"
	                    )
	                )
	            )
	        );
	    }

	    int safeOffset = normalizarOffset(offsetGrupos);

	    if (safeOffset >= grupos.size()) {
	        safeOffset = 0;
	    }

	    int pageSize = grupos.size() > LIST_MAX_ROWS ? 8 : 9;
	    int endExclusive = Math.min(safeOffset + pageSize, grupos.size());
	    List<GrupoComplementoResponseDTO> page = grupos.subList(safeOffset, endExclusive);
	    boolean temMais = endExclusive < grupos.size();

	    String corpo =
	        "🧩 *Grupos de complementos*\n\n" +
	            "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
	            "Escolha um grupo para gerenciar.";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new java.util.ArrayList<>();

	    for (GrupoComplementoResponseDTO grupo : page) {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_PROD_COMP_GRUPO_DETALHE|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + grupo.getId(),
	            sup.msg().trunc(grupo.getNome(), 24),
	            montarDescricaoGrupo(grupo)
	        ));
	    }

	    if (temMais) {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_PROD_COMP_GRUPOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + endExclusive,
	            "➡️ Mais grupos",
	            "Ver próxima página"
	        ));
	    }

	    itens.add(sup.row(
	        "COMANDO|ADMIN_PROD_COMPLEMENTOS_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto),
	        "⬅️ Voltar",
	        "Menu de complementos"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_comp_grupos",
	        sup.msg().lista(
	            whatsappAdmin,
	            sup.msg().truncWord(corpo, 1024),
	            "Grupos",
	            "Grupos",
	            itens
	        )
	    );
	}
    
    
    public AdministradorWhatsappResultados.ResultadoAdmin criarGrupoProdutoBasico(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetListaProduto
	) {

	    Produto produto = buscarProdutoValidado(estabelecimento, idProduto);

	    int ordem = grupoComplementoService
	        .listarGruposDoProduto(idProduto, false)
	        .size() + 1;

	    GrupoComplementoRequestDTO dto = new GrupoComplementoRequestDTO();
	    dto.setIdEstabelecimento(estabelecimento.getId());
	    dto.setIdProduto(idProduto);
	    dto.setNome("Complementos - " + produto.getNome());
	    dto.setDescricao("Complementos disponíveis para o produto " + produto.getNome());
	    dto.setMinimoSelecoes(0);
	    dto.setMaximoSelecoes(1);
	    dto.setOrdem(ordem);
	    dto.setAtivo(true);

	    GrupoComplementoResponseDTO grupo = grupoComplementoService.salvarGrupo(null, dto);

	    String corpo =
	        "✅ Grupo de complementos criado.\n\n" +
	            "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
	            "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n\n" +
	            "Agora você pode cadastrar os complementos deste grupo.";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_comp_grupo_criado",
	        sup.msg().botoes(
	            whatsappAdmin,
	            sup.msg().trunc(corpo, 1024),
	            List.of(
	            	sup.btn(
        			    "COMANDO|ADMIN_PROD_COMP_GRUPO_DETALHE|" +
        			        idProduto + "|" +
        			        idCategoria + "|" +
        			        normalizarOffset(offsetListaProduto) + "|" +
        			        grupo.getId(),
        			    "🧩 Ver grupo"
        			),
	                sup.btn(
	                    "COMANDO|ADMIN_PROD_COMPLEMENTOS_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto),
	                    "⬅️ Voltar"
	                )
	            )
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin montarDetalheGrupoProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetListaProduto,
	    Long idGrupo
	) {

	    Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
	    GrupoComplemento grupo = buscarGrupoProdutoValidado(estabelecimento, idProduto, idGrupo);

	    String status = grupo.isAtivo() ? "Ativo" : "Inativo";

	    String corpo =
	        "🧩 *Grupo de complementos*\n\n" +
	            "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
	            "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n\n" +
	            "*Status:* " + status + "\n" +
	            "*Ordem:* " + grupo.getOrdem() + "\n" +
	            "*Mínimo:* " + grupo.getMinimoSelecoes() + "\n" +
	            "*Máximo:* " + grupo.getMaximoSelecoes() + "\n\n" +
	            "O que deseja fazer?";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_comp_grupo_detalhe",
	        sup.msg().lista(
	            whatsappAdmin,
	            sup.msg().truncWord(corpo, 1024),
	            "Grupo",
	            "Opções",
	            List.of(
	                sup.row(
	                    "COMANDO|ADMIN_PROD_COMP_COMPLEMENTOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|0",
	                    "Ver complementos",
	                    "Opções deste grupo"
	                ),
	                sup.row(
	                    "COMANDO|ADMIN_PROD_COMP_GRUPOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|0",
	                    "⬅️ Voltar",
	                    "Grupos do produto"
	                )
	            )
	        )
	    );
	}

    // =========================================================
    // PRODUTO: COMPLEMENTOS DO GRUPO ASSOCIADO
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin listarComplementosDoGrupo(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo,
        Integer offsetComplementos
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
        GrupoComplemento grupo = buscarGrupoProdutoValidado(
    	    estabelecimento,
    	    idProduto,
    	    idGrupo
    	);

        List<ComplementoResponseDTO> complementos =
            grupoComplementoService.listarComplementos(idGrupo, false);

        if (complementos.isEmpty()) {
            String corpo =
                "🧩 *Complementos do grupo*\n\n" +
                    "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                    "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                    "Este grupo ainda não possui complementos cadastrados.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_prod_comp_complementos_vazio",
                sup.msg().botoes(
                    whatsappAdmin,
                    sup.msg().trunc(corpo, 1024),
                    List.of(
                        sup.btn(
                            "COMANDO|ADMIN_PROD_COMP_GRUPO_DETALHE|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo,
                            "⬅️ Voltar"
                        ),
                        sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                    )
                )
            );
        }

        int safeOffset = normalizarOffset(offsetComplementos);
        if (safeOffset >= complementos.size()) {
            safeOffset = 0;
        }

        int pageSize = complementos.size() > LIST_MAX_ROWS ? 8 : 9;
        int endExclusive = Math.min(safeOffset + pageSize, complementos.size());
        List<ComplementoResponseDTO> page = complementos.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < complementos.size();

        String corpo =
            "🧩 *Complementos do grupo*\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "Escolha um complemento para gerenciar.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new java.util.ArrayList<>();

        for (ComplementoResponseDTO complemento : page) {
            String status = complemento.isAtivo() ? "Ativo" : "Inativo";
            String preco = sup.msg().formatarMoeda(complemento.getPrecoAdicional());

            itens.add(sup.row(
                "COMANDO|ADMIN_PROD_COMP_COMPLEMENTO_DETALHE|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|" + complemento.getId(),
                sup.msg().trunc(complemento.getNome(), 24),
                sup.msg().trunc(status + " ┃ +" + preco, 72)
            ));
        }

        if (temMais) {
            itens.add(sup.row(
                "COMANDO|ADMIN_PROD_COMP_COMPLEMENTOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|" + endExclusive,
                "➡️ Mais opções",
                "Ver próxima página"
            ));
        }

        itens.add(sup.row(
            "COMANDO|ADMIN_PROD_COMP_GRUPO_DETALHE|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo,
            "⬅️ Voltar",
            "Detalhes do grupo"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_comp_complementos",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(corpo, 1024),
                "Complementos",
                "Complementos",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarDetalheComplemento(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo,
        Long idComplemento
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
        GrupoComplemento grupo = buscarGrupoProdutoValidado(
    	    estabelecimento,
    	    idProduto,
    	    idGrupo
    	);

    	Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

    	validarComplementoDoGrupo(complemento, idGrupo);

        String descricao = sup.msg().safe(complemento.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String comandoStatus = complemento.isAtivo()
            ? "COMANDO|ADMIN_PROD_COMP_COMPLEMENTO_STATUS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|" + idComplemento + "|0"
            : "COMANDO|ADMIN_PROD_COMP_COMPLEMENTO_STATUS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|" + idComplemento + "|1";

        String corpo =
            "🧩 *Complemento*\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "*Opção:* " + sup.msg().trunc(sup.msg().safe(complemento.getNome()), 80) + "\n" +
                "*Descrição:* " + sup.msg().trunc(descricao, 300) + "\n" +
                "*Preço adicional:* " + sup.msg().formatarMoeda(complemento.getPrecoAdicional()) + "\n" +
                "*Status:* " + (complemento.isAtivo() ? "Ativo" : "Inativo") + "\n\n" +
                "O que deseja fazer?";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_comp_complemento_detalhe",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(corpo, 1024),
                "Complemento",
                "Opções",
                List.of(
                    sup.row(
                        "COMANDO|ADMIN_PROD_COMP_COMPLEMENTO_PRECO_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|" + idComplemento,
                        "Ajustar preço",
                        "Preço adicional"
                    ),
                    sup.row(
                        comandoStatus,
                        complemento.isAtivo() ? "Desativar" : "Ativar",
                        complemento.isAtivo() ? "Ocultar esta opção" : "Liberar esta opção"
                    ),
                    sup.row(
                        "COMANDO|ADMIN_PROD_COMP_COMPLEMENTOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|0",
                        "⬅️ Voltar",
                        "Complementos do grupo"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin alterarStatusComplemento(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo,
        Long idComplemento,
        boolean ativo
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
        GrupoComplemento grupo = buscarGrupoProdutoValidado(
    	    estabelecimento,
    	    idProduto,
    	    idGrupo
    	);

    	Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

    	validarComplementoDoGrupo(complemento, idGrupo);

        grupoComplementoService.atualizarStatusComplemento(idComplemento, ativo);

        String corpo =
            "✅ Status atualizado.\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n" +
                "Complemento: *" + sup.msg().trunc(sup.msg().safe(complemento.getNome()), 80) + "*\n\n" +
                "*Novo status:* " + (ativo ? "Ativo" : "Inativo");

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_comp_complemento_status_ok",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMP_COMPLEMENTO_DETALHE|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|" + idComplemento,
                        "🧩 Ver opção"
                    ),
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMP_COMPLEMENTOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|0",
                        "⬅️ Voltar"
                    )
                )
            )
        );
    }

    // =========================================================
    // PRODUTO: PREÇO DO COMPLEMENTO
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuPrecoComplemento(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo,
        Long idComplemento
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
        GrupoComplemento grupo = buscarGrupoProdutoValidado(
    	    estabelecimento,
    	    idProduto,
    	    idGrupo
    	);

    	Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

    	validarComplementoDoGrupo(complemento, idGrupo);

        String corpo =
            "💲 *Preço do complemento*\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n" +
                "Complemento: *" + sup.msg().trunc(sup.msg().safe(complemento.getNome()), 80) + "*\n\n" +
                "*Preço adicional atual:* " + sup.msg().formatarMoeda(complemento.getPrecoAdicional()) + "\n\n" +
                "Escolha um ajuste:";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_comp_complemento_preco_menu",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(corpo, 1024),
                "Preço",
                "Preço",
                List.of(
                    sup.row(montarComandoAplicarPrecoComplemento(idProduto, idCategoria, offsetListaProduto, idGrupo, idComplemento, 100), "+ R$ 1,00", "Aumentar"),
                    sup.row(montarComandoAplicarPrecoComplemento(idProduto, idCategoria, offsetListaProduto, idGrupo, idComplemento, 200), "+ R$ 2,00", "Aumentar"),
                    sup.row(montarComandoAplicarPrecoComplemento(idProduto, idCategoria, offsetListaProduto, idGrupo, idComplemento, 500), "+ R$ 5,00", "Aumentar"),
                    sup.row(montarComandoAplicarPrecoComplemento(idProduto, idCategoria, offsetListaProduto, idGrupo, idComplemento, -100), "- R$ 1,00", "Diminuir"),
                    sup.row(montarComandoAplicarPrecoComplemento(idProduto, idCategoria, offsetListaProduto, idGrupo, idComplemento, -200), "- R$ 2,00", "Diminuir"),
                    sup.row(montarComandoAplicarPrecoComplemento(idProduto, idCategoria, offsetListaProduto, idGrupo, idComplemento, -500), "- R$ 5,00", "Diminuir"),
                    sup.row(
                        "COMANDO|ADMIN_PROD_COMP_COMPLEMENTO_DETALHE|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|" + idComplemento,
                        "⬅️ Voltar",
                        "Detalhes do complemento"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin aplicarDeltaPrecoComplemento(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo,
        Long idComplemento,
        Integer deltaCentavos
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
        GrupoComplemento grupo = buscarGrupoProdutoValidado(
    	    estabelecimento,
    	    idProduto,
    	    idGrupo
    	);

    	Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

    	validarComplementoDoGrupo(complemento, idGrupo);

        if (deltaCentavos == null || deltaCentavos == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deltaCentavos é obrigatório");
        }

        BigDecimal atual = complemento.getPrecoAdicional() == null ? BigDecimal.ZERO : complemento.getPrecoAdicional();
        BigDecimal delta = BigDecimal.valueOf(deltaCentavos).movePointLeft(2);
        BigDecimal novoPreco = atual.add(delta).setScale(2, RoundingMode.HALF_UP);

        if (novoPreco.compareTo(BigDecimal.ZERO) < 0) {
            novoPreco = BigDecimal.ZERO;
        }

        // A persistência fica centralizada no service de produto.
        grupoComplementoService.atualizarPrecoComplemento(idComplemento, novoPreco);

        String corpo =
            "✅ Preço atualizado.\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n" +
                "Complemento: *" + sup.msg().trunc(sup.msg().safe(complemento.getNome()), 80) + "*\n\n" +
                "*Novo preço adicional:* " + sup.msg().formatarMoeda(novoPreco);

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_comp_complemento_preco_ok",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMP_COMPLEMENTO_DETALHE|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|" + idComplemento,
                        "🧩 Ver opção"
                    ),
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMP_COMPLEMENTOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo + "|0",
                        "⬅️ Voltar"
                    )
                )
            )
        );
    }

    
    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroGuiadoComplementoProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Long idProduto,
	    Long idCategoria,
	    Integer offsetListaProduto
	) {

	    Produto produto = buscarProdutoValidado(estabelecimento, idProduto);

	    List<GrupoComplementoResponseDTO> grupos =
	        grupoComplementoService.listarGruposDoProduto(idProduto, true);

	    GrupoComplementoResponseDTO grupo;

	    if (grupos == null || grupos.isEmpty()) {
	        int ordem = grupoComplementoService
	            .listarGruposDoProduto(idProduto, false)
	            .size() + 1;

	        GrupoComplementoRequestDTO dto = new GrupoComplementoRequestDTO();
	        dto.setIdEstabelecimento(estabelecimento.getId());
	        dto.setIdProduto(idProduto);
	        dto.setNome("Complementos - " + produto.getNome());
	        dto.setDescricao("Complementos disponíveis para o produto " + produto.getNome());
	        dto.setMinimoSelecoes(0);
	        dto.setMaximoSelecoes(1);
	        dto.setOrdem(ordem);
	        dto.setAtivo(true);

	        // O grupo técnico é criado automaticamente para simplificar o fluxo do admin.
	        grupo = grupoComplementoService.salvarGrupo(null, dto);
	    } else {
	        grupo = grupos.get(0);
	    }

	    sessaoAdminComplementoService.marcarAguardandoNovoComplementoProduto(
	        idSessao,
	        idProduto,
	        idCategoria,
	        grupo.getId(),
	        normalizarOffset(offsetListaProduto)
	    );

	    String corpo =
	        "➕ *Novo complemento*\n\n" +
	            "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
	            "Digite o *nome do complemento*.\n\n" +
	            "Exemplos:\n" +
	            "- Leite condensado\n" +
	            "- Granola\n" +
	            "- Borda recheada\n" +
	            "- Bacon";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_prod_comp_novo_nome",
	        sup.msg().botoes(
	            whatsappAdmin,
	            sup.msg().trunc(corpo, 1024),
	            List.of(
	                sup.btn(
	                    "COMANDO|ADMIN_CARDAPIO_PRODUTO|" +
	                        idProduto + "|" +
	                        idCategoria + "|" +
	                        normalizarOffset(offsetListaProduto),
	                    "⬅️ Cancelar"
	                )
	            )
	        )
	    );
	}
    
    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroGuiadoComplementoProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String texto
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    if (!sessaoAdminComplementoService.isAguardandoNovoComplementoProduto(idSessao)) {
	        throw new ResponseStatusException(
	            HttpStatus.CONFLICT,
	            "Sessão não está aguardando cadastro de complemento do produto"
	        );
	    }

	    String etapa = sessaoAdminComplementoService.getEtapaNovoComplementoProduto(idSessao);
	    String valor = texto == null ? "" : texto.trim();

	    Long idProduto = sessaoAdminComplementoService.getIdProdutoNovoComplementoProduto(idSessao);
	    Long idCategoria = sessaoAdminComplementoService.getIdCategoriaNovoComplementoProduto(idSessao);
	    Long idGrupo = sessaoAdminComplementoService.getIdGrupoNovoComplementoProduto(idSessao);
	    int offsetListaProduto = sessaoAdminComplementoService.getOffsetListaProdutoNovoComplemento(idSessao);

	    Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
	    GrupoComplemento grupo = buscarGrupoProdutoValidado(estabelecimento, idProduto, idGrupo);

	    if (SessaoWhatsappAdminComplementoService.ETAPA_PRODUTO_COMPLEMENTO_NOME.equals(etapa)) {

	        if (!StringUtils.hasText(valor)) {
	            return new AdministradorWhatsappResultados.ResultadoAdmin(
	                "admin_prod_comp_nome_invalido",
	                sup.msg().texto(
	                    whatsappAdmin,
	                    "Não consegui identificar o nome do complemento.\n\nExemplo: *Granola*"
	                )
	            );
	        }

	        sessaoAdminComplementoService.salvarNomeNovoComplementoProduto(
	            idSessao,
	            sup.msg().trunc(valor, 120)
	        );

	        String corpo =
	            "📝 *Descrição do complemento*\n\n" +
	                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
	                "Complemento: *" + sup.msg().trunc(valor, 80) + "*\n\n" +
	                "Agora envie uma *descrição curta*.\n\n" +
	                "Exemplos:\n" +
	                "- Adicional crocante\n" +
	                "- Cobertura premium\n" +
	                "- Ingrediente extra\n\n" +
	                "Se não quiser descrição, envie: *sem descrição*";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_prod_comp_novo_descricao",
	            sup.msg().texto(
	                whatsappAdmin,
	                sup.msg().trunc(corpo, 1024)
	            )
	        );
	    }

	    if (SessaoWhatsappAdminComplementoService.ETAPA_PRODUTO_COMPLEMENTO_DESCRICAO.equals(etapa)) {

	        String descricao = "sem descrição".equalsIgnoreCase(valor)
	            ? ""
	            : sup.msg().trunc(valor, 600);

	        sessaoAdminComplementoService.salvarDescricaoNovoComplementoProduto(
	            idSessao,
	            descricao
	        );

	        String corpo =
	            "💲 *Preço do complemento*\n\n" +
	                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
	                "Complemento: *" + sup.msg().trunc(sup.msg().safe(sessaoAdminComplementoService.getNomeNovoComplementoProduto(idSessao)), 80) + "*\n\n" +
	                "Agora envie o *preço adicional*.\n\n" +
	                "Exemplos:\n" +
	                "- 0\n" +
	                "- 2,50\n" +
	                "- 5";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_prod_comp_novo_preco",
	            sup.msg().texto(
	                whatsappAdmin,
	                sup.msg().trunc(corpo, 1024)
	            )
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
	                "admin_prod_comp_preco_invalido",
	                sup.msg().texto(
	                    whatsappAdmin,
	                    "Preço inválido.\n\nExemplos válidos:\n- 0\n- 2,50\n- 5"
	                )
	            );
	        }

	        if (preco.compareTo(BigDecimal.ZERO) < 0) {
	            return new AdministradorWhatsappResultados.ResultadoAdmin(
	                "admin_prod_comp_preco_negativo",
	                sup.msg().texto(
	                    whatsappAdmin,
	                    "O preço do complemento não pode ser negativo.\n\nExemplo: *2,50*"
	                )
	            );
	        }

	        sessaoAdminComplementoService.salvarPrecoNovoComplementoProduto(
	            idSessao,
	            preco
	        );

	        String corpo =
	            "📋 *Regra de consumo*\n\n" +
	                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
	                "Complemento: *" + sup.msg().trunc(sup.msg().safe(sessaoAdminComplementoService.getNomeNovoComplementoProduto(idSessao)), 80) + "*\n" +
	                "Preço adicional: *" + sup.msg().formatarMoeda(preco) + "*\n\n" +
	                "Quantas vezes o cliente pode escolher este complemento?\n\n" +
	                "Envie apenas um número.\n\n" +
	                "Exemplos:\n" +
	                "- 1\n" +
	                "- 2\n" +
	                "- 3";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_prod_comp_novo_regras",
	            sup.msg().texto(
	                whatsappAdmin,
	                sup.msg().trunc(corpo, 1024)
	            )
	        );
	    }

	    if (SessaoWhatsappAdminComplementoService.ETAPA_PRODUTO_COMPLEMENTO_REGRAS.equals(etapa)) {

	        Integer maximoSelecoes;

	        try {
	            maximoSelecoes = Integer.valueOf(valor.replaceAll("\\D", ""));
	        } catch (Exception ex) {
	            return new AdministradorWhatsappResultados.ResultadoAdmin(
	                "admin_prod_comp_regra_invalida",
	                sup.msg().texto(
	                    whatsappAdmin,
	                    "Não consegui identificar a regra de consumo.\n\nEnvie apenas um número.\n\nExemplo: *1*"
	                )
	            );
	        }

	        if (maximoSelecoes == null || maximoSelecoes < 1) {
	            return new AdministradorWhatsappResultados.ResultadoAdmin(
	                "admin_prod_comp_regra_invalida",
	                sup.msg().texto(
	                    whatsappAdmin,
	                    "A quantidade máxima precisa ser maior que zero.\n\nExemplo: *1*"
	                )
	            );
	        }

	        String nome = sessaoAdminComplementoService.getNomeNovoComplementoProduto(idSessao);
	        String descricao = sessaoAdminComplementoService.getDescricaoNovoComplementoProduto(idSessao);
	        BigDecimal preco = sessaoAdminComplementoService.getPrecoNovoComplementoProduto(idSessao);

	        ComplementoRequestDTO dto = new ComplementoRequestDTO();
	        dto.setIdGrupo(grupo.getId());
	        dto.setNome(nome);
	        dto.setDescricao(descricao);
	        dto.setPrecoAdicional(preco == null ? BigDecimal.ZERO : preco);
	        dto.setAtivo(true);

	        ComplementoResponseDTO complemento = grupoComplementoService.salvarComplemento(null, dto);

	        GrupoComplementoRequestDTO grupoDto = new GrupoComplementoRequestDTO();
	        grupoDto.setIdEstabelecimento(estabelecimento.getId());
	        grupoDto.setIdProduto(idProduto);
	        grupoDto.setNome(grupo.getNome());
	        grupoDto.setDescricao(grupo.getDescricao());
	        grupoDto.setMinimoSelecoes(0);
	        grupoDto.setMaximoSelecoes(maximoSelecoes);
	        grupoDto.setOrdem(grupo.getOrdem());
	        grupoDto.setAtivo(grupo.isAtivo());

	        // A regra fica no grupo técnico do produto, mantendo o fluxo simples para o admin.
	        grupoComplementoService.salvarGrupo(grupo.getId(), grupoDto);

	        sessaoAdminComplementoService.limparAguardandoNovoComplementoProduto(idSessao);

	        String corpo =
	            "✅ Complemento cadastrado.\n\n" +
	                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
	                "Complemento: *" + sup.msg().trunc(sup.msg().safe(complemento.getNome()), 80) + "*\n" +
	                "Preço adicional: *" + sup.msg().formatarMoeda(complemento.getPrecoAdicional()) + "*\n" +
	                "Máximo por item: *" + maximoSelecoes + "*";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_prod_comp_novo_ok",
	            sup.msg().botoes(
	                whatsappAdmin,
	                sup.msg().trunc(corpo, 1024),
	                List.of(
	                    sup.btn(
	                        "COMANDO|ADMIN_PROD_COMPLEMENTOS_MENU|" +
	                            idProduto + "|" +
	                            idCategoria + "|" +
	                            normalizarOffset(offsetListaProduto) + "|0",
	                        "🧩 Ver complementos"
	                    ),
	                    sup.btn(
	                        "COMANDO|ADMIN_PROD_COMP_GRUPO_NOVO|" +
	                            idProduto + "|" +
	                            idCategoria + "|" +
	                            normalizarOffset(offsetListaProduto),
	                        "➕ Novo complemento"
	                    )
	                )
	            )
	        );
	    }

	    throw new ResponseStatusException(
	        HttpStatus.CONFLICT,
	        "Etapa inválida do cadastro guiado de complemento"
	    );
	}
    // =========================================================
    // HELPERS
    // =========================================================

    private List<ComplementoProdutoAdmin> listarComplementosDoProduto(Long idProduto) {

        List<GrupoComplementoResponseDTO> grupos =
            grupoComplementoService.listarGruposDoProduto(idProduto, true);

        if (grupos == null || grupos.isEmpty()) {
            return List.of();
        }

        List<ComplementoProdutoAdmin> itens = new java.util.ArrayList<>();

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

                itens.add(new ComplementoProdutoAdmin(grupo.getId(), complemento));
            }
        }

        return itens.stream()
            .sorted((a, b) -> sup.msg().safe(a.complemento().getNome())
                .compareToIgnoreCase(sup.msg().safe(b.complemento().getNome())))
            .toList();
    }

    private record ComplementoProdutoAdmin(
        Long idGrupo,
        ComplementoResponseDTO complemento
    ) {
    }
    
    private Produto buscarProdutoValidado(Estabelecimento estabelecimento, Long idProduto) {
        sup.validarBasico(estabelecimento, "admin");

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        Produto produto = produtoService.buscarObrigatorio(idProduto);

        if (produto.getEstabelecimento() == null || produto.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto sem estabelecimento associado");
        }

        if (!Objects.equals(produto.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto não pertence ao estabelecimento");
        }

        return produto;
    }

    private void validarGrupoDoEstabelecimento(Estabelecimento estabelecimento, GrupoComplemento grupo) {
        if (grupo.getEstabelecimento() == null || grupo.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Grupo sem estabelecimento associado");
        }

        if (!Objects.equals(grupo.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupo não pertence ao estabelecimento");
        }
    }

    private void validarComplementoDoGrupo(Complemento complemento, Long idGrupo) {
        if (complemento.getGrupo() == null || complemento.getGrupo().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complemento sem grupo associado");
        }

        if (!Objects.equals(complemento.getGrupo().getId(), idGrupo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complemento não pertence ao grupo informado");
        }
    }

    

    private String montarComandoVoltarProduto(Long idProduto, Long idCategoria, Integer offsetListaProduto) {
        return "COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto);
    }

    private String montarComandoAplicarPrecoComplemento(
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo,
        Long idComplemento,
        Integer deltaCentavos
    ) {
        return "COMANDO|ADMIN_PROD_COMP_COMPLEMENTO_PRECO_APLICAR|" +
            idProduto + "|" +
            idCategoria + "|" +
            normalizarOffset(offsetListaProduto) + "|" +
            idGrupo + "|" +
            idComplemento + "|" +
            deltaCentavos;
    }
    
    
    private GrupoComplemento buscarGrupoProdutoValidado(
	    Estabelecimento estabelecimento,
	    Long idProduto,
	    Long idGrupo
	) {

	    GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);

	    validarGrupoDoEstabelecimento(estabelecimento, grupo);

	    Long idProdutoGrupo = grupo.getProduto() == null ? null : grupo.getProduto().getId();

	    if (!Objects.equals(idProdutoGrupo, idProduto)) {
	        throw new ResponseStatusException(
	            HttpStatus.BAD_REQUEST,
	            "Grupo de complementos não pertence ao produto informado"
	        );
	    }

	    if (grupo.isExcluido()) {
	        throw new ResponseStatusException(
	            HttpStatus.CONFLICT,
	            "Grupo de complementos excluído"
	        );
	    }

	    return grupo;
	}
    
    private String montarDescricaoGrupo(GrupoComplementoResponseDTO grupo) {

        if (grupo == null) {
            return "";
        }

        Integer minimo = grupo.getMinimoSelecoes() == null ? 0 : grupo.getMinimoSelecoes();
        Integer maximo = grupo.getMaximoSelecoes() == null ? 0 : grupo.getMaximoSelecoes();

        return "mín. " + minimo + " / máx. " + maximo;
    }
    
    private int normalizarOffset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }
}