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
import br.com.oraped.domain.produto.Complemento;
import br.com.oraped.domain.produto.GrupoComplemento;
import br.com.oraped.dto.produto.ComplementoRequestDTO;
import br.com.oraped.dto.produto.ComplementoResponseDTO;
import br.com.oraped.dto.produto.GrupoComplementoRequestDTO;
import br.com.oraped.dto.produto.GrupoComplementoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.produto.GrupoComplementoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminComplementoService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminGrupoComplementoService;
import lombok.RequiredArgsConstructor;

/**
 * Serviço administrativo para gerenciamento global de grupos de complementos pelo WhatsApp.
 *
 * Aplicação:
 * - administra grupos reutilizáveis do cardápio
 * - cria, altera, ativa/inativa e exclui logicamente grupos
 * - administra complementos/opções dentro dos grupos
 * - comandos atendidos por este service seguem o padrão ADMIN_COMP_*
 */
@Service
@RequiredArgsConstructor
public class AdminGrupoComplementoService {

    private static final int LIST_MAX_ROWS = 10;

    private final GrupoComplementoService grupoComplementoService;
    private final SessaoWhatsappAdminGrupoComplementoService sessaoAdminGrupoComplementoService;
    private final SessaoWhatsappAdminComplementoService sessaoAdminComplementoService;
    private final AdminWhatsappUiHelper uiHelper;

    // =========================================================
    // GRUPOS GLOBAIS DO CARDÁPIO
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin listarGruposGlobais(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offsetGrupos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        List<GrupoComplementoResponseDTO> grupos =
            grupoComplementoService.listarGrupos(estabelecimento.getId(), false);

        if (grupos.isEmpty()) {
            String corpo =
                "🧩 *Grupos de complementos*\n\n" +
                    "*" + uiHelper.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                    "Ainda não há grupos de complementos cadastrados.\n\n" +
                    "Use esta área para criar grupos reutilizáveis, como:\n" +
                    "- Tipo de massa\n" +
                    "- Borda\n" +
                    "- Recheio da borda\n" +
                    "- Molho extra";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_comp_grupos_vazio",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    uiHelper.msg().trunc(corpo, 1024),
                    List.of(
                        uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_NOVO_MENU|0", "➕ Novo grupo"),
                        uiHelper.btn("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Voltar")
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
                "*" + uiHelper.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                "Escolha um grupo para gerenciar.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (GrupoComplementoResponseDTO grupo : page) {
            String status = grupo.isAtivo() ? "Ativo" : "Inativo";
            String descricao =
                status + " ┃ " +
                    grupo.getMinimoSelecoes() + " mín. / " +
                    grupo.getMaximoSelecoes() + " máx.";

            itens.add(uiHelper.row(
                "COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + grupo.getId() + "|" + safeOffset,
                uiHelper.msg().trunc(grupo.getNome(), 24),
                uiHelper.msg().trunc(descricao, 72)
            ));
        }

        if (temMais) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_COMP_GRUPOS_MENU|" + endExclusive,
                "➡️ Mais grupos",
                "Ver próxima página"
            ));
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_COMP_GRUPO_NOVO_MENU|" + safeOffset,
            "➕ Novo grupo",
            "Cadastrar grupo reutilizável"
        ));

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_CARDAPIO_MENU",
            "⬅️ Voltar",
            "Menu do cardápio"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupos_menu",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Grupos",
                "Grupos",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarDetalheGrupoGlobal(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        int safeOffset = normalizarOffset(offsetGrupos);
        String status = grupo.isAtivo() ? "Ativo" : "Inativo";

        String descricao = uiHelper.msg().safe(grupo.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String corpo =
            "🧩 *Grupo de complementos*\n\n" +
                "*" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "*Descrição:* " + uiHelper.msg().trunc(descricao, 250) + "\n" +
                "*Status:* " + status + "\n" +
                "*Mínimo:* " + grupo.getMinimoSelecoes() + "\n" +
                "*Máximo:* " + grupo.getMaximoSelecoes() + "\n\n" +
                "O que deseja fazer?";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_detalhe",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Grupo",
                "Opções",
                List.of(
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_GRUPO_COMPLEMENTOS|" + idGrupo + "|" + safeOffset + "|0",
                        "Ver complementos",
                        "Opções deste grupo"
                    ),
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_GRUPO_EDITAR_NOME_MENU|" + idGrupo + "|" + safeOffset,
                        "Editar nome",
                        "Alterar o nome do grupo"
                    ),
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_GRUPO_EDITAR_DESC_MENU|" + idGrupo + "|" + safeOffset,
                        "Editar descrição",
                        "Alterar descrição do grupo"
                    ),
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_GRUPO_REGRAS_MENU|" + idGrupo + "|" + safeOffset,
                        "Editar regras",
                        "Mínimo e máximo"
                    ),
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_GRUPO_STATUS|" + idGrupo + "|" + safeOffset + "|" + (grupo.isAtivo() ? "0" : "1"),
                        grupo.isAtivo() ? "Desativar" : "Ativar",
                        grupo.isAtivo() ? "Ocultar para novas associações" : "Liberar para uso"
                    ),
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_GRUPO_EXCLUIR_CONFIRM|" + idGrupo + "|" + safeOffset,
                        "Excluir grupo",
                        "Exclusão lógica"
                    ),
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_GRUPOS_MENU|" + safeOffset,
                        "⬅️ Voltar",
                        "Lista de grupos"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin alterarStatusGrupoGlobal(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos,
        boolean ativo
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        grupoComplementoService.atualizarStatusGrupo(idGrupo, ativo);

        String corpo =
            "✅ Status do grupo atualizado.\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "*Novo status:* " + (ativo ? "Ativo" : "Inativo");

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_status_ok",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + idGrupo + "|" + normalizarOffset(offsetGrupos), "🧩 Ver grupo"),
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPOS_MENU|" + normalizarOffset(offsetGrupos), "⬅️ Voltar")
                )
            )
        );
    }

    // =========================================================
    // GRUPOS: CRIAÇÃO E EDIÇÃO POR DIGITAÇÃO
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroGrupoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Integer offsetGrupos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);
        validarSessao(idSessao);

        int safeOffset = normalizarOffset(offsetGrupos);
        sessaoAdminGrupoComplementoService.marcarAguardandoNovoGrupo(idSessao, safeOffset);

        String corpo =
            "➕ *Novo grupo de complementos*\n\n" +
                "Agora envie apenas o *nome do grupo*.\n\n" +
                "Exemplos:\n" +
                "- Tipo de massa\n" +
                "- Borda\n" +
                "- Molho extra";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_novo_digitacao",
            uiHelper.msg().texto(whatsappAdmin, uiHelper.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroGrupoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String nomeGrupo
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);
        validarSessao(idSessao);

        if (!sessaoAdminGrupoComplementoService.isAguardandoNovoGrupo(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo grupo de complementos");
        }

        if (!StringUtils.hasText(nomeGrupo)) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_comp_grupo_novo_nome_invalido",
                uiHelper.msg().texto(
                    whatsappAdmin,
                    uiHelper.msg().trunc("Não consegui identificar o nome do grupo. Envie apenas o nome, por exemplo: *Borda*.", 1024)
                )
            );
        }

        int safeOffset = sessaoAdminGrupoComplementoService.getOffsetNovoGrupo(idSessao);

        GrupoComplementoRequestDTO dto = new GrupoComplementoRequestDTO();
        dto.setIdEstabelecimento(estabelecimento.getId());
        dto.setNome(nomeGrupo.trim());
        dto.setDescricao(null);
        dto.setMinimoSelecoes(0);
        dto.setMaximoSelecoes(1);
        dto.setAtivo(true);

        GrupoComplementoResponseDTO salvo = grupoComplementoService.salvarGrupo(null, dto);
        sessaoAdminGrupoComplementoService.limparAguardandoNovoGrupo(idSessao);

        String corpo =
            "✅ Grupo criado com sucesso.\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(salvo.getNome()), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_novo_ok",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + salvo.getId() + "|" + safeOffset, "🧩 Ver grupo"),
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPOS_MENU|" + safeOffset, "⬅️ Voltar")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoNomeGrupoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idGrupo,
        Integer offsetGrupos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);
        validarSessao(idSessao);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        int safeOffset = normalizarOffset(offsetGrupos);
        sessaoAdminGrupoComplementoService.marcarAguardandoEditarNomeGrupo(idSessao, idGrupo, safeOffset);

        String corpo =
            "✏️ *Editar nome do grupo*\n\n" +
                "Atual: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "Agora envie apenas o *novo nome*.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_nome_digitacao",
            uiHelper.msg().texto(whatsappAdmin, uiHelper.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoNomeGrupoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novoNome
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);
        validarSessao(idSessao);

        if (!sessaoAdminGrupoComplementoService.isAguardandoEditarNomeGrupo(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando edição do nome do grupo");
        }

        if (!StringUtils.hasText(novoNome)) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_comp_grupo_nome_invalido",
                uiHelper.msg().texto(whatsappAdmin, uiHelper.msg().trunc("Envie um nome válido para o grupo.", 1024))
            );
        }

        Long idGrupo = sessaoAdminGrupoComplementoService.getIdGrupoEditarNome(idSessao);
        int safeOffset = sessaoAdminGrupoComplementoService.getOffsetEditarNomeGrupo(idSessao);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        GrupoComplementoRequestDTO dto = montarDtoGrupoAtual(grupo);
        dto.setNome(novoNome.trim());

        grupoComplementoService.salvarGrupo(idGrupo, dto);
        sessaoAdminGrupoComplementoService.limparAguardandoEditarNomeGrupo(idSessao);

        String corpo =
            "✅ Nome do grupo atualizado.\n\n" +
                "Novo nome: *" + uiHelper.msg().trunc(uiHelper.msg().safe(novoNome.trim()), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_nome_ok",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + idGrupo + "|" + safeOffset, "🧩 Ver grupo"),
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPOS_MENU|" + safeOffset, "⬅️ Voltar")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoDescricaoGrupoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idGrupo,
        Integer offsetGrupos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);
        validarSessao(idSessao);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        int safeOffset = normalizarOffset(offsetGrupos);
        sessaoAdminGrupoComplementoService.marcarAguardandoEditarDescricaoGrupo(idSessao, idGrupo, safeOffset);

        String descricaoAtual = StringUtils.hasText(grupo.getDescricao())
            ? uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getDescricao()), 500)
            : "Sem descrição.";

        String corpo =
            "📝 *Editar descrição do grupo*\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "Descrição atual:\n" +
                descricaoAtual + "\n\n" +
                "Agora envie a *nova descrição*.\n\n" +
                "Para remover a descrição, envie: *remover*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_desc_digitacao",
            uiHelper.msg().texto(whatsappAdmin, uiHelper.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoDescricaoGrupoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novaDescricao
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);
        validarSessao(idSessao);

        if (!sessaoAdminGrupoComplementoService.isAguardandoEditarDescricaoGrupo(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando edição da descrição do grupo");
        }

        if (!StringUtils.hasText(novaDescricao)) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_comp_grupo_desc_invalida",
                uiHelper.msg().texto(whatsappAdmin, uiHelper.msg().trunc("Envie uma descrição válida ou *remover* para limpar.", 1024))
            );
        }

        Long idGrupo = sessaoAdminGrupoComplementoService.getIdGrupoEditarDescricao(idSessao);
        int safeOffset = sessaoAdminGrupoComplementoService.getOffsetEditarDescricaoGrupo(idSessao);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        String texto = novaDescricao.trim();
        String descricaoFinal = "remover".equalsIgnoreCase(texto) ? null : texto;

        GrupoComplementoRequestDTO dto = montarDtoGrupoAtual(grupo);
        dto.setDescricao(descricaoFinal);

        grupoComplementoService.salvarGrupo(idGrupo, dto);
        sessaoAdminGrupoComplementoService.limparAguardandoEditarDescricaoGrupo(idSessao);

        String corpo =
            "✅ Descrição do grupo atualizada.\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_desc_ok",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + idGrupo + "|" + safeOffset, "🧩 Ver grupo"),
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPOS_MENU|" + safeOffset, "⬅️ Voltar")
                )
            )
        );
    }

    
    
    // =========================================================
    // GRUPOS: REGRAS
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuRegrasGrupo(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        int safeOffset = normalizarOffset(offsetGrupos);

        String corpo =
            "⚙️ *Regras do grupo*\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "Atual:\n" +
                "- Mínimo: *" + grupo.getMinimoSelecoes() + "*\n" +
                "- Máximo: *" + grupo.getMaximoSelecoes() + "*\n\n" +
                "Escolha uma configuração:";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_regras_menu",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Regras",
                "Regras",
                List.of(
                    uiHelper.row(montarComandoRegrasGrupo(idGrupo, safeOffset, 0, 1), "Opcional, 1 opção", "Mín 0 / Máx 1"),
                    uiHelper.row(montarComandoRegrasGrupo(idGrupo, safeOffset, 1, 1), "Obrigatório, 1", "Mín 1 / Máx 1"),
                    uiHelper.row(montarComandoRegrasGrupo(idGrupo, safeOffset, 0, 2), "Opcional, até 2", "Mín 0 / Máx 2"),
                    uiHelper.row(montarComandoRegrasGrupo(idGrupo, safeOffset, 1, 2), "Obrigatório, até 2", "Mín 1 / Máx 2"),
                    uiHelper.row(montarComandoRegrasGrupo(idGrupo, safeOffset, 0, 5), "Opcional, até 5", "Mín 0 / Máx 5"),
                    uiHelper.row(montarComandoRegrasGrupo(idGrupo, safeOffset, 1, 5), "Obrigatório, até 5", "Mín 1 / Máx 5"),
                    uiHelper.row("COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + idGrupo + "|" + safeOffset, "⬅️ Voltar", "Detalhes do grupo")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin aplicarRegrasGrupo(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos,
        Integer minimoSelecoes,
        Integer maximoSelecoes
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        Integer minimo = minimoSelecoes == null || minimoSelecoes < 0 ? 0 : minimoSelecoes;
        Integer maximo = maximoSelecoes == null || maximoSelecoes < 0 ? 0 : maximoSelecoes;

        if (maximo < minimo) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Máximo não pode ser menor que mínimo");
        }

        GrupoComplementoRequestDTO dto = montarDtoGrupoAtual(grupo);
        dto.setMinimoSelecoes(minimo);
        dto.setMaximoSelecoes(maximo);

        grupoComplementoService.salvarGrupo(idGrupo, dto);

        String corpo =
            "✅ Regras atualizadas.\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "- Mínimo: *" + minimo + "*\n" +
                "- Máximo: *" + maximo + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_regras_ok",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + idGrupo + "|" + normalizarOffset(offsetGrupos), "🧩 Ver grupo"),
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPOS_MENU|" + normalizarOffset(offsetGrupos), "⬅️ Voltar")
                )
            )
        );
    }

    // =========================================================
    // GRUPOS: EXCLUSÃO LÓGICA
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin confirmarExclusaoGrupo(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        List<ComplementoResponseDTO> complementos = grupoComplementoService.listarComplementos(idGrupo, false);

        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ *Excluir grupo de complementos*\n\n");
        sb.append("Grupo: *").append(uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80)).append("*\n\n");

        if (complementos == null || complementos.isEmpty()) {
            sb.append("Este grupo não possui complementos cadastrados.\n\n");
        } else {
            sb.append("Este grupo possui *").append(complementos.size()).append("* complemento(s):\n");
            sb.append(montarResumoComplementos(complementos)).append("\n\n");
        }

        sb.append("A exclusão será *lógica*.\n");
        sb.append("- O grupo não aparecerá mais na administração normal\n");
        sb.append("- Novas associações e seleções deixarão de usar este grupo\n");
        sb.append("- Pedidos já realizados continuarão preservados\n\n");
        sb.append("Deseja continuar?");

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_excluir_confirm",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(sb.toString(), 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_EXCLUIR|" + idGrupo + "|" + normalizarOffset(offsetGrupos), "🗑️ Excluir"),
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + idGrupo + "|" + normalizarOffset(offsetGrupos), "⬅️ Cancelar")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin excluirGrupo(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        grupoComplementoService.excluirGrupoLogicamente(idGrupo);

        String corpo =
            "🗑️ Grupo excluído com sucesso.\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "O histórico de pedidos foi preservado.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_excluido",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPOS_MENU|" + normalizarOffset(offsetGrupos), "🧩 Ver grupos"),
                    uiHelper.btn("COMANDO|ADMIN_CARDAPIO_MENU", "🧾 Cardápio")
                )
            )
        );
    }

    // =========================================================
    // COMPLEMENTOS DO GRUPO GLOBAL
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin listarComplementosGrupoGlobal(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos,
        Integer offsetComplementos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        validarGrupoDoEstabelecimento(estabelecimento, grupo);

        List<ComplementoResponseDTO> complementos =
            grupoComplementoService.listarComplementos(idGrupo, false);

        if (complementos.isEmpty()) {
            String corpo =
                "🧩 *Complementos do grupo*\n\n" +
                    "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                    "Este grupo ainda não possui complementos cadastrados.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_comp_grupo_complementos_vazio",
                uiHelper.msg().botoes(
                    whatsappAdmin,
                    uiHelper.msg().trunc(corpo, 1024),
                    List.of(
                	    uiHelper.btn(
                	        "COMANDO|ADMIN_COMP_COMPLEMENTO_NOVO_MENU|" + idGrupo + "|" + normalizarOffset(offsetGrupos),
                	        "➕ Novo complemento"
                	    ),
                	    uiHelper.btn(
                	        "COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + idGrupo + "|" + normalizarOffset(offsetGrupos),
                	        "⬅️ Voltar"
                	    )
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
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "Escolha um complemento para gerenciar.";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (ComplementoResponseDTO complemento : page) {
            String status = complemento.isAtivo() ? "Ativo" : "Inativo";
            String preco = uiHelper.msg().formatarMoeda(complemento.getPrecoAdicional());

            itens.add(uiHelper.row(
                "COMANDO|ADMIN_COMP_COMPLEMENTO_DETALHE|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|" + complemento.getId(),
                uiHelper.msg().trunc(complemento.getNome(), 24),
                uiHelper.msg().trunc(status + " ┃ +" + preco, 72)
            ));
        }

        if (temMais) {
            itens.add(uiHelper.row(
                "COMANDO|ADMIN_COMP_GRUPO_COMPLEMENTOS|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|" + endExclusive,
                "➡️ Mais opções",
                "Ver próxima página"
            ));
        }

        itens.add(uiHelper.row(
            "COMANDO|ADMIN_COMP_GRUPO_DETALHE|" + idGrupo + "|" + normalizarOffset(offsetGrupos),
            "⬅️ Voltar",
            "Detalhes do grupo"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_grupo_complementos",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Complementos",
                "Complementos",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarDetalheComplementoGlobal(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos,
        Long idComplemento
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarComplementoDoGrupo(complemento, idGrupo);

        String descricao = uiHelper.msg().safe(complemento.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String comandoStatus = complemento.isAtivo()
            ? "COMANDO|ADMIN_COMP_COMPLEMENTO_STATUS|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|" + idComplemento + "|0"
            : "COMANDO|ADMIN_COMP_COMPLEMENTO_STATUS|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|" + idComplemento + "|1";

        String corpo =
            "🧩 *Complemento*\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
                "*Opção:* " + uiHelper.msg().trunc(uiHelper.msg().safe(complemento.getNome()), 80) + "\n" +
                "*Descrição:* " + uiHelper.msg().trunc(descricao, 300) + "\n" +
                "*Preço adicional:* " + uiHelper.msg().formatarMoeda(complemento.getPrecoAdicional()) + "\n" +
                "*Status:* " + (complemento.isAtivo() ? "Ativo" : "Inativo") + "\n\n" +
                "O que deseja fazer?";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_complemento_detalhe",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Complemento",
                "Opções",
                List.of(
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_COMPLEMENTO_PRECO_MENU|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|" + idComplemento,
                        "Ajustar preço",
                        "Preço adicional"
                    ),
                    uiHelper.row(
                        comandoStatus,
                        complemento.isAtivo() ? "Desativar" : "Ativar",
                        complemento.isAtivo() ? "Ocultar esta opção" : "Liberar esta opção"
                    ),
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_GRUPO_COMPLEMENTOS|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|0",
                        "⬅️ Voltar",
                        "Complementos do grupo"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin alterarStatusComplementoGlobal(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos,
        Long idComplemento,
        boolean ativo
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarComplementoDoGrupo(complemento, idGrupo);

        grupoComplementoService.atualizarStatusComplemento(idComplemento, ativo);

        String corpo =
            "✅ Status atualizado.\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n" +
                "Complemento: *" + uiHelper.msg().trunc(uiHelper.msg().safe(complemento.getNome()), 80) + "*\n\n" +
                "*Novo status:* " + (ativo ? "Ativo" : "Inativo");

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_complemento_status_ok",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_COMP_COMPLEMENTO_DETALHE|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|" + idComplemento, "🧩 Ver opção"),
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_COMPLEMENTOS|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|0", "⬅️ Voltar")
                )
            )
        );
    }

	 // =========================================================
	 // COMPLEMENTOS: CRIAÇÃO POR DIGITAÇÃO
	 // =========================================================
	 public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroComplementoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Long idGrupo,
	    Integer offsetGrupos
	) {
    	
    	validarSessao(idSessao);
    	
    	uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
	    validarGrupoDoEstabelecimento(estabelecimento, grupo);

	    sessaoAdminGrupoComplementoService.marcarAguardandoNovoComplemento(
	        idSessao,
	        idGrupo,
	        offsetGrupos
	    );

	    String corpo =
	        "➕ *Novo complemento*\n\n" +
	            "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n\n" +
	            "Digite o *nome do complemento*.\n\n" +
	            "Exemplo: Granola";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_comp_complemento_novo_digitacao",
	        uiHelper.msg().botoes(
	            whatsappAdmin,
	            uiHelper.msg().trunc(corpo, 1024),
	            List.of(
	            	uiHelper.btn(
	                    "COMANDO|ADMIN_COMP_GRUPO_COMPLEMENTOS|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|0",
	                    "⬅️ Cancelar"
	                )
	            )
	        )
	    );
	}
    
    
    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroComplementoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String nomeComplemento
	) {
    	
    	validarSessao(idSessao);
    	
	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);

	    if (!StringUtils.hasText(nomeComplemento)) {
	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_comp_complemento_nome_invalido",
	            uiHelper.msg().texto(
	                whatsappAdmin,
	                uiHelper.msg().trunc(
	                    "Não consegui identificar o nome do complemento. Envie apenas o nome, por exemplo: *Granola*.",
	                    1024
	                )
	            )
	        );
	    }

	    if (!sessaoAdminGrupoComplementoService.isAguardandoNovoComplemento(idSessao)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo complemento");
	    }

	    Long idGrupo = sessaoAdminGrupoComplementoService.getIdGrupoNovoComplemento(idSessao);
	    int offsetGrupos = sessaoAdminGrupoComplementoService.getOffsetNovoComplementoGrupo(idSessao);

	    GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
	    validarGrupoDoEstabelecimento(estabelecimento, grupo);

	    ComplementoRequestDTO request = new ComplementoRequestDTO();
	    request.setIdGrupo(idGrupo);
	    request.setNome(nomeComplemento.trim());
	    request.setDescricao(null);
	    request.setPrecoAdicional(BigDecimal.ZERO);
	    request.setAtivo(true);

	    grupoComplementoService.salvarComplemento(null, request);

	    sessaoAdminGrupoComplementoService.limparAguardandoNovoComplemento(idSessao);

	    String corpo =
	        "✅ Complemento cadastrado.\n\n" +
	            "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n" +
	            "Complemento: *" + uiHelper.msg().trunc(uiHelper.msg().safe(nomeComplemento), 80) + "*";

	    
	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_comp_complemento_criado",
	        uiHelper.msg().botoes(
	            whatsappAdmin,
	            uiHelper.msg().trunc(corpo, 1024),
	            List.of(
	            	uiHelper.btn(
	                    "COMANDO|ADMIN_COMP_GRUPO_COMPLEMENTOS|" + idGrupo + "|" + offsetGrupos + "|0",
	                    "🧩 Ver lista"
	                ),
	            	uiHelper.btn(
	                    "COMANDO|ADMIN_COMP_COMPLEMENTO_NOVO_MENU|" + idGrupo + "|" + offsetGrupos,
	                    "➕ Novo"
	                )
	            )
	        )
	    );
	}
    
    // =========================================================
    // PREÇO DO COMPLEMENTO GLOBAL
    // =========================================================

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuPrecoComplementoGlobal(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos,
        Long idComplemento
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
        validarComplementoDoGrupo(complemento, idGrupo);

        String corpo =
            "💲 *Preço do complemento*\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n" +
                "Complemento: *" + uiHelper.msg().trunc(uiHelper.msg().safe(complemento.getNome()), 80) + "*\n\n" +
                "*Preço adicional atual:* " + uiHelper.msg().formatarMoeda(complemento.getPrecoAdicional()) + "\n\n" +
                "Escolha um ajuste:";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_complemento_preco_menu",
            uiHelper.msg().lista(
                whatsappAdmin,
                uiHelper.msg().truncWord(corpo, 1024),
                "Preço",
                "Preço",
                List.of(
                    uiHelper.row(montarComandoAplicarPrecoComplementoGlobal(idGrupo, offsetGrupos, idComplemento, 100), "+ R$ 1,00", "Aumentar"),
                    uiHelper.row(montarComandoAplicarPrecoComplementoGlobal(idGrupo, offsetGrupos, idComplemento, 200), "+ R$ 2,00", "Aumentar"),
                    uiHelper.row(montarComandoAplicarPrecoComplementoGlobal(idGrupo, offsetGrupos, idComplemento, 500), "+ R$ 5,00", "Aumentar"),
                    uiHelper.row(montarComandoAplicarPrecoComplementoGlobal(idGrupo, offsetGrupos, idComplemento, -100), "- R$ 1,00", "Diminuir"),
                    uiHelper.row(montarComandoAplicarPrecoComplementoGlobal(idGrupo, offsetGrupos, idComplemento, -200), "- R$ 2,00", "Diminuir"),
                    uiHelper.row(montarComandoAplicarPrecoComplementoGlobal(idGrupo, offsetGrupos, idComplemento, -500), "- R$ 5,00", "Diminuir"),
                    uiHelper.row(
                    	    "COMANDO|ADMIN_COMP_COMPLEMENTO_PRECO_MANUAL|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|" + idComplemento,
                    	    "Outro valor",
                    	    "Digitar preço manual"
                    	),
                    uiHelper.row(
                        "COMANDO|ADMIN_COMP_COMPLEMENTO_DETALHE|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|" + idComplemento,
                        "⬅️ Voltar",
                        "Detalhes do complemento"
                    )
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin aplicarDeltaPrecoComplementoGlobal(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idGrupo,
        Integer offsetGrupos,
        Long idComplemento,
        Integer deltaCentavos
    ) {

        uiHelper.validarBasico(estabelecimento, whatsappAdmin);

        GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
        Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

        validarGrupoDoEstabelecimento(estabelecimento, grupo);
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

        grupoComplementoService.atualizarPrecoComplemento(idComplemento, novoPreco);

        String corpo =
            "✅ Preço atualizado.\n\n" +
                "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n" +
                "Complemento: *" + uiHelper.msg().trunc(uiHelper.msg().safe(complemento.getNome()), 80) + "*\n\n" +
                "*Novo preço adicional:* " + uiHelper.msg().formatarMoeda(novoPreco);

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_comp_complemento_preco_ok",
            uiHelper.msg().botoes(
                whatsappAdmin,
                uiHelper.msg().trunc(corpo, 1024),
                List.of(
                    uiHelper.btn("COMANDO|ADMIN_COMP_COMPLEMENTO_DETALHE|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|" + idComplemento, "🧩 Ver opção"),
                    uiHelper.btn("COMANDO|ADMIN_COMP_GRUPO_COMPLEMENTOS|" + idGrupo + "|" + normalizarOffset(offsetGrupos) + "|0", "⬅️ Voltar")
                )
            )
        );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private GrupoComplementoRequestDTO montarDtoGrupoAtual(GrupoComplemento grupo) {

        GrupoComplementoRequestDTO dto = new GrupoComplementoRequestDTO();
        dto.setIdEstabelecimento(grupo.getEstabelecimento() == null ? null : grupo.getEstabelecimento().getId());
        dto.setNome(grupo.getNome());
        dto.setDescricao(grupo.getDescricao());
        dto.setMinimoSelecoes(grupo.getMinimoSelecoes());
        dto.setMaximoSelecoes(grupo.getMaximoSelecoes());
        dto.setAtivo(grupo.isAtivo());

        return dto;
    }

    private String montarResumoComplementos(List<ComplementoResponseDTO> complementos) {

        if (complementos == null || complementos.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int limite = Math.min(complementos.size(), 8);

        for (int i = 0; i < limite; i++) {
            ComplementoResponseDTO complemento = complementos.get(i);
            sb.append("- ")
                .append(uiHelper.msg().trunc(uiHelper.msg().safe(complemento.getNome()), 60))
                .append("\n");
        }

        if (complementos.size() > limite) {
            sb.append("- +").append(complementos.size() - limite).append(" outros\n");
        }

        return sb.toString().trim();
    }

    private String montarComandoRegrasGrupo(
        Long idGrupo,
        Integer offsetGrupos,
        Integer minimoSelecoes,
        Integer maximoSelecoes
    ) {

        return "COMANDO|ADMIN_COMP_GRUPO_REGRAS_APLICAR|" +
            idGrupo + "|" +
            normalizarOffset(offsetGrupos) + "|" +
            minimoSelecoes + "|" +
            maximoSelecoes;
    }

    private void validarSessao(Long idSessao) {

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
    }

    private void validarGrupoDoEstabelecimento(
        Estabelecimento estabelecimento,
        GrupoComplemento grupo
    ) {

        if (grupo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo de complementos não encontrado");
        }

        if (grupo.isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Grupo de complementos excluído");
        }

        if (grupo.getEstabelecimento() == null || grupo.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Grupo sem estabelecimento associado");
        }

        if (!Objects.equals(grupo.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupo não pertence ao estabelecimento");
        }
    }

    private void validarComplementoDoGrupo(
        Complemento complemento,
        Long idGrupo
    ) {

        if (complemento == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Complemento não encontrado");
        }

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

    private String montarComandoAplicarPrecoComplementoGlobal(
        Long idGrupo,
        Integer offsetGrupos,
        Long idComplemento,
        Integer deltaCentavos
    ) {

        return "COMANDO|ADMIN_COMP_COMPLEMENTO_PRECO_APLICAR|" +
            idGrupo + "|" +
            normalizarOffset(offsetGrupos) + "|" +
            idComplemento + "|" +
            deltaCentavos;
    }
    
    
    
    public AdministradorWhatsappResultados.ResultadoAdmin iniciarPrecoManualComplementoGlobalPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Long idGrupo,
	    Integer offsetGrupos,
	    Long idComplemento
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);
	    validarSessao(idSessao);

	    GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
	    Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

	    validarGrupoDoEstabelecimento(estabelecimento, grupo);
	    validarComplementoDoGrupo(complemento, idGrupo);

	    sessaoAdminComplementoService.marcarAguardandoNovoPrecoComplemento(
	        idSessao,
	        null,
	        null,
	        idGrupo,
	        idComplemento,
	        normalizarOffset(offsetGrupos)
	    );

	    String corpo =
	        "💲 *Preço do complemento*\n\n" +
	            "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n" +
	            "Complemento: *" + uiHelper.msg().trunc(uiHelper.msg().safe(complemento.getNome()), 80) + "*\n\n" +
	            "Preço atual: *" + uiHelper.msg().formatarMoeda(complemento.getPrecoAdicional()) + "*\n\n" +
	            "Agora envie apenas o *novo preço adicional*.\n\n" +
	            "Exemplos:\n" +
	            "- 0\n" +
	            "- 2,50\n" +
	            "- R$ 5,00";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_comp_complemento_preco_digitacao",
	        uiHelper.msg().texto(whatsappAdmin, uiHelper.msg().trunc(corpo, 1024))
	    );
	}

	public AdministradorWhatsappResultados.ResultadoAdmin concluirPrecoManualComplementoGlobalPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String textoDigitado
	) {

	    uiHelper.validarBasico(estabelecimento, whatsappAdmin);
	    validarSessao(idSessao);

	    if (!sessaoAdminComplementoService.isAguardandoNovoPrecoComplemento(idSessao)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo preço de complemento");
	    }

	    Long idGrupo = sessaoAdminComplementoService.getIdGrupoNovoPrecoComplemento(idSessao);
	    Long idComplemento = sessaoAdminComplementoService.getIdComplementoNovoPreco(idSessao);
	    int offsetGrupos = sessaoAdminComplementoService.getOffsetListaProdutoNovoPrecoComplemento(idSessao);

	    GrupoComplemento grupo = grupoComplementoService.buscarObrigatorio(idGrupo);
	    Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

	    validarGrupoDoEstabelecimento(estabelecimento, grupo);
	    validarComplementoDoGrupo(complemento, idGrupo);

	    BigDecimal novoPreco = parsePrecoDigitado(textoDigitado);

	    if (novoPreco.compareTo(BigDecimal.ZERO) < 0) {
	        novoPreco = BigDecimal.ZERO;
	    }

	    grupoComplementoService.atualizarPrecoComplemento(idComplemento, novoPreco);
	    sessaoAdminComplementoService.limparAguardandoNovoPrecoComplemento(idSessao);

	    String corpo =
	        "✅ Preço atualizado.\n\n" +
	            "Grupo: *" + uiHelper.msg().trunc(uiHelper.msg().safe(grupo.getNome()), 80) + "*\n" +
	            "Complemento: *" + uiHelper.msg().trunc(uiHelper.msg().safe(complemento.getNome()), 80) + "*\n\n" +
	            "*Novo preço adicional:* " + uiHelper.msg().formatarMoeda(novoPreco);

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_comp_complemento_preco_manual_ok",
	        uiHelper.msg().botoes(
	            whatsappAdmin,
	            uiHelper.msg().trunc(corpo, 1024),
	            List.of(
	                uiHelper.btn(
	                    "COMANDO|ADMIN_COMP_COMPLEMENTO_DETALHE|" + idGrupo + "|" + offsetGrupos + "|" + idComplemento,
	                    "🧩 Ver opção"
	                ),
	                uiHelper.btn(
	                    "COMANDO|ADMIN_COMP_GRUPO_COMPLEMENTOS|" + idGrupo + "|" + offsetGrupos + "|0",
	                    "⬅️ Voltar"
	                )
	            )
	        )
	    );
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