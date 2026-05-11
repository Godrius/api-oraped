package br.com.oraped.service.whatsapp.orquestrador;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.OrquestradorContexto;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.whatsapp.entrada.MensagemWhatsappEntradaDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdminCategoriaService;
import br.com.oraped.service.whatsapp.administrador.AdminComplementoCategoriaService;
import br.com.oraped.service.whatsapp.administrador.AdminComplementoProdutoService;
import br.com.oraped.service.whatsapp.administrador.AdminEntregaService;
import br.com.oraped.service.whatsapp.administrador.AdminGrupoComplementoService;
import br.com.oraped.service.whatsapp.administrador.AdminMarcaService;
import br.com.oraped.service.whatsapp.administrador.AdminProdutoService;
import br.com.oraped.service.whatsapp.administrador.AdminTamanhoCategoriaService;
import br.com.oraped.service.whatsapp.administrador.AdminTamanhoProdutoService;
import br.com.oraped.service.whatsapp.administrador.MenuAdminService;
import br.com.oraped.service.whatsapp.administrador.ValidadorAdminService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.cliente.MenuClienteService;
import br.com.oraped.service.whatsapp.cliente.PedidoClienteService;
import br.com.oraped.service.whatsapp.cliente.RevisaoPedidoClienteService;
import br.com.oraped.service.whatsapp.orquestrador.marketplace.OrquestradorFluxoMarketplaceService;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminCategoriaService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminComplementoService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminEntregaService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminGrupoComplementoService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminMarcaService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminProdutoService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminTamanhoService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappClienteService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappMarketplaceService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Tratar mensagens de texto livre recebidas no WhatsApp, resolvendo estados pendentes
 * da sessão antes de cair no menu padrão.
 *
 * Aplicação:
 * Utilizado pelo orquestrador principal quando a entrada não é um comando estruturado,
 * permitindo concluir fluxos do cliente, admin e marketplace por digitação livre.
 *
 * Utilização:
 * Deve priorizar estados pendentes da sessão, pois o mesmo texto pode ter significados
 * diferentes conforme o contexto atual da conversa.
 */
@Service
@RequiredArgsConstructor
public class OrquestradorTextoLivreService {

    private final MenuAdminService menuAdminService;
    private final AdminProdutoService adminProdutoService;
    private final AdminEntregaService adminEntregaService;
    private final AdminMarcaService adminMarcaService;
    private final ValidadorAdminService validadorAdminService;
    private final AdminGrupoComplementoService adminGrupoComplementoService;
    private final AdminComplementoProdutoService adminComplementoProdutoService;
    private final AdminComplementoCategoriaService adminComplementoCategoriaService;
    private final AdminCategoriaService adminCategoriaService;
    private final AdminTamanhoCategoriaService adminTamanhoCategoriaService;
    private final AdminTamanhoProdutoService adminTamanhoProdutoService;
    
    private final EstabelecimentoService estabelecimentoService;

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final SessaoWhatsappClienteService sessaoClienteService;
    private final SessaoWhatsappMarketplaceService sessaoMarketplaceService;
    private final SessaoWhatsappAdminProdutoService sessaoAdminProdutoService;
    private final SessaoWhatsappAdminTamanhoService sessaoAdminTamanhoService;
    private final SessaoWhatsappAdminCategoriaService sessaoAdminCategoriaService;
    private final SessaoWhatsappAdminMarcaService sessaoAdminMarcaService;
    private final SessaoWhatsappAdminEntregaService sessaoAdminEntregaService;
    private final SessaoWhatsappAdminGrupoComplementoService sessaoAdminGrupoComplementoService;
    private final SessaoWhatsappAdminComplementoService sessaoAdminComplementoService;
    
    private final OrquestradorRegistroMensagemService registroMensagemService;
    private final RevisaoPedidoClienteService revisaoPedidoService;
    private final MenuClienteService menusClienteService;
    private final PedidoClienteService fluxoClienteService;
    private final OrquestradorParseService parse;
    private final OrquestradorMensagemHelperService mensagemHelper;

    private final OrquestradorFluxoMarketplaceService fluxoMarketplaceService;

    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado tratarTextoLivre(
	    OrquestradorContexto ctx,
	    MensagemWhatsappEntradaDTO req
	) {

	    Estabelecimento estabelecimento = ctx.getEstabelecimento();
	    String whatsappCliente = ctx.getWhatsappCliente();
	    String whatsappReceptor = ctx.getWhatsappReceptor();
	    Long idSessao = (ctx.getSessao() == null) ? null : ctx.getSessao().getId();
	    boolean temSaidaAnterior = ctx.isTemSaidaAnterior();

	    if (idSessao == null) {
	        MensagemWhatsappSaidaDTO m = msg.texto(
	            whatsappCliente,
	            "⚠️ Não consegui identificar sua sessão. Tente novamente."
	        );
	        return new RoteamentoResultado("sessao_invalida", m);
	    }

	    boolean isAdminAtivo = validadorAdminService.isAdminAtivo(estabelecimento, whatsappCliente);

	    // =========================================================
	    // 0) BLOQUEIO DE ATENDIMENTO PARA CLIENTE
	    // =========================================================
	    if (!isAdminAtivo) {

	        if (estabelecimento != null && !estabelecimento.isAtivo()) {
	            MensagemWhatsappSaidaDTO saida = msg.texto(
	                whatsappCliente,
	                "⚠️ Este estabelecimento está *inativo* no momento.\n\n" +
	                    "Tente novamente mais tarde."
	            );

	            return new RoteamentoResultado("estabelecimento_inativo", saida);
	        }

	        if (estabelecimento != null && !estabelecimento.isAberto()) {
	            String corpo =
	                "⏸️ No momento o estabelecimento está *fechado*.\n\n" +
	                    "Quer que eu te avise quando abrir?";

	            MensagemWhatsappSaidaDTO saida = msg.botoes(
	                whatsappCliente,
	                msg.trunc(corpo, 1024),
	                List.of(
	                    mensagemHelper.btn("COMANDO|CADASTRAR_NOTIFICACAO_ESTABELECIMENTO_ABERTO", "Sim, me avise")
	                )
	            );

	            return new RoteamentoResultado("estabelecimento_fechado_aviso", saida);
	        }
	    }

	    // =========================================================
	    // 1) ATALHO GLOBAL: MENU
	    // =========================================================
	    String txtLivre = msg.safe(parse.safeTextoEntrada(req));

	    if (StringUtils.hasText(txtLivre)) {
	        String upper = txtLivre.trim().toUpperCase(Locale.ROOT);

	        if ("MENU".equals(upper)) {
	            sessaoService.limparAguardando(idSessao);

	            if (isAdminAtivo) {
	                limparEstadosAdmin(idSessao);
	            }

	            MensagemWhatsappSaidaDTO mensagemSaida = temSaidaAnterior
	                ? menusClienteService.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente, idSessao)
	                : menusClienteService.montarMenuPrincipal(
	                    estabelecimento,
	                    whatsappCliente,
	                    ctx.getNomeClienteWhatsapp()
	                );

	            return new RoteamentoResultado("menu_principal", mensagemSaida);
	        }
	    }

	    // =========================================================
	    // 2) MARKETPLACE: REFINAMENTO DE LOCALIZAÇÃO POR CEP
	    // =========================================================
	    if (sessaoMarketplaceService.isAguardandoCepRefinarMarketplace(idSessao)) {
	        return fluxoMarketplaceService.tratarRefinamentoLocalizacaoPorCep(
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );
	    }

	    // =========================================================
	    // 3) ADMIN: ESTADOS PENDENTES POR DIGITAÇÃO
	    // =========================================================
	    if (isAdminAtivo) {

	        // -----------------------------------------------------
	        // 3.1) ADMIN: GRUPOS DE COMPLEMENTOS
	        // -----------------------------------------------------
	        if (sessaoAdminGrupoComplementoService.isAguardandoNovoGrupo(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminGrupoComplementoService.concluirCadastroGrupoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        if (sessaoAdminGrupoComplementoService.isAguardandoEditarNomeGrupo(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminGrupoComplementoService.concluirAlteracaoNomeGrupoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        if (sessaoAdminGrupoComplementoService.isAguardandoEditarDescricaoGrupo(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminGrupoComplementoService.concluirAlteracaoDescricaoGrupoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        
	        if (sessaoAdminGrupoComplementoService.isAguardandoNovoComplemento(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminGrupoComplementoService.concluirCadastroComplementoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        if (sessaoAdminComplementoService.isAguardandoEditarNomeComplementoGlobal(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminGrupoComplementoService.concluirAlteracaoNomeComplementoGlobalPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        
	        if (sessaoAdminComplementoService.isAguardandoNovoComplementoCategoria(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoCategoriaService.concluirCadastroGuiadoComplementoCategoria(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        
	        if (sessaoAdminComplementoService.isAguardandoNovoComplementoProduto(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.concluirCadastroGuiadoComplementoProduto(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        
	        if (sessaoAdminComplementoService.isAguardandoNovoPrecoComplemento(idSessao)) {
	            var r = adminGrupoComplementoService.concluirPrecoManualComplementoGlobalPorDigitacao(
	                estabelecimento,
	                whatsappCliente,
	                idSessao,
	                parse.safeTextoEntrada(req)
	            );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        
	        
	        // -----------------------------------------------------
	        // 3.2) ADMIN: PRODUTO
	        // -----------------------------------------------------
	        if (sessaoAdminProdutoService.isAguardandoNovaFotoProduto(idSessao)) {

	            if (!req.isMensagemImagem()) {
	                MensagemWhatsappSaidaDTO m = msg.texto(
	                    whatsappCliente,
	                    "🖼️ Estou aguardando a *foto do produto*.\n\n" +
	                        "Envie uma imagem nesta conversa.\n\n" +
	                        "Se quiser cancelar, envie *MENU*."
	                );
	                return new RoteamentoResultado("admin_prod_foto_esperando_imagem", m);
	            }

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminProdutoService.concluirAlteracaoFotoProdutoPorImagem(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    req.getIdMidia(),
	                    req.getMimeTypeMidia()
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        
	        if (sessaoAdminProdutoService.isAguardandoNovoPreco(idSessao)) {

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminProdutoService.concluirPrecoManualProdutoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(
	                "admin_preco_atualizado_digitacao",
	                r.mensagem
	            );
	        }

	        if (sessaoAdminProdutoService.isAguardandoNovoNomeProduto(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminProdutoService.concluirAlteracaoNomeProdutoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        if (sessaoAdminProdutoService.isAguardandoNovaDescricaoProduto(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminProdutoService.concluirAlteracaoDescricaoProdutoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        // -----------------------------------------------------
	        // 3.3) ADMIN: MARCAS
	        // -----------------------------------------------------
	        if (sessaoAdminMarcaService.isAguardandoNovaMarca(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdminMarca r =
	                adminMarcaService.concluirCadastroMarcaPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            String corpoConfirmacao =
	                "✅ Marca cadastrada!\n\n" +
	                    "*" + msg.trunc(msg.safe(r.nomeMarca), 120) + "*";

	            MensagemWhatsappSaidaDTO confirmacao = msg.texto(
	                whatsappCliente,
	                msg.trunc(corpoConfirmacao, 1024)
	            );

	            List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

	            return new RoteamentoResultado("admin_marca_criada_digitacao", confirmacao, extras);
	        }

	        if (sessaoAdminMarcaService.isAguardandoEditarMarcaNome(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdminMarca r =
	                adminMarcaService.concluirAlteracaoNomeMarcaPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            String corpoConfirmacao =
	                "✅ Nome da marca atualizado!\n\n" +
	                    "*" + msg.trunc(msg.safe(r.nomeMarca), 120) + "*";

	            MensagemWhatsappSaidaDTO confirmacao = msg.texto(
	                whatsappCliente,
	                msg.trunc(corpoConfirmacao, 1024)
	            );

	            List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

	            return new RoteamentoResultado("admin_marca_nome_atualizado_digitacao", confirmacao, extras);
	        }

	        // -----------------------------------------------------
	        // 3.4) ADMIN: ENTREGAS
	        // -----------------------------------------------------
	        if (sessaoAdminEntregaService.isAguardandoCepEstabelecimento(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.concluirCadastroCepLojaPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            boolean salvouCep = r != null
	                && r.chave != null
	                && r.chave.startsWith("admin_entregas_cep_salvo");

	            if (!salvouCep) {
	                return new RoteamentoResultado(r.chave, r.mensagem);
	            }

	            Estabelecimento atualizado = estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);

	            MensagemWhatsappSaidaDTO menuEntregas =
	                menuAdminService.montarMenuEntregas(atualizado, whatsappCliente).mensagem;

	            return new RoteamentoResultado(r.chave, r.mensagem, List.of(menuEntregas));
	        }

	        if (sessaoAdminEntregaService.isAguardandoTaxaEntregaBairro(idSessao)) {
	            Integer offsetLista = sessaoAdminEntregaService.getOffsetListaTaxaEntregaBairro(idSessao);

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.concluirCadastroTaxaEntregaBairroPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            MensagemWhatsappSaidaDTO menuBairros =
	                adminEntregaService.montarMenuTaxaPorBairros(
	                    estabelecimento,
	                    whatsappCliente,
	                    offsetLista
	                ).mensagem;

	            return new RoteamentoResultado(r.chave, r.mensagem, List.of(menuBairros));
	        }

	        if (sessaoAdminEntregaService.isAguardandoTaxaEntregaPadrao(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.concluirCadastroTaxaEntregaPadraoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            Estabelecimento atualizado = estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);

	            MensagemWhatsappSaidaDTO menuEntregas =
	                menuAdminService.montarMenuEntregas(atualizado, whatsappCliente).mensagem;

	            return new RoteamentoResultado(r.chave, r.mensagem, List.of(menuEntregas));
	        }

	        if (sessaoAdminEntregaService.isAguardandoBairrosAtendidos(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminEntregaService.concluirConfiguracaoBairrosAtendidosPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            MensagemWhatsappSaidaDTO listaAtualizada =
	                adminEntregaService.iniciarConfiguracaoBairrosAtendidosPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao
	                ).mensagem;

	            MensagemWhatsappSaidaDTO navegacao = msg.botoes(
	                whatsappCliente,
	                "Digite os códigos dos bairros atendidos ou use os botões abaixo para voltar ao menu de opções",
	                List.of(
	                    mensagemHelper.btn("COMANDO|ADMIN_ENTREGAS_MENU", "⬅️ Voltar"),
	                    mensagemHelper.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
	                )
	            );

	            return new RoteamentoResultado(r.chave, r.mensagem, List.of(listaAtualizada, navegacao));
	        }
	        
	        // -----------------------------------------------------
	        // 3.5) ADMIN: TAMANHOS
	        // -----------------------------------------------------
	        if (sessaoAdminTamanhoService.isAguardandoNovoTamanhoProduto(idSessao)) {
	        	AdministradorWhatsappResultados.ResultadoAdmin r =
	        		adminTamanhoProdutoService.concluirCadastroTamanhoProdutoPorDigitacao(
		                 estabelecimento,
		                 whatsappCliente,
		                 idSessao,
		                 parse.safeTextoEntrada(req)
	        	);
	
	        	return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	
	        if (sessaoAdminTamanhoService.isAguardandoNovoPrecoProdutoTamanho(idSessao)) {
		         AdministradorWhatsappResultados.ResultadoAdmin r =
		             adminTamanhoProdutoService.concluirAlteracaoPrecoTamanhoProduto(
		                 estabelecimento,
		                 whatsappCliente,
		                 idSessao,
		                 parse.safeTextoEntrada(req)
		             );
	
		         return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	
	        if (sessaoAdminTamanhoService.isAguardandoDescricaoOpcaoTamanho(idSessao)) {
		         AdministradorWhatsappResultados.ResultadoAdmin r =
		             adminTamanhoCategoriaService.concluirAlteracaoDescricaoOpcaoTamanho(
		                 estabelecimento,
		                 whatsappCliente,
		                 idSessao,
		                 parse.safeTextoEntrada(req)
		             );
	
		         return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	
	        if (sessaoAdminTamanhoService.isAguardandoNovaOpcaoTamanho(idSessao)) {
		         AdministradorWhatsappResultados.ResultadoAdmin r =
		             adminTamanhoCategoriaService.concluirCadastroOpcaoTamanho(
		                 estabelecimento,
		                 whatsappCliente,
		                 idSessao,
		                 parse.safeTextoEntrada(req)
		             );
	
		         return new RoteamentoResultado(r.chave, r.mensagem);
		    }
		     
	        if (sessaoAdminTamanhoService.isAguardandoNovoNomeTamanhoProduto(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminTamanhoProdutoService.concluirAlteracaoNomeTamanhoProdutoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
		     
		    // -----------------------------------------------------
		    // 3.6) ADMIN: CATEGORIAS E PRODUTOS
		    // -----------------------------------------------------
	        if (sessaoAdminCategoriaService.isAguardandoNovaCategoria(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	            	adminCategoriaService.concluirCadastroCategoriaPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        if (sessaoAdminProdutoService.isAguardandoNovoProduto(idSessao)) {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminProdutoService.concluirCadastroProdutoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        
	        
	    }

	    // =========================================================
	    // 4) FAILSAFE: LIMPA ESTADOS ADMIN PARA NÃO-ADMIN
	    // =========================================================
	    if (!isAdminAtivo) {
	        limparEstadosAdmin(idSessao);
	    }

		 // =========================================================
		 // 5) CLIENTE: QUANTIDADE MANUAL
		 // =========================================================
		 if (sessaoClienteService.isAguardandoQuantidadeManual(idSessao)) {
		     String raw = msg.safe(parse.safeTextoEntrada(req));
	
		     Integer quantidade = extrairQuantidadeInteira(raw);
	
		     if (quantidade == null || quantidade.intValue() <= 0) {
		         MensagemWhatsappSaidaDTO m = msg.texto(
		             whatsappCliente,
		             "Quantidade inválida 😕\n\n" +
		                 "Me informe um número inteiro maior que zero.\n\n" +
		                 "Exemplo: 15"
		         );
	
		         return new RoteamentoResultado("quantidade_manual_invalida", m);
		     }
	
		     Long idProduto = sessaoClienteService.getIdProdutoQuantidadeManual(idSessao);
	
		     if (idProduto == null) {
		         sessaoService.limparAguardando(idSessao);
	
		         MensagemWhatsappSaidaDTO fallback = temSaidaAnterior
		             ? menusClienteService.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente, idSessao)
		             : menusClienteService.montarMenuPrincipal(
		                 estabelecimento,
		                 whatsappCliente,
		                 ctx.getNomeClienteWhatsapp()
		             );
	
		         return new RoteamentoResultado("quantidade_manual_sem_produto", fallback);
		     }
	
		     // A digitação manual reaproveita o mesmo fluxo de adição usado pelos botões de quantidade.
		     String comandoAdicionar = "COMANDO|ADICIONAR_PRODUTO|" + idProduto + "|" + quantidade;
		     registroMensagemService.registrarEntrada(idSessao, comandoAdicionar, null);
	
		     sessaoClienteService.limparAguardandoQuantidadeManual(idSessao);
	
		     return fluxoClienteService.tratarAdicionarProduto(
		         estabelecimento,
		         whatsappCliente,
		         idSessao,
		         idProduto,
		         quantidade
		     );
		 }

	    // =========================================================
	    // 6) CLIENTE: ENTREGA E PAGAMENTO
	    // =========================================================
	    if (sessaoClienteService.isAguardandoEnderecoEntrega(idSessao)) {
	        MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarEnderecoEntregaInformado(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );

	        return new RoteamentoResultado("endereco_entrega_tratado", m);
	    }

	    if (sessaoClienteService.isAguardandoCepEntrega(idSessao)) {
	        MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarCepEntregaInformado(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );

	        return new RoteamentoResultado("cep_entrega_tratado", m);
	    }

	    if (sessaoClienteService.isAguardandoComplementoEndereco(idSessao)) {
	        MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarComplementoEnderecoInformado(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );

	        return new RoteamentoResultado("complemento_endereco_tratado", m);
	    }

	    if (sessaoClienteService.isAguardandoEnderecoCompletoFallback(idSessao)) {
	        MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarEnderecoCompletoFallbackInformado(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );

	        return new RoteamentoResultado("endereco_fallback_tratado", m);
	    }

	    if (sessaoClienteService.isAguardandoFormaPagamento(idSessao)) {
	        MensagemWhatsappSaidaDTO m = menusClienteService.montarEscolhaFormaPagamento(
	            estabelecimento,
	            whatsappCliente,
	            idSessao
	        );

	        return new RoteamentoResultado("forma_pagamento_menu", m);
	    }

	    if (sessaoClienteService.isAguardandoTrocoConfirmacao(idSessao)) {
	        MensagemWhatsappSaidaDTO m = menusClienteService.montarPerguntaTrocoSimNao(whatsappCliente);

	        return new RoteamentoResultado("troco_confirmacao_menu", m);
	    }

	    if (sessaoClienteService.isAguardandoTrocoValor(idSessao)) {
	        MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarValorTrocoInformado(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );

	        return new RoteamentoResultado("troco_valor_registrado", m);
	    }

	    if (sessaoClienteService.isAguardandoConfirmacaoFinal(idSessao)) {
	        var s = sessaoService.buscarPorId(idSessao);

	        MensagemWhatsappSaidaDTO m = revisaoPedidoService.montarConfirmacaoFinalAntesDeEnviar(
	            estabelecimento,
	            whatsappCliente,
	            s
	        );

	        return new RoteamentoResultado("confirmacao_final", m);
	    }

	    // =========================================================
	    // 7) FALLBACK: MENU PRINCIPAL DO CLIENTE
	    // =========================================================
	    MensagemWhatsappSaidaDTO fallback = temSaidaAnterior
	        ? menusClienteService.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente, idSessao)
	        : menusClienteService.montarMenuPrincipal(
	            estabelecimento,
	            whatsappCliente,
	            ctx.getNomeClienteWhatsapp()
	        );

	    return new RoteamentoResultado("menu_principal", fallback);
	}

    private Integer extrairQuantidadeInteira(String raw) {

        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String txt = raw.trim();

        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(\\d+)")
            .matcher(txt);

        if (!m.find()) {
            return null;
        }

        try {
            return Integer.valueOf(m.group(1));
        } catch (Exception e) {
            return null;
        }
    }
    
    
    private void limparEstadosAdmin(Long idSessao) {

        sessaoAdminGrupoComplementoService.limparEstadosGrupo(idSessao);

        sessaoAdminProdutoService.limparAguardandoNovoPreco(idSessao);
        sessaoAdminProdutoService.limparAguardandoNovoNomeProduto(idSessao);
        sessaoAdminProdutoService.limparAguardandoNovaDescricaoProduto(idSessao);
        sessaoAdminProdutoService.limparAguardandoNovaFotoProduto(idSessao);
        sessaoAdminProdutoService.limparAguardandoNovoProduto(idSessao);
        
        sessaoAdminTamanhoService.limparAguardandoNovoNomeTamanhoProduto(idSessao);
        sessaoAdminTamanhoService.limparAguardandoNovaOpcaoTamanho(idSessao);
        sessaoAdminTamanhoService.limparAguardandoDescricaoOpcaoTamanho(idSessao);
        sessaoAdminTamanhoService.limparAguardandoNovoPrecoProdutoTamanho(idSessao);
        sessaoAdminTamanhoService.limparAguardandoNovoTamanhoProduto(idSessao);
        
        sessaoAdminCategoriaService.limparAguardandoNovaCategoria(idSessao);
        
        sessaoAdminComplementoService.limparAguardandoEditarNomeComplementoGlobal(idSessao);
        sessaoAdminComplementoService.limparAguardandoNovoPrecoComplemento(idSessao);
        sessaoAdminComplementoService.limparAguardandoNovoComplementoProduto(idSessao);
        sessaoAdminComplementoService.limparAguardandoNovoComplementoCategoria(idSessao);
        
        sessaoAdminEntregaService.limparAguardandoCepEstabelecimento(idSessao);
        sessaoAdminEntregaService.limparAguardandoTaxaEntregaBairro(idSessao);
        sessaoAdminEntregaService.limparAguardandoTaxaEntregaPadrao(idSessao);
        
    }
}