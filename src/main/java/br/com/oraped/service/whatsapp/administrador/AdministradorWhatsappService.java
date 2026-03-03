package br.com.oraped.service.whatsapp.administrador;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.AdministradorEstabelecimento;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.Pedido;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.enums.TipoPeriodoRelatorio;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdministradorWhatsappService {

    private final AdministradorWhatsappSupport sup;

    private final AdministradorWhatsappMenuAdminService adminMenuService;
    private final AdministradorWhatsappPedidoService pedidoService;
    private final AdministradorWhatsappCardapioService cardapioProdutoService;
    private final AdministradorWhatsappMarcaService marcaService;
    private final AdministradorWhatsappDisponibilidadeProdutoService disponibilidadeService;
    private final AdministradorWhatsappEntregaService entregaCepService;
    private final AdministradorWhatsappRelatorioService relatorioService;
    
    // =========================================================
    // Tipos (mantidos para compatibilidade com o Orquestrador)
    // =========================================================

    public static class ResultadoAdmin extends AdministradorWhatsappResultados.ResultadoAdmin {
        public ResultadoAdmin(String chave, MensagemWhatsappSaidaDTO mensagem) { super(chave, mensagem); }
    }

    public static class ResultadoAdminPreco extends AdministradorWhatsappResultados.ResultadoAdminPreco {
        public ResultadoAdminPreco(ResultadoAdmin admin, java.math.BigDecimal novoPreco, String nomeProduto, String descricaoProduto) {
            super(admin, novoPreco, nomeProduto, descricaoProduto);
        }
    }

    public static class ResultadoAdminMarca extends AdministradorWhatsappResultados.ResultadoAdminMarca {
        public ResultadoAdminMarca(ResultadoAdmin admin, Long idMarca, String nomeMarca) { super(admin, idMarca, nomeMarca); }
    }

    public enum AcaoPedidoAdmin {
        ACEITAR, RECUSAR, PREPARAR, CANCELAR, INICIAR_ENTREGA
    }

    public static class ResultadoAdminAcaoPedido extends AdministradorWhatsappResultados.ResultadoAdminAcaoPedido {

        public ResultadoAdminAcaoPedido(
            ResultadoAdmin admin,
            String whatsappCliente,
            String textoCliente,
            MensagemWhatsappSaidaDTO mensagemCliente
        ) {
            super(admin, whatsappCliente, textoCliente, mensagemCliente);
        }
    }

    // =========================================================
    // ADMINISTRADORES: LISTAGEM + PERMISSÃO (mantido aqui)
    // =========================================================

    public List<String> listarWhatsappsAdministradoresAtivos(Estabelecimento e) {

        if (e == null || e.getAdministradores() == null) return List.of();

        return e.getAdministradores().stream()
            .filter(Objects::nonNull)
            .filter(AdministradorEstabelecimento::isAtivo)
            .map(AdministradorEstabelecimento::getWhatsapp)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(sup.msg()::normalizarSomenteDigitos)
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.toList());
    }

    public boolean isAdminAtivo(Estabelecimento e, String whatsapp) {

        if (e == null || e.getAdministradores() == null) return false;

        String w = sup.msg().normalizarSomenteDigitos(whatsapp);
        if (!StringUtils.hasText(w)) return false;

        return e.getAdministradores().stream()
            .filter(Objects::nonNull)
            .filter(AdministradorEstabelecimento::isAtivo)
            .map(AdministradorEstabelecimento::getWhatsapp)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(sup.msg()::normalizarSomenteDigitos)
            .anyMatch(w::equals);
    }

    // =========================================================
    // MENU PRINCIPAL DO ADMIN
    // =========================================================

    public ResultadoAdmin montarMenuAdmin(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = adminMenuService.montarMenuAdmin(estabelecimento, whatsappAdmin);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin abrirLoja(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = adminMenuService.abrirLoja(estabelecimento, whatsappAdmin);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin fecharLoja(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = adminMenuService.fecharLoja(estabelecimento, whatsappAdmin);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    // =========================================================
    // NOTIFICAÇÃO PARA ADMIN (NOVO PEDIDO)
    // =========================================================

    public MensagemWhatsappSaidaDTO montarNotificacaoPedidoParaAdmin(
        String whatsappAdmin,
        Long idPedido,
        String whatsappCliente,
        String endereco,
        String observacoes,
        String resumoItens,
        java.math.BigDecimal total
    ) {
        return pedidoService.montarNotificacaoPedidoParaAdmin(
            whatsappAdmin, idPedido, whatsappCliente, endereco, observacoes, resumoItens, total
        );
    }

    // =========================================================
    // NOTIFICAÇÃO PARA ADMINS (MUDANÇA NO PEDIDO PELO CLIENTE)
    // =========================================================

    public List<MensagemWhatsappSaidaDTO> montarNotificacoesMudancaPedidoParaAdmins(
        Estabelecimento estabelecimento,
        Long idPedido,
        String whatsappCliente,
        String motivo,
        StatusPedido statusAtual,
        String resumoItens,
        java.math.BigDecimal total
    ) {
        List<String> admins = listarWhatsappsAdministradoresAtivos(estabelecimento);
        return pedidoService.montarNotificacoesMudancaPedidoParaAdmins(
            estabelecimento,
            idPedido,
            whatsappCliente,
            motivo,
            statusAtual,
            resumoItens,
            total,
            admins
        );
    }

    // =========================================================
    // PEDIDOS: LISTAGEM / DETALHE / AÇÕES
    // =========================================================

    public ResultadoAdmin listarPedidosPorStatus(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        StatusPedido status,
        Integer offset
    ) {
        var r = pedidoService.listarPedidosPorStatus(estabelecimento, whatsappAdmin, status, offset);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin montarDetalhePedido(Estabelecimento estabelecimento, String whatsappAdmin, Long idPedido) {
        var r = pedidoService.montarDetalhePedido(estabelecimento, whatsappAdmin, idPedido);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdminAcaoPedido executarAcaoPedido(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idPedido,
        AcaoPedidoAdmin acao
    ) {
        AdministradorWhatsappResultados.AcaoPedidoAdmin a;
        switch (acao) {
            case ACEITAR: a = AdministradorWhatsappResultados.AcaoPedidoAdmin.ACEITAR; break;
            case RECUSAR: a = AdministradorWhatsappResultados.AcaoPedidoAdmin.RECUSAR; break;
            case PREPARAR: a = AdministradorWhatsappResultados.AcaoPedidoAdmin.PREPARAR; break;
            case CANCELAR: a = AdministradorWhatsappResultados.AcaoPedidoAdmin.CANCELAR; break;
            case INICIAR_ENTREGA: a = AdministradorWhatsappResultados.AcaoPedidoAdmin.INICIAR_ENTREGA; break;
            default: a = AdministradorWhatsappResultados.AcaoPedidoAdmin.ACEITAR;
        }

        var r = pedidoService.executarAcaoPedido(estabelecimento, whatsappAdmin, idPedido, a);

        ResultadoAdmin ra = new ResultadoAdmin(r.admin.chave, r.admin.mensagem);

        return new ResultadoAdminAcaoPedido(
            ra,
            r.whatsappCliente,
            r.textoCliente,
            r.mensagemCliente
        );
    }

    public String montarResumoItensDoPedido(Pedido pedido) {
        return pedidoService.montarResumoItensDoPedido(pedido);
    }

    // =========================================================
    // CARDÁPIO: MENU PRINCIPAL
    // =========================================================

    public ResultadoAdmin montarMenuCardapio(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = adminMenuService.montarMenuCardapio(estabelecimento, whatsappAdmin);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    // =========================================================
    // CARDÁPIO: PRODUTOS (LISTA / AÇÕES / PREÇO / NOME / DESC / EXCLUIR)
    // =========================================================

    public ResultadoAdmin montarMenuCardapioProdutos(Estabelecimento estabelecimento, String whatsappAdmin, Integer offset) {
        var r = cardapioProdutoService.montarMenuCardapioProdutos(estabelecimento, whatsappAdmin, offset);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin montarMenuAcoesProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto, Integer offsetLista) {
        var r = cardapioProdutoService.montarMenuAcoesProduto(estabelecimento, whatsappAdmin, idProduto, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin montarMenuAjustePrecoProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto, Integer offsetLista) {
        var r = cardapioProdutoService.montarMenuAjustePrecoProduto(estabelecimento, whatsappAdmin, idProduto, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdminPreco aplicarDeltaPrecoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer deltaCentavos,
        Integer offsetLista
    ) {
        var r = cardapioProdutoService.aplicarDeltaPrecoProduto(estabelecimento, whatsappAdmin, idProduto, deltaCentavos, offsetLista);
        ResultadoAdmin ra = new ResultadoAdmin(r.admin.chave, r.admin.mensagem);
        return new ResultadoAdminPreco(ra, r.novoPreco, r.nomeProduto, r.descricaoProduto);
    }

    public ResultadoAdmin iniciarPrecoManualProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Integer offsetLista
    ) {
        var r = cardapioProdutoService.iniciarPrecoManualProdutoPorDigitacao(estabelecimento, whatsappAdmin, idSessao, idProduto, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdminPreco concluirPrecoManualProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String textoDigitado
    ) {
        var r = cardapioProdutoService.concluirPrecoManualProdutoPorDigitacao(estabelecimento, whatsappAdmin, idSessao, textoDigitado);
        ResultadoAdmin ra = new ResultadoAdmin(r.admin.chave, r.admin.mensagem);
        return new ResultadoAdminPreco(ra, r.novoPreco, r.nomeProduto, r.descricaoProduto);
    }

    public ResultadoAdmin iniciarAlteracaoNomeProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Integer offsetLista
    ) {
        var r = cardapioProdutoService.iniciarAlteracaoNomeProdutoPorDigitacao(estabelecimento, whatsappAdmin, idSessao, idProduto, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin concluirAlteracaoNomeProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novoNome
    ) {
        var r = cardapioProdutoService.concluirAlteracaoNomeProdutoPorDigitacao(estabelecimento, whatsappAdmin, idSessao, novoNome);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin iniciarAlteracaoDescricaoProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Integer offsetLista
    ) {
        var r = cardapioProdutoService.iniciarAlteracaoDescricaoProdutoPorDigitacao(estabelecimento, whatsappAdmin, idSessao, idProduto, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin concluirAlteracaoDescricaoProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novaDesc
    ) {
        var r = cardapioProdutoService.concluirAlteracaoDescricaoProdutoPorDigitacao(estabelecimento, whatsappAdmin, idSessao, novaDesc);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin confirmarExclusaoProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto, Integer offsetLista) {
        var r = cardapioProdutoService.confirmarExclusaoProduto(estabelecimento, whatsappAdmin, idProduto, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin excluirProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto, Integer offsetLista) {
        var r = cardapioProdutoService.excluirProduto(estabelecimento, whatsappAdmin, idProduto, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    // =========================================================
    // MARCAS
    // =========================================================

    public ResultadoAdmin montarMenuMarcas(Estabelecimento estabelecimento, String whatsappAdmin, Integer offset) {
        var r = marcaService.montarMenuMarcas(estabelecimento, whatsappAdmin, offset);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin montarDetalheMarca(Estabelecimento estabelecimento, String whatsappAdmin, Long idMarca, Integer offsetLista) {
        var r = marcaService.montarDetalheMarca(estabelecimento, whatsappAdmin, idMarca, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin iniciarCadastroMarcaPorDigitacao(Estabelecimento estabelecimento, String whatsappAdmin, Long idSessao, Integer offsetLista) {
        var r = marcaService.iniciarCadastroMarcaPorDigitacao(estabelecimento, whatsappAdmin, idSessao, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdminMarca concluirCadastroMarcaPorDigitacao(Estabelecimento estabelecimento, String whatsappAdmin, Long idSessao, String nomeMarca) {
        var r = marcaService.concluirCadastroMarcaPorDigitacao(estabelecimento, whatsappAdmin, idSessao, nomeMarca);
        ResultadoAdmin ra = new ResultadoAdmin(r.admin.chave, r.admin.mensagem);
        return new ResultadoAdminMarca(ra, r.idMarca, r.nomeMarca);
    }

    public ResultadoAdmin iniciarAlteracaoNomeMarcaPorDigitacao(Estabelecimento estabelecimento, String whatsappAdmin, Long idSessao, Long idMarca, Integer offsetLista) {
        var r = marcaService.iniciarAlteracaoNomeMarcaPorDigitacao(estabelecimento, whatsappAdmin, idSessao, idMarca, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdminMarca concluirAlteracaoNomeMarcaPorDigitacao(Estabelecimento estabelecimento, String whatsappAdmin, Long idSessao, String novoNome) {
        var r = marcaService.concluirAlteracaoNomeMarcaPorDigitacao(estabelecimento, whatsappAdmin, idSessao, novoNome);
        ResultadoAdmin ra = new ResultadoAdmin(r.admin.chave, r.admin.mensagem);
        return new ResultadoAdminMarca(ra, r.idMarca, r.nomeMarca);
    }

    public ResultadoAdmin confirmarExclusaoMarca(Estabelecimento estabelecimento, String whatsappAdmin, Long idMarca, Integer offsetLista) {
        var r = marcaService.confirmarExclusaoMarca(estabelecimento, whatsappAdmin, idMarca, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin excluirMarca(Estabelecimento estabelecimento, String whatsappAdmin, Long idMarca, Integer offsetLista) {
        var r = marcaService.excluirMarca(estabelecimento, whatsappAdmin, idMarca, offsetLista);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    // =========================================================
    // SUSPENDER / LIBERAR
    // =========================================================

    public ResultadoAdmin listarProdutosParaSuspender(Estabelecimento estabelecimento, String whatsappAdmin, Integer offset) {
        var r = disponibilidadeService.listarProdutosParaSuspender(estabelecimento, whatsappAdmin, offset);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin listarProdutosParaLiberar(Estabelecimento estabelecimento, String whatsappAdmin, Integer offset) {
        var r = disponibilidadeService.listarProdutosParaLiberar(estabelecimento, whatsappAdmin, offset);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin suspenderProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto) {
        var r = disponibilidadeService.suspenderProduto(estabelecimento, whatsappAdmin, idProduto);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin liberarProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto) {
        var r = disponibilidadeService.liberarProduto(estabelecimento, whatsappAdmin, idProduto);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    // =========================================================
    // ENTREGAS (menus + CEP + taxas)
    // =========================================================

    public ResultadoAdmin montarMenuEntregas(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = adminMenuService.montarMenuEntregas(estabelecimento, whatsappAdmin);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin montarMenuCepLoja(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = entregaCepService.montarMenuCepLoja(estabelecimento, whatsappAdmin);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin iniciarCadastroCepLojaPorDigitacao(Estabelecimento estabelecimento, String whatsappAdmin, Long idSessao) {
        var r = entregaCepService.iniciarCadastroCepLojaPorDigitacao(estabelecimento, whatsappAdmin, idSessao);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin concluirCadastroCepLojaPorDigitacao(Estabelecimento estabelecimento, String whatsappAdmin, Long idSessao, String textoDigitado) {
        var r = entregaCepService.concluirCadastroCepLojaPorDigitacao(estabelecimento, whatsappAdmin, idSessao, textoDigitado);

        // Se salvou ok e você quer voltar pro menu entregas “real”, o Orquestrador já chama montarMenuEntregas
        // via chave/mensagem; aqui mantive exatamente o retorno que o subservice produz.
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin montarMenuTaxasEntrega(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = entregaCepService.montarMenuTaxasEntrega(estabelecimento, whatsappAdmin);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin montarMenuTaxaPadrao(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = entregaCepService.montarMenuTaxaPadrao(estabelecimento, whatsappAdmin);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin montarMenuTaxaPorBairros(Estabelecimento estabelecimento, String whatsappAdmin, Integer offset) {
        var r = entregaCepService.montarMenuTaxaPorBairros(estabelecimento, whatsappAdmin, offset);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }
    
    public ResultadoAdmin montarMenuBairroEntregaSelecionado(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idBairro,
	    Integer offsetLista
	) {
	    var r = entregaCepService.montarMenuBairroEntregaSelecionado(estabelecimento, whatsappAdmin, idBairro, offsetLista);
	    return new ResultadoAdmin(r.chave, r.mensagem);
	}
    
  //================TAXA DE ENTREGA POR BAIRRO===================
    public ResultadoAdmin iniciarCadastroTaxaEntregaBairroPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Long idBairro,
	    Integer offsetLista
	) {
	    var r = entregaCepService.iniciarCadastroTaxaEntregaBairroPorDigitacao(estabelecimento, whatsappAdmin, idSessao, idBairro, offsetLista);
	    return new ResultadoAdmin(r.chave, r.mensagem);
	}

	public ResultadoAdmin concluirCadastroTaxaEntregaBairroPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String textoDigitado
	) {
	    var r = entregaCepService.concluirCadastroTaxaEntregaBairroPorDigitacao(estabelecimento, whatsappAdmin, idSessao, textoDigitado);
	    return new ResultadoAdmin(r.chave, r.mensagem);
	}
	
	
	
	//================TAXA PADRÃO===================
	public ResultadoAdmin iniciarCadastroTaxaEntregaPadraoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    Integer offsetVoltar
	) {
	    var r = entregaCepService.iniciarCadastroTaxaEntregaPadraoPorDigitacao(estabelecimento, whatsappAdmin, idSessao, offsetVoltar);
	    return new ResultadoAdmin(r.chave, r.mensagem);
	}

	public ResultadoAdmin concluirCadastroTaxaEntregaPadraoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String textoDigitado
	) {
	    var r = entregaCepService.concluirCadastroTaxaEntregaPadraoPorDigitacao(estabelecimento, whatsappAdmin, idSessao, textoDigitado);
	    return new ResultadoAdmin(r.chave, r.mensagem);
	}
	
	
    // =========================================================
    // RELATÓRIOS
    // =========================================================

    public ResultadoAdmin montarMenuRelatorios(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = relatorioService.montarMenuRelatorios(estabelecimento, whatsappAdmin);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin gerarRelatorioHoje(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = relatorioService.gerarRelatorio(estabelecimento, whatsappAdmin, TipoPeriodoRelatorio.HOJE);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin gerarRelatorioOntem(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = relatorioService.gerarRelatorio(estabelecimento, whatsappAdmin, TipoPeriodoRelatorio.ONTEM);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin gerarRelatorioSemana(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = relatorioService.gerarRelatorio(estabelecimento, whatsappAdmin, TipoPeriodoRelatorio.SEMANA_ATUAL);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }

    public ResultadoAdmin gerarRelatorioMes(Estabelecimento estabelecimento, String whatsappAdmin) {
        var r = relatorioService.gerarRelatorio(estabelecimento, whatsappAdmin, TipoPeriodoRelatorio.MES_ATUAL);
        return new ResultadoAdmin(r.chave, r.mensagem);
    }
}