package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.AdminMarcaService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado às marcas.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar listagem,
 * detalhe, cadastro, edição e exclusão de marcas.
 *
 * Utilização:
 * Este service atua apenas como camada de roteamento,
 * mantendo as regras operacionais em AdminMarcaService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminMarcaService {

    private final AdminMarcaService adminMarcaService;

    private final OrquestradorParseService parse;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ADMIN_CARDAPIO_MARCAS_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminMarcaService.montarMenuMarcas(
                        estabelecimento,
                        whatsappAdmin,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_DETALHE": {
                Long idMarca = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idMarca"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(3)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminMarcaService.montarDetalheMarca(
                        estabelecimento,
                        whatsappAdmin,
                        idMarca,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_NOVA_MENU": {
                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(2)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminMarcaService.iniciarCadastroMarcaPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_EDITAR_MENU": {
                Long idMarca = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idMarca"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(3)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminMarcaService.iniciarAlteracaoNomeMarcaPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idMarca,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_EXCLUIR_CONFIRM": {
                Long idMarca = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idMarca"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(3)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminMarcaService.confirmarExclusaoMarca(
                        estabelecimento,
                        whatsappAdmin,
                        idMarca,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_EXCLUIR": {
                Long idMarca = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idMarca"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(3)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminMarcaService.excluirMarca(
                        estabelecimento,
                        whatsappAdmin,
                        idMarca,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return null;
        }
    }
}