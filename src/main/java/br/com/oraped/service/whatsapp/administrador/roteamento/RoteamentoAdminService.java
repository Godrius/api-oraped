package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.MenuAdminService;
import br.com.oraped.service.whatsapp.administrador.ValidadorAdminService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar a entrada do roteamento administrativo no WhatsApp.
 *
 * Aplicação:
 * Recebe comandos ADMIN_* e delega para roteamentos especializados por domínio.
 *
 * Utilização:
 * Deve permanecer fino, sem regras específicas de loja, pedido, categoria,
 * produto, tamanho, entrega, marca, relatório ou complementos.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminService {

    private final ValidadorAdminService validadorAdminService;
    private final MenuAdminService menuAdminService;

    private final RoteamentoAdminLojaService roteamentoLojaAdminService;
    private final RoteamentoAdminPedidoService roteamentoPedidoAdminService;
    private final RoteamentoAdminCategoriaService roteamentoCategoriaAdminService;
    private final RoteamentoAdminProdutoService roteamentoProdutoAdminService;
    private final RoteamentoAdminTamanhoService roteamentoTamanhoAdminService;
    private final RoteamentoAdminComplementoCategoriaService roteamentoComplementoCategoriaAdminService;
    private final RoteamentoAdminGrupoComplementoService roteamentoGrupoComplementoAdminService;
    private final RoteamentoAdminComplementoProdutoService roteamentoComplementoProdutoAdminService;
    private final RoteamentoAdminMarcaService roteamentoMarcaAdminService;
    private final RoteamentoAdminEntregaService roteamentoEntregaAdminService;
    private final RoteamentoAdminRelatorioService roteamentoRelatorioAdminService;

    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotearAdmin(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        if (!validadorAdminService.isAdminAtivo(estabelecimento, whatsappAdmin)) {
            return new RoteamentoResultado(
                "admin_nao_autorizado",
                msg.texto(whatsappAdmin, "Sem permissão.")
            );
        }

        RoteamentoResultado resultado = rotearPorDominio(
            estabelecimento,
            whatsappAdmin,
            idSessao,
            cmd,
            acao
        );

        if (resultado != null) {
            return resultado;
        }

        AdministradorWhatsappResultados.ResultadoAdmin r =
            menuAdminService.montarMenuAdmin(estabelecimento, whatsappAdmin);

        return new RoteamentoResultado("admin_acao_desconhecida", r.mensagem);
    }

    private RoteamentoResultado rotearPorDominio(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        ComandoWhatsapp cmd,
        String acao
    ) {

        if (isComandoMenuCardapio(acao)) {
            AdministradorWhatsappResultados.ResultadoAdmin r =
                menuAdminService.montarMenuCardapio(estabelecimento, whatsappAdmin);

            return new RoteamentoResultado(r.chave, r.mensagem);
        }

        if (isComandoLoja(acao)) {
            return roteamentoLojaAdminService.rotear(estabelecimento, whatsappAdmin, cmd);
        }

        if (isComandoPedido(acao)) {
            return roteamentoPedidoAdminService.rotear(estabelecimento, whatsappAdmin, cmd);
        }

        if (isComandoCategoria(acao)) {
            return roteamentoCategoriaAdminService.rotear(estabelecimento, whatsappAdmin, idSessao, cmd);
        }

        if (isComandoProduto(acao)) {
            return roteamentoProdutoAdminService.rotear(estabelecimento, whatsappAdmin, idSessao, cmd);
        }

        if (isComandoTamanho(acao)) {
            return roteamentoTamanhoAdminService.rotear(estabelecimento, whatsappAdmin, idSessao, cmd);
        }

        if (isComandoComplementoCategoria(acao)) {
            return roteamentoComplementoCategoriaAdminService.rotear(estabelecimento, whatsappAdmin, cmd);
        }

        if (isComandoGrupoComplemento(acao)) {
            return roteamentoGrupoComplementoAdminService.rotear(estabelecimento, whatsappAdmin, idSessao, cmd);
        }

        if (isComandoComplementoProduto(acao)) {
            return roteamentoComplementoProdutoAdminService.rotear(estabelecimento, whatsappAdmin, cmd);
        }

        if (isComandoMarca(acao)) {
            return roteamentoMarcaAdminService.rotear(estabelecimento, whatsappAdmin, idSessao, cmd);
        }

        if (isComandoEntrega(acao)) {
            return roteamentoEntregaAdminService.rotear(estabelecimento, whatsappAdmin, idSessao, cmd);
        }

        if (isComandoRelatorio(acao)) {
            return roteamentoRelatorioAdminService.rotear(estabelecimento, whatsappAdmin, cmd);
        }

        return null;
    }

    private boolean isComandoMenuCardapio(String acao) {
        return "ADMIN_CARDAPIO_MENU".equals(acao);
    }

    private boolean isComandoLoja(String acao) {
        return "ADMIN_MENU".equals(acao)
            || "ADMIN_ABRIR_LOJA".equals(acao)
            || "ADMIN_FECHAR_LOJA".equals(acao);
    }

    private boolean isComandoPedido(String acao) {
        return "ADMIN_VER_PEDIDOS".equals(acao)
            || "ADMIN_PEDIDO_DETALHE".equals(acao)
            || "ADMIN_ACEITAR_PEDIDO".equals(acao)
            || "ADMIN_RECUSAR_PEDIDO".equals(acao)
            || "ADMIN_PREPARAR_PEDIDO".equals(acao)
            || "ADMIN_CANCELAR_PEDIDO".equals(acao)
            || "ADMIN_INICIAR_ENTREGA".equals(acao);
    }

    private boolean isComandoCategoria(String acao) {
        return "ADMIN_CARDAPIO_CATEGORIAS_MENU".equals(acao)
            || "ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU".equals(acao)
            || "ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_LISTA".equals(acao)
            || "ADMIN_CATEGORIA_NOVA_MENU".equals(acao);
    }

    private boolean isComandoProduto(String acao) {
        return "ADMIN_CARDAPIO_PRODUTO".equals(acao)
            || "ADMIN_PRODUTO_NOVO_CATEGORIA_MENU".equals(acao)
            || "ADMIN_PRODUTO_NOVO_MENU".equals(acao)
            || acao != null && acao.startsWith("ADMIN_PROD_PRECO")
            || acao != null && acao.startsWith("ADMIN_PROD_NOME")
            || acao != null && acao.startsWith("ADMIN_PROD_DESC")
            || acao != null && acao.startsWith("ADMIN_PROD_FOTO")
            || acao != null && acao.startsWith("ADMIN_PROD_EXCLUIR")
            || "ADMIN_SUSPENDER_PRODUTO_MENU".equals(acao)
            || "ADMIN_LIBERAR_PRODUTO_MENU".equals(acao)
            || "ADMIN_SUSPENDER_PRODUTO".equals(acao)
            || "ADMIN_LIBERAR_PRODUTO".equals(acao);
    }

    private boolean isComandoTamanho(String acao) {
        return acao != null && acao.startsWith("ADMIN_CAT_TAMANHOS")
            || acao != null && acao.startsWith("ADMIN_TAM_OPCAO")
            || "ADMIN_PROD_TAMANHOS_PRECOS".equals(acao)
            || "ADMIN_PROD_TAM_PRECO_MENU".equals(acao);
    }

    private boolean isComandoComplementoCategoria(String acao) {
        return acao != null && acao.startsWith("ADMIN_CAT_COMP")
            || "ADMIN_CAT_COMPLEMENTOS_MENU".equals(acao);
    }

    private boolean isComandoGrupoComplemento(String acao) {
        return acao != null && acao.startsWith("ADMIN_COMP_");
    }

    private boolean isComandoComplementoProduto(String acao) {
        return acao != null && acao.startsWith("ADMIN_PROD_COMP");
    }

    private boolean isComandoMarca(String acao) {
        return "ADMIN_CARDAPIO_MARCAS_MENU".equals(acao)
            || acao != null && acao.startsWith("ADMIN_MARCA");
    }

    private boolean isComandoEntrega(String acao) {
        return acao != null && acao.startsWith("ADMIN_ENTREGAS");
    }

    private boolean isComandoRelatorio(String acao) {
        return acao != null && acao.startsWith("ADMIN_RELATORIOS");
    }
}