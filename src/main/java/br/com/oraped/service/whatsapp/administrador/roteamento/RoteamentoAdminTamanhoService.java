package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.AdminTamanhoCategoriaService;
import br.com.oraped.service.whatsapp.administrador.AdminTamanhoProdutoService;
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
 * Deve permanecer fino, delegando regras de categoria para AdminTamanhoCategoriaService
 * e regras de produto para AdminTamanhoProdutoService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminTamanhoService {

    private final AdminTamanhoCategoriaService adminTamanhoCategoriaService;
    private final AdminTamanhoProdutoService adminTamanhoProdutoService;
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
                    adminTamanhoCategoriaService.montarMenuTamanhosCategoria(
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
                    adminTamanhoCategoriaService.ativarTamanhosCategoria(
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
                    adminTamanhoCategoriaService.desativarTamanhosCategoria(
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
                    adminTamanhoCategoriaService.montarMenuOpcoesTamanhoCategoria(
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
                    adminTamanhoCategoriaService.montarMenuOpcaoTamanho(
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
                    adminTamanhoCategoriaService.iniciarCadastroOpcaoTamanho(
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
                    adminTamanhoCategoriaService.montarMenuTamanhosCategoria(
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
                    adminTamanhoCategoriaService.ativarOpcaoTamanho(
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
                    adminTamanhoCategoriaService.desativarOpcaoTamanho(
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
                    adminTamanhoCategoriaService.iniciarAlteracaoDescricaoOpcaoTamanho(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idOpcao,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }
            
            case "ADMIN_PROD_TAMANHOS_MENU": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(4));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoProdutoService.montarMenuTamanhosProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        offsetProdutos
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }
            
            case "ADMIN_PROD_TAMANHO_MENU": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Long idOpcaoTamanho = parse.parseLongObrigatorio(cmd.getParte(4), "idOpcaoTamanho");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(5));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoProdutoService.montarMenuTamanhoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        idOpcaoTamanho,
                        offsetProdutos,
                        null
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }
            
            case "ADMIN_PROD_TAMANHO_NOME_MENU": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Long idOpcaoTamanho = parse.parseLongObrigatorio(cmd.getParte(4), "idOpcaoTamanho");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(5));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoProdutoService.iniciarAlteracaoNomeTamanhoProdutoPorDigitacao(
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

            case "ADMIN_PROD_TAMANHO_ATIVAR": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Long idOpcaoTamanho = parse.parseLongObrigatorio(cmd.getParte(4), "idOpcaoTamanho");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(5));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoProdutoService.alterarStatusTamanhoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        idOpcaoTamanho,
                        offsetProdutos,
                        true
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_TAMANHO_DESATIVAR": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Long idOpcaoTamanho = parse.parseLongObrigatorio(cmd.getParte(4), "idOpcaoTamanho");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(5));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoProdutoService.alterarStatusTamanhoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        idOpcaoTamanho,
                        offsetProdutos,
                        false
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }
            
            case "ADMIN_PROD_TAMANHO_NOVO_MENU": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer offsetProduto = parse.parseIntDefaultZero(cmd.getParte(4));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoProdutoService.iniciarCadastroTamanhoProdutoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        idCategoria,
                        offsetProduto
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_TAMANHOS_PRECOS": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer offsetProdutos = parse.parseIntDefaultZero(cmd.getParte(4));
                Integer offsetTamanhos = parse.parseIntDefaultZero(cmd.getParte(5));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminTamanhoProdutoService.montarMenuPrecosTamanhoProduto(
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
                    adminTamanhoProdutoService.iniciarAlteracaoPrecoTamanhoProduto(
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