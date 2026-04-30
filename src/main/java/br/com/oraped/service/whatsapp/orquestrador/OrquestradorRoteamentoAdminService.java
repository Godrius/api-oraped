package br.com.oraped.service.whatsapp.orquestrador;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.enums.TipoPeriodoRelatorio;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdminCardapioService;
import br.com.oraped.service.whatsapp.administrador.AdminComplementoCategoriaService;
import br.com.oraped.service.whatsapp.administrador.AdminComplementoProdutoService;
import br.com.oraped.service.whatsapp.administrador.AdminDisponibilidadeProdutoService;
import br.com.oraped.service.whatsapp.administrador.AdminEntregaService;
import br.com.oraped.service.whatsapp.administrador.AdminGrupoComplementoService;
import br.com.oraped.service.whatsapp.administrador.AdminLojaService;
import br.com.oraped.service.whatsapp.administrador.AdminMarcaService;
import br.com.oraped.service.whatsapp.administrador.AdminPedidoService;
import br.com.oraped.service.whatsapp.administrador.AdminProdutoService;
import br.com.oraped.service.whatsapp.administrador.AdminRelatorioService;
import br.com.oraped.service.whatsapp.administrador.MenuAdminService;
import br.com.oraped.service.whatsapp.administrador.ValidadorAdminService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorRoteamentoAdminService {

	private final AdminLojaService adminLojaService;
	private final MenuAdminService menuAdminService;
	private final AdminCardapioService adminCardapioService;
	private final AdminProdutoService adminProdutoService;
	private final AdminEntregaService adminEntregaService;
	private final AdminPedidoService adminPedidoService;
	private final AdminMarcaService adminMarcaService;
	private final AdminDisponibilidadeProdutoService adminDisponibilidadeProdutoService;
	private final AdminRelatorioService adminRelatorioService;
	private final AdminGrupoComplementoService adminGrupoComplementoService;
	private final AdminComplementoProdutoService adminComplementoProdutoService; 
	private final AdminComplementoCategoriaService adminComplementoCategoriaService;
	
    private final ValidadorAdminService validadorAdminService;
    
    private final OrquestradorParseService parse;
    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotearAdmin(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    ComandoWhatsapp cmd
	) {

	    String acao = cmd.getAcao();

	    if (!validadorAdminService.isAdminAtivo(estabelecimento, whatsappAdmin)) {
	        return new RoteamentoResultado("admin_nao_autorizado", msg.texto(whatsappAdmin, "Sem permissão."));
	    }

	    switch (acao) {

	        // =========================================================
	        // ADMIN: MENU PRINCIPAL E LOJA
	        // =========================================================

	        case "ADMIN_MENU": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                menuAdminService.montarMenuAdmin(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ABRIR_LOJA": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminLojaService.abrirLoja(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_FECHAR_LOJA": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminLojaService.fecharLoja(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        // =========================================================
	        // ADMIN: PEDIDOS
	        // =========================================================

	        case "ADMIN_VER_PEDIDOS": {
	            StatusPedido status = parse.parseStatusPedidoObrigatorio(cmd.getParte(2));
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminPedidoService.listarPedidosPorStatus(estabelecimento, whatsappAdmin, status, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PEDIDO_DETALHE": {
	            Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminPedidoService.montarDetalhePedido(estabelecimento, whatsappAdmin, idPedido);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ACEITAR_PEDIDO":
	            return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappResultados.AcaoPedidoAdmin.ACEITAR);

	        case "ADMIN_RECUSAR_PEDIDO":
	            return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappResultados.AcaoPedidoAdmin.RECUSAR);

	        case "ADMIN_PREPARAR_PEDIDO":
	            return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappResultados.AcaoPedidoAdmin.PREPARAR);

	        case "ADMIN_CANCELAR_PEDIDO":
	            return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappResultados.AcaoPedidoAdmin.CANCELAR);

	        case "ADMIN_INICIAR_ENTREGA":
	            return executarAcaoPedidoAdmin(estabelecimento, whatsappAdmin, cmd, AdministradorWhatsappResultados.AcaoPedidoAdmin.INICIAR_ENTREGA);

	        // =========================================================
	        // ADMIN: CARDÁPIO — CATEGORIAS, PRODUTOS E DISPONIBILIDADE
	        // =========================================================

	        case "ADMIN_CARDAPIO_MENU": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                menuAdminService.montarMenuCardapio(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_CARDAPIO_CATEGORIAS_MENU": {
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminCardapioService.montarMenuCardapioCategorias(
	                    estabelecimento,
	                    whatsappAdmin,
	                    offset
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        
	        case "ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU": {
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminCardapioService.montarMenuCardapioProdutosPorCategoria(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idCategoria,
	                    offset
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_CARDAPIO_PRODUTO": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminCardapioService.montarMenuAcoesProduto(
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
	                adminDisponibilidadeProdutoService.listarProdutosParaSuspender(estabelecimento, whatsappAdmin, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_LIBERAR_PRODUTO_MENU": {
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminDisponibilidadeProdutoService.listarProdutosParaLiberar(estabelecimento, whatsappAdmin, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_SUSPENDER_PRODUTO": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminDisponibilidadeProdutoService.suspenderProduto(estabelecimento, whatsappAdmin, idProduto);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_LIBERAR_PRODUTO": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminDisponibilidadeProdutoService.liberarProduto(estabelecimento, whatsappAdmin, idProduto);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

		     // =========================================================
		     // ADMIN: CADASTRO DE CATEGORIAS E PRODUTOS
		     // =========================================================
	
		     case "ADMIN_CATEGORIA_NOVA_MENU": {
		         Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(2));
	
		         AdministradorWhatsappResultados.ResultadoAdmin r =
		             adminCardapioService.iniciarCadastroCategoriaPorDigitacao(
		                 estabelecimento,
		                 whatsappAdmin,
		                 idSessao,
		                 offsetCategorias
		             );
	
		         return new RoteamentoResultado(r.chave, r.mensagem);
		     }
	
		     case "ADMIN_PRODUTO_NOVO_CATEGORIA_MENU": {
		         Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(2));
	
		         AdministradorWhatsappResultados.ResultadoAdmin r =
		             adminProdutoService.listarCategoriasParaNovoProduto(
		                 estabelecimento,
		                 whatsappAdmin,
		                 offsetCategorias
		             );
	
		         return new RoteamentoResultado(r.chave, r.mensagem);
		     }
	
		     case "ADMIN_PRODUTO_NOVO_MENU": {
		         Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
		         Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));
	
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
	        // =========================================================
	        // ADMIN: PRODUTO — PREÇO, NOME, DESCRIÇÃO, FOTO E EXCLUSÃO
	        // =========================================================

	        case "ADMIN_PROD_PRECO_MENU": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

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
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer deltaCentavos = parse.parseIntDefaultZeroAllowNegative(cmd.getParte(3));
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(4), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(5));

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

	            MensagemWhatsappSaidaDTO confirmacao = msg.texto(whatsappAdmin, msg.trunc(corpo, 1024));
	            List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

	            return new RoteamentoResultado("admin_preco_atualizado", confirmacao, extras);
	        }

	        case "ADMIN_PROD_PRECO_MANUAL": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

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
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

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
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

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
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

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
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

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
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

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
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

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
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(4));

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

	        // =========================================================
	        // ADMIN: COMPLEMENTOS POR CATEGORIA
	        // =========================================================

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

	        // =========================================================
	        // ADMIN: GRUPOS GLOBAIS DE COMPLEMENTOS
	        // =========================================================

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

	        // =========================================================
	        // ADMIN: COMPLEMENTOS GLOBAIS DO GRUPO
	        // =========================================================

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

	        // =========================================================
	        // ADMIN: COMPLEMENTOS POR PRODUTO
	        // =========================================================

	        case "ADMIN_PROD_COMPLEMENTOS_MENU": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.montarMenuComplementosProduto(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_ASSOCIADOS": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(5));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.listarGruposAssociadosAoProduto(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    offsetGrupos
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_ASSOCIAR_MENU": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(5));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.listarGruposDisponiveisParaAssociar(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    offsetGrupos
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_ASSOCIAR": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.associarGrupoAoProduto(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_GRUPO_DETALHE": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.montarDetalheGrupoAssociado(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_GRUPO_DESASSOCIAR_CONFIRM": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.confirmarDesassociacaoGrupoProduto(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_GRUPO_DESASSOCIAR": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.desassociarGrupoProduto(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTOS": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Integer offsetComplementos = parse.parseIntDefaultZero(cmd.getParte(6));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.listarComplementosDoGrupo(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    offsetComplementos
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTO_DETALHE": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(6), "idComplemento");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.montarDetalheComplemento(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    idComplemento
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTO_STATUS": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(6), "idComplemento");
	            boolean ativo = parse.parseIntDefaultZero(cmd.getParte(7)) == 1;

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.alterarStatusComplemento(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    idComplemento,
	                    ativo
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTO_PRECO_MENU": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(6), "idComplemento");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.montarMenuPrecoComplemento(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    idComplemento
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTO_PRECO_APLICAR": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(6), "idComplemento");
	            Integer deltaCentavos = parse.parseIntDefaultZeroAllowNegative(cmd.getParte(7));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.aplicarDeltaPrecoComplemento(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    idComplemento,
	                    deltaCentavos
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        // =========================================================
	        // ADMIN: MARCAS
	        // =========================================================

	        case "ADMIN_CARDAPIO_MARCAS_MENU": {
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminMarcaService.montarMenuMarcas(estabelecimento, whatsappAdmin, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_MARCA_DETALHE": {
	            Long idMarca = parse.parseLongObrigatorio(cmd.getParte(2), "idMarca");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminMarcaService.montarDetalheMarca(estabelecimento, whatsappAdmin, idMarca, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_MARCA_NOVA_MENU": {
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

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
	            Long idMarca = parse.parseLongObrigatorio(cmd.getParte(2), "idMarca");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

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
	            Long idMarca = parse.parseLongObrigatorio(cmd.getParte(2), "idMarca");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminMarcaService.confirmarExclusaoMarca(estabelecimento, whatsappAdmin, idMarca, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_MARCA_EXCLUIR": {
	            Long idMarca = parse.parseLongObrigatorio(cmd.getParte(2), "idMarca");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminMarcaService.excluirMarca(estabelecimento, whatsappAdmin, idMarca, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        // =========================================================
	        // ADMIN: ENTREGAS
	        // =========================================================

	        case "ADMIN_ENTREGAS_MENU": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                menuAdminService.montarMenuEntregas(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_CEP_MENU":
	        case "ADMIN_ENTREGAS_CEP_DIGITAR": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.iniciarCadastroCepLojaPorDigitacao(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idSessao
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_TAXAS_MENU": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.montarMenuTaxasEntrega(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_TAXA_PADRAO_MENU": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.montarMenuTaxaPadrao(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_BAIRROS_MENU": {
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.montarMenuTaxaPorBairros(estabelecimento, whatsappAdmin, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_BAIRRO_SELECIONAR": {
	            Long idBairro = parse.parseLongObrigatorio(cmd.getParte(2), "idBairro");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

	            // Ao selecionar o bairro, mostramos primeiro o menu de ações do bairro.
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.montarMenuBairroEntregaSelecionado(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idBairro,
	                    offset
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_TAXA_BAIRRO_DIGITAR": {
	            Long idBairro = parse.parseLongObrigatorio(cmd.getParte(2), "idBairro");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

	            // A digitação manual fica como ação explícita para não confundir com botões rápidos.
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.iniciarCadastroTaxaEntregaBairroPorDigitacao(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idSessao,
	                    idBairro,
	                    offset
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_BAIRRO_ISENTO": {
	            Long idBairro = parse.parseLongObrigatorio(cmd.getParte(2), "idBairro");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

	            // Frete grátis precisa ser explícito para não ser confundido com ausência de configuração.
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.marcarBairroComoEntregaGratuita(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idBairro,
	                    offset
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_BAIRRO_REMOVER": {
	            Long idBairro = parse.parseLongObrigatorio(cmd.getParte(2), "idBairro");
	            Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

	            // Remove a configuração específica para o bairro voltar ao comportamento padrão.
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.removerConfiguracaoBairroEntrega(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idBairro,
	                    offset
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_BAIRROS_ATENDIDOS": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.iniciarConfiguracaoBairrosAtendidosPorDigitacao(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idSessao
	                );

	            MensagemWhatsappSaidaDTO navegacao = msg.botoes(
	                whatsappAdmin,
	                "Digite os códigos dos bairros atendidos ou use os botões abaixo para voltar ao menu de opções",
	                List.of(
	                    new MensagemInterativaBotaoReplyWhatsappDTO(
	                        "COMANDO|ADMIN_ENTREGAS_MENU",
	                        "⬅️ Voltar"
	                    ),
	                    new MensagemInterativaBotaoReplyWhatsappDTO(
	                        "COMANDO|ADMIN_MENU",
	                        "🛠️ Menu admin"
	                    )
	                )
	            );

	            return new RoteamentoResultado(r.chave, r.mensagem, List.of(navegacao));
	        }

	        case "ADMIN_ENTREGAS_TAXA_PADRAO_DIGITAR": {
	            Integer offsetVoltar = parse.parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.iniciarCadastroTaxaEntregaPadraoPorDigitacao(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idSessao,
	                    offsetVoltar
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        // =========================================================
	        // ADMIN: RELATÓRIOS
	        // =========================================================

	        case "ADMIN_RELATORIOS_MENU": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminRelatorioService.montarMenuRelatorios(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_RELATORIOS_HOJE":
	        case "ADMIN_RELATORIOS_ONTEM":
	        case "ADMIN_RELATORIOS_SEMANA":
	        case "ADMIN_RELATORIOS_MES": {

	            TipoPeriodoRelatorio periodo;

	            switch (cmd.getAcao()) {
	                case "ADMIN_RELATORIOS_HOJE":
	                    periodo = TipoPeriodoRelatorio.HOJE;
	                    break;
	                case "ADMIN_RELATORIOS_ONTEM":
	                    periodo = TipoPeriodoRelatorio.ONTEM;
	                    break;
	                case "ADMIN_RELATORIOS_SEMANA":
	                    periodo = TipoPeriodoRelatorio.SEMANA_ATUAL;
	                    break;
	                case "ADMIN_RELATORIOS_MES":
	                    periodo = TipoPeriodoRelatorio.MES_ATUAL;
	                    break;
	                default:
	                    throw new IllegalArgumentException("Período inválido");
	            }

	            var r = adminRelatorioService.gerarRelatorio(estabelecimento, whatsappAdmin, periodo);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        // =========================================================
	        // ADMIN: FALLBACK
	        // =========================================================

	        default: {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                menuAdminService.montarMenuAdmin(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado("admin_acao_desconhecida", r.mensagem);
	        }
	    }
	}

    private RoteamentoResultado executarAcaoPedidoAdmin(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    ComandoWhatsapp cmd,
	    AdministradorWhatsappResultados.AcaoPedidoAdmin acao
	) {

	    Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");

	    var r = adminPedidoService.executarAcaoPedido(
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