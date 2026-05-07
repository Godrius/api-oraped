package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.AdminGrupoComplementoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado aos grupos globais de complementos.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar listagem, cadastro, detalhe,
 * edição, regras, status, exclusão e complementos do grupo.
 *
 * Utilização:
 * Este service apenas interpreta comandos administrativos de grupos globais
 * e delega as regras operacionais para AdminGrupoComplementoService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminGrupoComplementoService {

    private final AdminGrupoComplementoService adminGrupoComplementoService;

    private final OrquestradorParseService parse;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ADMIN_COMP_GRUPOS_MENU": {
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(2));

                var r = adminGrupoComplementoService.listarGruposGlobais(
                    estabelecimento,
                    whatsappAdmin,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_NOVO_MENU": {
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(2));

                var r = adminGrupoComplementoService.iniciarCadastroGrupoPorDigitacao(
                    estabelecimento,
                    whatsappAdmin,
                    idSessao,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_DETALHE": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));

                var r = adminGrupoComplementoService.montarDetalheGrupoGlobal(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_COMPLEMENTOS": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                Integer offsetComplementos = parse.parseIntDefaultZero(cmd.getParte(4));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminGrupoComplementoService.listarComplementosGrupoGlobal(
                        estabelecimento,
                        whatsappAdmin,
                        idGrupo,
                        offsetGrupos,
                        offsetComplementos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_EDITAR_NOME_MENU": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));

                var r = adminGrupoComplementoService.iniciarAlteracaoNomeGrupoPorDigitacao(
                    estabelecimento,
                    whatsappAdmin,
                    idSessao,
                    idGrupo,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_EDITAR_DESC_MENU": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));

                var r = adminGrupoComplementoService.iniciarAlteracaoDescricaoGrupoPorDigitacao(
                    estabelecimento,
                    whatsappAdmin,
                    idSessao,
                    idGrupo,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_REGRAS_MENU": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));

                var r = adminGrupoComplementoService.montarMenuRegrasGrupo(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_REGRAS_APLICAR": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                Integer minimoSelecoes = parse.parseIntDefaultZero(cmd.getParte(4));
                Integer maximoSelecoes = parse.parseIntDefaultZero(cmd.getParte(5));

                var r = adminGrupoComplementoService.aplicarRegrasGrupo(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos,
                    minimoSelecoes,
                    maximoSelecoes
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_STATUS": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                boolean ativo = parse.parseIntDefaultZero(cmd.getParte(4)) == 1;

                var r = adminGrupoComplementoService.alterarStatusGrupoGlobal(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos,
                    ativo
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_EXCLUIR_CONFIRM": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));

                var r = adminGrupoComplementoService.confirmarExclusaoGrupo(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_GRUPO_EXCLUIR": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));

                var r = adminGrupoComplementoService.excluirGrupo(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_COMPLEMENTO_NOVO_MENU": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));

                var r = adminGrupoComplementoService.iniciarCadastroComplementoPorDigitacao(
                    estabelecimento,
                    whatsappAdmin,
                    idSessao,
                    idGrupo,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_COMPLEMENTO_DETALHE": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(4), "idComplemento");

                var r = adminGrupoComplementoService.montarDetalheComplementoGlobal(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos,
                    idComplemento
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_COMPLEMENTO_NOME_MENU": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(4), "idComplemento");

                var r = adminGrupoComplementoService.montarMenuAlterarNomeComplementoGlobal(
                    estabelecimento,
                    whatsappAdmin,
                    idSessao,
                    idGrupo,
                    offsetGrupos,
                    idComplemento
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_COMPLEMENTO_STATUS": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(4), "idComplemento");
                boolean ativo = parse.parseIntDefaultZero(cmd.getParte(5)) == 1;

                var r = adminGrupoComplementoService.alterarStatusComplementoGlobal(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos,
                    idComplemento,
                    ativo
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_COMPLEMENTO_PRECO_MENU": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(4), "idComplemento");

                var r = adminGrupoComplementoService.montarMenuPrecoComplementoGlobal(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos,
                    idComplemento
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_COMPLEMENTO_PRECO_MANUAL": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(4), "idComplemento");

                var r = adminGrupoComplementoService.iniciarPrecoManualComplementoGlobalPorDigitacao(
                    estabelecimento,
                    whatsappAdmin,
                    idSessao,
                    idGrupo,
                    offsetGrupos,
                    idComplemento
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_COMP_COMPLEMENTO_PRECO_APLICAR": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(4), "idComplemento");
                Integer deltaCentavos = parse.parseIntDefaultZeroAllowNegative(cmd.getParte(5));

                var r = adminGrupoComplementoService.aplicarDeltaPrecoComplementoGlobal(
                    estabelecimento,
                    whatsappAdmin,
                    idGrupo,
                    offsetGrupos,
                    idComplemento,
                    deltaCentavos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return null;
        }
    }
}