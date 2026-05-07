package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.AdminTamanhoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminTamanhoService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado aos tamanhos do cardápio.
 *
 * Aplicação:
 * Recebe comandos de ativação de tamanhos na categoria, manutenção das opções
 * de tamanho e configuração de preço por tamanho no produto.
 *
 * Utilização:
 * Deve permanecer fino, delegando as regras para AdminTamanhoService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminTamanhoService {

    private final AdminTamanhoService adminTamanhoService;
    private final OrquestradorParseService parse;
    private final SessaoWhatsappAdminTamanhoService sessaoAdminTamanhoService;
    
    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ADMIN_CAT_TAMANHOS_MENU": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.montarMenuTamanhosCategoria(
                        estabelecimento,
                        whatsappAdmin,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_TAMANHOS_ATIVAR": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.ativarTamanhosCategoria(
                        estabelecimento,
                        whatsappAdmin,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_TAMANHOS_DESATIVAR": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.desativarTamanhosCategoria(
                        estabelecimento,
                        whatsappAdmin,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_TAMANHOS_OPCOES": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(3));
                Integer offsetOpcoes = parse.parseIntDefaultZero(cmd.getParte(4));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.montarMenuOpcoesTamanhoCategoria(
                        estabelecimento,
                        whatsappAdmin,
                        idCategoria,
                        offsetProdutos,
                        offsetOpcoes,
                        ""
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_TAM_OPCAO_MENU": {
                Long idOpcao = parse.parseLongObrigatorio(cmd.getParte(2), "idOpcao");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(4));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.montarMenuOpcaoTamanho(
                        estabelecimento,
                        whatsappAdmin,
                        idOpcao,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_TAM_OPCAO_NOVA_MENU": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.iniciarCadastroOpcaoTamanho(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }
            
            case "ADMIN_TAM_OPCAO_CANCELAR": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(3));

                // Cancela a digitação pendente para impedir que a próxima mensagem vire um tamanho indevido.
                sessaoAdminTamanhoService.limparAguardandoNovaOpcaoTamanho(idSessao);

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.montarMenuTamanhosCategoria(
                        estabelecimento,
                        whatsappAdmin,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_TAM_OPCAO_ATIVAR": {
                Long idOpcao = parse.parseLongObrigatorio(cmd.getParte(2), "idOpcao");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(4));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.ativarOpcaoTamanho(
                        estabelecimento,
                        whatsappAdmin,
                        idOpcao,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_TAM_OPCAO_DESATIVAR": {
                Long idOpcao = parse.parseLongObrigatorio(cmd.getParte(2), "idOpcao");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(4));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.desativarOpcaoTamanho(
                        estabelecimento,
                        whatsappAdmin,
                        idOpcao,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_TAM_OPCAO_DESC_MENU": {
                Long idOpcao = parse.parseLongObrigatorio(cmd.getParte(2), "idOpcao");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(4));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.iniciarAlteracaoDescricaoOpcaoTamanho(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idOpcao,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_TAMANHOS_PRECOS": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(4));
                Integer offsetTamanhos = parse.parseIntDefaultZero(cmd.getParte(5));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.montarMenuPrecosTamanhoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        offsetProdutos,
                        offsetTamanhos,
                        null
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_TAM_PRECO_MENU": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Long idOpcaoTamanho = parse.parseLongObrigatorio(cmd.getParte(4), "idOpcaoTamanho");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(5));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoService.iniciarAlteracaoPrecoTamanhoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        idCategoria,
                        idOpcaoTamanho,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return null;
        }
    }
}