package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.AdminComplementoCategoriaService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado aos complementos por categoria.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar listagem, associação,
 * detalhe e desassociação de grupos de complementos vinculados à categoria.
 *
 * Utilização:
 * Este service apenas interpreta comandos administrativos de complementos por categoria
 * e delega as regras operacionais para AdminComplementoCategoriaService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminComplementoCategoriaService {

    private final AdminComplementoCategoriaService adminComplementoCategoriaService;

    private final OrquestradorParseService parse;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ADMIN_CAT_COMPLEMENTOS_MENU": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));

                var r = adminComplementoCategoriaService.montarMenuComplementosCategoria(
                    estabelecimento,
                    whatsappAdmin,
                    idCategoria,
                    offsetCategorias
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_COMP_CATEGORIAS": {
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(2));

                var r = adminComplementoCategoriaService.listarCategoriasParaComplementos(
                    estabelecimento,
                    whatsappAdmin,
                    offsetCategorias
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_COMP_ASSOCIADOS": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(4));

                var r = adminComplementoCategoriaService.listarGruposAssociadosCategoria(
                    estabelecimento,
                    whatsappAdmin,
                    idCategoria,
                    offsetCategorias,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_COMP_ASSOCIAR_MENU": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(4));

                var r = adminComplementoCategoriaService.listarGruposDisponiveisParaCategoria(
                    estabelecimento,
                    whatsappAdmin,
                    idCategoria,
                    offsetCategorias,
                    offsetGrupos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_COMP_ASSOCIAR": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(4), "idGrupo");

                var r = adminComplementoCategoriaService.associarGrupoCategoria(
                    estabelecimento,
                    whatsappAdmin,
                    idCategoria,
                    offsetCategorias,
                    idGrupo
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_COMP_GRUPO_DETALHE": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(4), "idGrupo");

                var r = adminComplementoCategoriaService.montarDetalheGrupoCategoria(
                    estabelecimento,
                    whatsappAdmin,
                    idCategoria,
                    offsetCategorias,
                    idGrupo
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_COMP_GRUPO_DESASSOCIAR_CONFIRM": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(4), "idGrupo");

                var r = adminComplementoCategoriaService.confirmarDesassociacaoGrupoCategoria(
                    estabelecimento,
                    whatsappAdmin,
                    idCategoria,
                    offsetCategorias,
                    idGrupo
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_COMP_GRUPO_DESASSOCIAR": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(4), "idGrupo");

                var r = adminComplementoCategoriaService.desassociarGrupoCategoria(
                    estabelecimento,
                    whatsappAdmin,
                    idCategoria,
                    offsetCategorias,
                    idGrupo
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return null;
        }
    }
}