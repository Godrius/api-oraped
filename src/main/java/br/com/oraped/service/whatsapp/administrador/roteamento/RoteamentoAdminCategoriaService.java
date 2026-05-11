package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.AdminCategoriaService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado às categorias do cardápio.
 *
 * Aplicação:
 * Recebe comandos de navegação e cadastro inicial de categorias.
 *
 * Utilização:
 * Deve permanecer fino, delegando as regras para AdminCategoriaService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminCategoriaService {

    private final AdminCategoriaService adminCategoriaService;
    private final OrquestradorParseService parse;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ADMIN_CARDAPIO_CATEGORIAS_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminCategoriaService.montarMenuCategorias(
                        estabelecimento,
                        whatsappAdmin,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU": {
                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminCategoriaService.montarMenuCategoria(
                        estabelecimento,
                        whatsappAdmin,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CATEGORIA_NOVA_MENU": {
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminCategoriaService.iniciarCadastroCategoriaPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        offsetCategorias
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }
            
            case "ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_LISTA": {
                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminCategoriaService.montarListaProdutosPorCategoria(
                        estabelecimento,
                        whatsappAdmin,
                        idCategoria,
                        offset,
                        null
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return null;
        }
    }
}