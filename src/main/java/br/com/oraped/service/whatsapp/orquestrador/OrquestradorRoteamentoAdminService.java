// src/main/java/br/com/oraped/service/whatsapp/orquestrador/OrquestradorRoteamentoAdminService.java
package br.com.oraped.service.whatsapp.orquestrador;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.ComandoWhatsapp;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdministradorWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorRoteamentoAdminService {

    private final AdministradorWhatsappService administradorWhatsappService;
    private final OrquestradorParseService parse;
    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotearAdmin(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd.getAcao();

        if (!administradorWhatsappService.isAdminAtivo(estabelecimento, whatsappAdmin)) {
            return new RoteamentoResultado("admin_nao_autorizado", msg.texto(whatsappAdmin, "Sem permissão."));
        }

        switch (acao) {

            case "ADMIN_MENU": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuAdmin(estabelecimento, whatsappAdmin);
                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ABRIR_LOJA": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.abrirLoja(estabelecimento, whatsappAdmin);
                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_FECHAR_LOJA": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.fecharLoja(estabelecimento, whatsappAdmin);
                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_VER_PEDIDOS": {
                StatusPedido status = parse.parseStatusPedidoObrigatorio(cmd.getParte(2));
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.listarPedidosPorStatus(estabelecimento, whatsappAdmin, status, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PEDIDO_DETALHE": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarDetalhePedido(estabelecimento, whatsappAdmin, idPedido);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ACEITAR_PEDIDO":
                return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappService.AcaoPedidoAdmin.ACEITAR);

            case "ADMIN_RECUSAR_PEDIDO":
                return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappService.AcaoPedidoAdmin.RECUSAR);

            case "ADMIN_PREPARAR_PEDIDO":
                return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappService.AcaoPedidoAdmin.PREPARAR);

            case "ADMIN_CANCELAR_PEDIDO":
                return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappService.AcaoPedidoAdmin.CANCELAR);

            case "ADMIN_INICIAR_ENTREGA":
                return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappService.AcaoPedidoAdmin.INICIAR_ENTREGA);

            // ============== PRODUTOS (suspender/liberar) ==============
            case "ADMIN_SUSPENDER_PRODUTO_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.listarProdutosParaSuspender(estabelecimento, whatsappAdmin, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_LIBERAR_PRODUTO_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.listarProdutosParaLiberar(estabelecimento, whatsappAdmin, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_SUSPENDER_PRODUTO": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.suspenderProduto(estabelecimento, whatsappAdmin, idProduto);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_LIBERAR_PRODUTO": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.liberarProduto(estabelecimento, whatsappAdmin, idProduto);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            // ============== CARDÁPIO ==============
            case "ADMIN_CARDAPIO_MENU": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuCardapio(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CARDAPIO_PRODUTOS_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuCardapioProdutos(estabelecimento, whatsappAdmin, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CARDAPIO_PRODUTO": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuAcoesProduto(estabelecimento, whatsappAdmin, idProduto, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            // ============== PRODUTO: PREÇO ==============
            case "ADMIN_PROD_PRECO_MENU": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuAjustePrecoProduto(estabelecimento, whatsappAdmin, idProduto, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_PRECO_APLICAR": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer deltaCentavos = parse.parseIntDefaultZeroAllowNegative(cmd.getParte(3));
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

                AdministradorWhatsappService.ResultadoAdminPreco r =
                    administradorWhatsappService.aplicarDeltaPrecoProduto(
                        estabelecimento,
                        whatsappAdmin,
                        idProduto,
                        deltaCentavos,
                        offset
                    );

                String corpo =
                    "✅ Preço atualizado!\n\n" +
                        "*" + msg.trunc(msg.safe(r.nomeProduto), 80) + "*\n" +
                        msg.trunc(msg.safe(r.descricaoProduto), 500) + "\n\n" +
                        "*Novo preço:* " + msg.formatarMoeda(r.novoPreco);

                MensagemWhatsappSaidaDTO confirmacao = msg.texto(whatsappAdmin, msg.trunc(corpo, 1024));
                List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

                return new RoteamentoResultado("admin_preco_atualizado", confirmacao, extras);
            }

            case "ADMIN_PROD_PRECO_MANUAL": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.iniciarPrecoManualProdutoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            // ============== PRODUTO: NOME / DESCRIÇÃO (modo digitação) ==============
            case "ADMIN_PROD_NOME_MENU": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.iniciarAlteracaoNomeProdutoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_DESC_MENU": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.iniciarAlteracaoDescricaoProdutoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idProduto,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            // ============== PRODUTO: EXCLUSÃO ==============
            case "ADMIN_PROD_EXCLUIR_CONFIRM": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.confirmarExclusaoProduto(estabelecimento, whatsappAdmin, idProduto, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PROD_EXCLUIR": {
                Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.excluirProduto(estabelecimento, whatsappAdmin, idProduto, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            // ============== MARCAS ==============
            case "ADMIN_CARDAPIO_MARCAS_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuMarcas(estabelecimento, whatsappAdmin, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_DETALHE": {
                Long idMarca = parse.parseLongObrigatorio(cmd.getParte(2), "idMarca");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarDetalheMarca(estabelecimento, whatsappAdmin, idMarca, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_NOVA_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.iniciarCadastroMarcaPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_EDITAR_MENU": {
                Long idMarca = parse.parseLongObrigatorio(cmd.getParte(2), "idMarca");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.iniciarAlteracaoNomeMarcaPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idMarca,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_EXCLUIR_CONFIRM": {
                Long idMarca = parse.parseLongObrigatorio(cmd.getParte(2), "idMarca");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.confirmarExclusaoMarca(estabelecimento, whatsappAdmin, idMarca, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_MARCA_EXCLUIR": {
                Long idMarca = parse.parseLongObrigatorio(cmd.getParte(2), "idMarca");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.excluirMarca(estabelecimento, whatsappAdmin, idMarca, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            //===============ENTREGAS=================
            case "ADMIN_ENTREGAS_MENU": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuEntregas(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_CEP_MENU":
            case "ADMIN_ENTREGAS_CEP_DIGITAR": {

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.iniciarCadastroCepLojaPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_TAXAS_MENU": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuTaxasEntrega(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_TAXA_PADRAO_MENU": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuTaxaPadrao(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_BAIRROS_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuTaxaPorBairros(estabelecimento, whatsappAdmin, offset);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_BAIRRO_SELECIONAR": {
                Long idBairro = parse.parseLongObrigatorio(cmd.getParte(2), "idBairro");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.iniciarCadastroTaxaEntregaBairroPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idBairro,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_TAXA_PADRAO_DIGITAR": {
                Integer offsetVoltar = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.iniciarCadastroTaxaEntregaPadraoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        offsetVoltar
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }
            
            
            // ============== RELATÓRIOS ==============
            case "ADMIN_RELATORIOS_MENU": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuRelatorios(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_RELATORIOS_HOJE": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.gerarRelatorioHoje(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_RELATORIOS_ONTEM": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.gerarRelatorioOntem(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_RELATORIOS_SEMANA": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.gerarRelatorioSemana(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_RELATORIOS_MES": {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.gerarRelatorioMes(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default: {
                AdministradorWhatsappService.ResultadoAdmin r =
                    administradorWhatsappService.montarMenuAdmin(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado("admin_acao_desconhecida", r.mensagem);
            }
        }
    }

    private RoteamentoResultado executarAcaoPedidoAdmin(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        ComandoWhatsapp cmd,
        AdministradorWhatsappService.AcaoPedidoAdmin acao
    ) {

        Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");

        var r = administradorWhatsappService.executarAcaoPedido(
            estabelecimento,
            whatsappAdmin,
            idPedido,
            acao
        );

        List<MensagemWhatsappSaidaDTO> extras = new ArrayList<>();

        if (r.mensagemCliente != null) {
            extras.add(r.mensagemCliente);
        } else if (StringUtils.hasText(r.whatsappCliente) && StringUtils.hasText(r.textoCliente)) {
            extras.add(msg.texto(r.whatsappCliente, r.textoCliente));
        }

        return new RoteamentoResultado(r.admin.chave, r.admin.mensagem, extras);
        
    }
}