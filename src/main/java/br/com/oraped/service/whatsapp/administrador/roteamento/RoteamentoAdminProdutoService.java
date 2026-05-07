package br.com.oraped.service.whatsapp.administrador.roteamento;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdminDisponibilidadeProdutoService;
import br.com.oraped.service.whatsapp.administrador.AdminProdutoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado ao cadastro e edição de produtos.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar:
 * - cadastro de produtos
 * - alteração de preço
 * - alteração de nome
 * - alteração de descrição
 * - alteração de foto
 * - exclusão de produtos
 *
 * Utilização:
 * Deve permanecer fino, delegando as regras para AdminProdutoService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminProdutoService {

    private final AdminProdutoService adminProdutoService;
    private final OrquestradorParseService parse;
    private final WhatsappMensagemFactory msg;
    private final AdminDisponibilidadeProdutoService adminDisponibilidadeProdutoService;
    
    
    
    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ADMIN_CARDAPIO_PRODUTO": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.montarMenuAcoesProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        offset,
                        null
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PRODUTO_NOVO_CATEGORIA_MENU": {
                Integer offsetCategorias = parse.parseIntDefaultZero(
                    cmd.getParte(2)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.listarCategoriasParaNovoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        offsetCategorias
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PRODUTO_NOVO_MENU": {
                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idCategoria"
                );

                Integer offsetCategorias = parse.parseIntDefaultZero(
                    cmd.getParte(3)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.iniciarCadastroProdutoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idCategoria,
                        offsetCategorias
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_PRECO_MENU": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.montarMenuAjustePrecoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_PRECO_APLICAR": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Integer deltaCentavos =
                    parse.parseIntDefaultZeroAllowNegative(
                        cmd.getParte(3)
                    );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(4),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(5)
                );

                AdministradorWhatsappResultados.ResultadoAdminPreco r =
                    adminProdutoService.aplicarDeltaPrecoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        deltaCentavos,
                        idCategoria,
                        offset
                    );

                String corpo =
                    "✅ Preço atualizado!\n\n" +
                        "*" + msg.trunc(msg.safe(r.nomeProduto), 80) + "*\n" +
                        msg.trunc(msg.safe(r.descricaoProduto), 500) + "\n\n" +
                        "*Novo preço:* " + msg.formatarMoeda(r.novoPreco);

                MensagemWhatsappSaidaDTO confirmacao = msg.texto(
                    whatsappAdmin,
                    msg.trunc(corpo, 1024)
                );

                List<MensagemWhatsappSaidaDTO> extras =
                    List.of(r.admin.mensagem);

                return new RoteamentoResultado(
                    "admin_preco_atualizado",
                    confirmacao,
                    extras
                );
            }

            case "ADMIN_PROD_PRECO_MANUAL": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.iniciarPrecoManualProdutoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_NOME_MENU": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.iniciarAlteracaoNomeProdutoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_DESC_MENU": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.iniciarAlteracaoDescricaoProdutoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_FOTO_MENU": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.iniciarAlteracaoFotoProdutoPorEnvioImagem(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_FOTO_REMOVER_CONFIRM": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.confirmarRemocaoFotoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_FOTO_REMOVER": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.removerFotoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_EXCLUIR_CONFIRM": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.confirmarExclusaoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_EXCLUIR": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                Long idCategoria = parse.parseLongObrigatorio(
                    cmd.getParte(3),
                    "idCategoria"
                );

                Integer offset = parse.parseIntDefaultZero(
                    cmd.getParte(4)
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminProdutoService.excluirProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        idCategoria,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }
            
            case "ADMIN_SUSPENDER_PRODUTO_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminDisponibilidadeProdutoService.listarProdutosParaSuspender(
                        estabelecimento,
                        whatsappAdmin,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_LIBERAR_PRODUTO_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminDisponibilidadeProdutoService.listarProdutosParaLiberar(
                        estabelecimento,
                        whatsappAdmin,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_SUSPENDER_PRODUTO": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminDisponibilidadeProdutoService.suspenderProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_LIBERAR_PRODUTO": {
                Long idProduto = parse.parseLongObrigatorio(
                    cmd.getParte(2),
                    "idProduto"
                );

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminDisponibilidadeProdutoService.liberarProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return null;
        }
    }
}