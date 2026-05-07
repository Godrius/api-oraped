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
import br.com.oraped.dto.produto.complemento.ComplementoResponseDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoProdutoRequestDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoProdutoResponseDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.produto.ProdutoService;
import br.com.oraped.service.produto.complemento.GrupoComplementoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import lombok.RequiredArgsConstructor;

/**
 * Serviço administrativo para complementos associados a produtos pelo WhatsApp.
 *
 * Aplicação:
 * - associa grupos reutilizáveis aos produtos
 * - lista grupos associados ao produto
 * - permite consultar complementos dentro do contexto do produto
 * - comandos atendidos por este service seguem o padrão ADMIN_PROD_COMP_*
 */
@Service
@RequiredArgsConstructor
public class AdminComplementoProdutoService {

    private static final int LIST_MAX_ROWS = 10;

    private final ProdutoService produtoService;
    private final GrupoComplementoService grupoComplementoService;
    private final AdminWhatsappUiHelper sup;

    // =========================================================
    // PRODUTO: MENU DE COMPLEMENTOS
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuComplementosProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);

        List<GrupoComplementoProdutoResponseDTO> associados =
            grupoComplementoService.listarGruposDoProduto(idProduto, true);

        String resumo = associados.isEmpty()
            ? "Nenhum grupo associado."
            : associados.size() == 1
                ? "1 grupo associado."
                : associados.size() + " grupos associados.";

        String corpo =
            "🧩 *Complementos do produto*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                resumo + "\n\n" +
                "O que deseja fazer?";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_complementos_menu",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(corpo, 1024),
                "Complementos",
                "Opções",
                List.of(
                    sup.row(
                        "COMANDO|ADMIN_PROD_COMP_ASSOCIADOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|0",
                        "Ver associados",
                        "Grupos usados neste produto"
                    ),
                    sup.row(
                        "COMANDO|ADMIN_PROD_COMP_ASSOCIAR_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|0",
                        "Associar grupo",
                        "Usar grupo existente"
                    ),
                    sup.row(
                        montarComandoVoltarProduto(idProduto, idCategoria, offsetListaProduto),
                        "⬅️ Voltar",
                        "Ações do produto"
                    )
                )
            )
        );
    }

    // =========================================================
    // PRODUTO: GRUPOS ASSOCIADOS
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin listarGruposAssociadosAoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Integer offsetGrupos
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);

        List<GrupoComplementoProdutoResponseDTO> associados =
            grupoComplementoService.listarGruposDoProduto(idProduto, true);

        if (associados.isEmpty()) {
            String corpo =
                "🧩 *Grupos associados*\n\n" +
                    "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                    "Este produto ainda não possui grupos de complementos associados.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_prod_comp_associados_vazio",
                sup.msg().botoes(
                    whatsappAdmin,
                    sup.msg().trunc(corpo, 1024),
                    List.of(
                        sup.btn(
                            "COMANDO|ADMIN_PROD_COMP_ASSOCIAR_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|0",
                            "➕ Associar"
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
        if (safeOffset >= associados.size()) {
            safeOffset = 0;
        }

        int pageSize = associados.size() > LIST_MAX_ROWS ? 8 : 9;
        int endExclusive = Math.min(safeOffset + pageSize, associados.size());
        List<GrupoComplementoProdutoResponseDTO> page = associados.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < associados.size();

        String corpo =
            "🧩 *Grupos associados*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                "Escolha um grupo para gerenciar.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new java.util.ArrayList<>();

        for (GrupoComplementoProdutoResponseDTO item : page) {
            itens.add(sup.row(
                "COMANDO|ADMIN_PROD_COMP_GRUPO_DETALHE|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + item.getIdGrupo(),
                sup.msg().trunc(item.getNomeGrupo(), 24),
                "Ordem " + item.getOrdem()
            ));
        }

        if (temMais) {
            itens.add(sup.row(
                "COMANDO|ADMIN_PROD_COMP_ASSOCIADOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + endExclusive,
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
            "admin_prod_comp_associados",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(corpo, 1024),
                "Grupos",
                "Grupos",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarDetalheGrupoAssociado(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        GrupoComplementoProdutoResponseDTO associacao = grupoComplementoService.listarGruposDoProduto(idProduto, false)
            .stream()
            .filter(item -> Objects.equals(item.getIdGrupo(), idGrupo))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não está associado ao produto"));

        String status = associacao.isAtivo() ? "Ativo" : "Inativo";

        String corpo =
            "🧩 *Grupo associado*\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "*Status:* " + status + "\n" +
                "*Ordem:* " + associacao.getOrdem() + "\n" +
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
                        "COMANDO|ADMIN_PROD_COMP_GRUPO_DESASSOCIAR_CONFIRM|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo,
                        "Desassociar",
                        "Remover grupo deste produto"
                    ),
                    sup.row(
                        "COMANDO|ADMIN_PROD_COMP_ASSOCIADOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|0",
                        "⬅️ Voltar",
                        "Grupos associados"
                    )
                )
            )
        );
    }

    // =========================================================
    // PRODUTO: ASSOCIAR / DESASSOCIAR GRUPO
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin listarGruposDisponiveisParaAssociar(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Integer offsetGrupos
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);

        List<GrupoComplementoResponseDTO> todosAtivos =
            grupoComplementoService.listarGrupos(estabelecimento.getId(), true);

        List<GrupoComplementoProdutoResponseDTO> associados =
            grupoComplementoService.listarGruposDoProduto(idProduto, false);

        List<Long> idsAssociadosAtivos = associados.stream()
            .filter(GrupoComplementoProdutoResponseDTO::isAtivo)
            .map(GrupoComplementoProdutoResponseDTO::getIdGrupo)
            .filter(Objects::nonNull)
            .toList();

        List<GrupoComplementoResponseDTO> disponiveis = todosAtivos.stream()
            .filter(grupo -> !idsAssociadosAtivos.contains(grupo.getId()))
            .toList();

        if (disponiveis.isEmpty()) {
            String corpo =
                "🧩 *Associar grupo*\n\n" +
                    "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                    "Não há grupos disponíveis para associação.\n\n" +
                    "Todos os grupos ativos já estão associados a este produto ou ainda não existem grupos cadastrados.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_prod_comp_associar_vazio",
                sup.msg().botoes(
                    whatsappAdmin,
                    sup.msg().trunc(corpo, 1024),
                    List.of(
                        sup.btn(
                            "COMANDO|ADMIN_PROD_COMPLEMENTOS_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto),
                            "⬅️ Voltar"
                        ),
                        sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
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
            "🧩 *Associar grupo ao produto*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                "Escolha o grupo que deseja associar.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new java.util.ArrayList<>();

        for (GrupoComplementoResponseDTO grupo : page) {
            String descricao =
                grupo.getMinimoSelecoes() + " mín. / " +
                    grupo.getMaximoSelecoes() + " máx.";

            itens.add(sup.row(
                "COMANDO|ADMIN_PROD_COMP_ASSOCIAR|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + grupo.getId(),
                sup.msg().trunc(grupo.getNome(), 24),
                sup.msg().trunc(descricao, 72)
            ));
        }

        if (temMais) {
            itens.add(sup.row(
                "COMANDO|ADMIN_PROD_COMP_ASSOCIAR_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + endExclusive,
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
            "admin_prod_comp_associar_menu",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(corpo, 1024),
                "Grupos",
                "Grupos",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin associarGrupoAoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        int proximaOrdem = grupoComplementoService.listarGruposDoProduto(idProduto, true).size() + 1;

        GrupoComplementoProdutoRequestDTO request = new GrupoComplementoProdutoRequestDTO();
        request.setIdProduto(idProduto);
        request.setIdGrupo(idGrupo);
        request.setOrdem(proximaOrdem);
        request.setAtivo(true);

        GrupoComplementoProdutoResponseDTO associacao =
            grupoComplementoService.associarGrupoAoProduto(request);

        String corpo =
            "✅ Grupo associado ao produto.\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Grupo: *" + sup.msg().trunc(sup.msg().safe(associacao.getNomeGrupo()), 80) + "*\n" +
                "Ordem: " + associacao.getOrdem();

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_comp_associado",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMP_ASSOCIADOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|0",
                        "🧩 Ver grupos"
                    ),
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMPLEMENTOS_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto),
                        "⬅️ Voltar"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin confirmarDesassociacaoGrupoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarGrupoAssociadoAoProduto(idProduto, idGrupo);

        String corpo =
            "⚠️ *Desassociar grupo*\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "O grupo deixará de aparecer no fluxo de compra deste produto.\n\n" +
                "O cadastro do grupo e dos complementos será mantido para uso em outros produtos.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_comp_grupo_desassociar_confirm",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMP_GRUPO_DESASSOCIAR|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo,
                        "Desassociar"
                    ),
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMP_GRUPO_DETALHE|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|" + idGrupo,
                        "Cancelar"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin desassociarGrupoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetListaProduto,
        Long idGrupo
    ) {
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto);
        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarGrupoAssociadoAoProduto(idProduto, idGrupo);

        grupoComplementoService.desassociarGrupoDoProduto(idProduto, idGrupo);

        String corpo =
            "✅ Grupo desassociado do produto.\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Grupo: *" + sup.msg().trunc(sup.msg().safe(grupo.getNome()), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_comp_grupo_desassociado",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMP_ASSOCIADOS|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto) + "|0",
                        "🧩 Ver grupos"
                    ),
                    sup.btn(
                        "COMANDO|ADMIN_PROD_COMPLEMENTOS_MENU|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetListaProduto),
                        "⬅️ Voltar"
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
        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarGrupoAssociadoAoProduto(idProduto, idGrupo);

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
        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarGrupoAssociadoAoProduto(idProduto, idGrupo);
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
        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarGrupoAssociadoAoProduto(idProduto, idGrupo);
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
        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarGrupoAssociadoAoProduto(idProduto, idGrupo);
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
        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarGrupoAssociadoAoProduto(idProduto, idGrupo);
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

    // =========================================================
    // HELPERS
    // =========================================================

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

    private GrupoComplementoProdutoResponseDTO validarGrupoAssociadoAoProduto(Long idProduto, Long idGrupo) {
        return grupoComplementoService.listarGruposDoProduto(idProduto, false)
            .stream()
            .filter(item -> Objects.equals(item.getIdGrupo(), idGrupo))
            .filter(GrupoComplementoProdutoResponseDTO::isAtivo)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não está associado ao produto"));
    }

    private void validarComplementoDoGrupo(Complemento complemento, Long idGrupo) {
        if (complemento.getGrupo() == null || complemento.getGrupo().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complemento sem grupo associado");
        }

        if (!Objects.equals(complemento.getGrupo().getId(), idGrupo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complemento não pertence ao grupo informado");
        }
    }

    private int normalizarOffset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
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
}