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
import br.com.oraped.service.whatsapp.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdministradorWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorTextoLivreService {

    private final AdministradorWhatsappService administradorWhatsappService;
    private final EstabelecimentoService estabelecimentoService;

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final OrquestradorRegistroMensagemService registroMensagemService;
    
    private final OrquestradorMenusClienteService menusClienteService;
    private final OrquestradorFluxoClienteService fluxoClienteService;
    private final OrquestradorParseService parse;
    private final OrquestradorMensagemHelperService mensagemHelper;
    
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
	        MensagemWhatsappSaidaDTO m = msg.texto(whatsappCliente, "⚠️ Não consegui identificar sua sessão. Tente novamente.");
	        return new RoteamentoResultado("sessao_invalida", m);
	    }

	    boolean isAdminAtivo = administradorWhatsappService.isAdminAtivo(estabelecimento, whatsappCliente);

	    // =========================================================
	    // 0) BLOQUEIO DE ATENDIMENTO (CLIENTE)
	    // - admin ativo NUNCA é bloqueado
	    // - inativo: apenas informa
	    // - fechado: oferece botão "Sim, me avise"
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
	    // Comando "MENU" (atalho)
	    // =========================================================
	    String txtLivre = msg.safe(parse.safeTextoEntrada(req));
	    if (StringUtils.hasText(txtLivre)) {

	        String upper = txtLivre.trim().toUpperCase(Locale.ROOT);

            if ("MENU".equals(upper)) {

                sessaoService.limparAguardando(idSessao);

                MensagemWhatsappSaidaDTO mensagemSaida = temSaidaAnterior
                    ? menusClienteService.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente)
                    : menusClienteService.montarMenuPrincipal(estabelecimento, whatsappCliente);

                return new RoteamentoResultado("menu_principal", mensagemSaida);
            }
	    }

	    // =========================================================
	    // 0) Admin aguardando digitação (prioridade máxima)
	    // =========================================================
	    if (isAdminAtivo) {

	        if (sessaoService.isAguardandoNovoPreco(idSessao)) {
	            var r = administradorWhatsappService.concluirPrecoManualProdutoPorDigitacao(
	                estabelecimento,
	                whatsappCliente,
	                idSessao,
	                parse.safeTextoEntrada(req)
	            );

	            String corpoConfirmacao =
	                "✅ Preço atualizado!\n\n" +
	                    "*" + msg.trunc(msg.safe(r.nomeProduto), 80) + "*\n" +
	                    msg.trunc(msg.safe(r.descricaoProduto), 500) + "\n\n" +
	                    "*Novo preço:* " + msg.formatarMoeda(r.novoPreco);

	            MensagemWhatsappSaidaDTO confirmacao = msg.texto(whatsappCliente, msg.trunc(corpoConfirmacao, 1024));
	            List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

	            return new RoteamentoResultado("admin_preco_atualizado_digitacao", confirmacao, extras);
	        }

	        if (sessaoService.isAguardandoNovoNomeProduto(idSessao)) {
	            var r = administradorWhatsappService.concluirAlteracaoNomeProdutoPorDigitacao(
	                estabelecimento,
	                whatsappCliente,
	                idSessao,
	                parse.safeTextoEntrada(req)
	            );
	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        if (sessaoService.isAguardandoNovaDescricaoProduto(idSessao)) {
	            var r = administradorWhatsappService.concluirAlteracaoDescricaoProdutoPorDigitacao(
	                estabelecimento,
	                whatsappCliente,
	                idSessao,
	                parse.safeTextoEntrada(req)
	            );
	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        if (sessaoService.isAguardandoNovaMarca(idSessao)) {
	            var r = administradorWhatsappService.concluirCadastroMarcaPorDigitacao(
	                estabelecimento,
	                whatsappCliente,
	                idSessao,
	                parse.safeTextoEntrada(req)
	            );

	            String corpoConfirmacao =
	                "✅ Marca cadastrada!\n\n" +
	                    "*" + msg.trunc(msg.safe(r.nomeMarca), 120) + "*";

	            MensagemWhatsappSaidaDTO confirmacao = msg.texto(whatsappCliente, msg.trunc(corpoConfirmacao, 1024));
	            List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

	            return new RoteamentoResultado("admin_marca_criada_digitacao", confirmacao, extras);
	        }

	        if (sessaoService.isAguardandoEditarMarcaNome(idSessao)) {
	            var r = administradorWhatsappService.concluirAlteracaoNomeMarcaPorDigitacao(
	                estabelecimento,
	                whatsappCliente,
	                idSessao,
	                parse.safeTextoEntrada(req)
	            );

	            String corpoConfirmacao =
	                "✅ Nome da marca atualizado!\n\n" +
	                    "*" + msg.trunc(msg.safe(r.nomeMarca), 120) + "*";

	            MensagemWhatsappSaidaDTO confirmacao = msg.texto(whatsappCliente, msg.trunc(corpoConfirmacao, 1024));
	            List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

	            return new RoteamentoResultado("admin_marca_nome_atualizado_digitacao", confirmacao, extras);
	        }

	        if (sessaoService.isAguardandoCepEstabelecimento(idSessao)) {

	            var r = administradorWhatsappService.concluirCadastroCepLojaPorDigitacao(
	                estabelecimento,
	                whatsappCliente,
	                idSessao,
	                parse.safeTextoEntrada(req)
	            );

	            boolean salvouCep = r != null && r.chave != null && r.chave.startsWith("admin_entregas_cep_salvo");

	            if (!salvouCep) {
	                return new RoteamentoResultado(r.chave, r.mensagem);
	            }

	            Estabelecimento atualizado = estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);
	            MensagemWhatsappSaidaDTO menuEntregas =
	                administradorWhatsappService.montarMenuEntregas(atualizado, whatsappCliente).mensagem;

	            return new RoteamentoResultado(r.chave, r.mensagem, List.of(menuEntregas));
	        }

	        if (sessaoService.isAguardandoTaxaEntregaBairro(idSessao)) {

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.concluirCadastroTaxaEntregaBairroPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        if (sessaoService.isAguardandoTaxaEntregaPadrao(idSessao)) {

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.concluirCadastroTaxaEntregaPadraoPorDigitacao(
	                    estabelecimento,
	                    whatsappCliente,
	                    idSessao,
	                    parse.safeTextoEntrada(req)
	                );

	            Estabelecimento atualizado = estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);

	            MensagemWhatsappSaidaDTO menuEntregas =
	                administradorWhatsappService.montarMenuEntregas(atualizado, whatsappCliente).mensagem;

	            return new RoteamentoResultado(r.chave, r.mensagem, List.of(menuEntregas));
	        }
	    }

	    // FAILSAFE: limpa estados admin remanescentes para não-admin
	    if (!isAdminAtivo) {
	        if (sessaoService.isAguardandoTaxaEntregaPadrao(idSessao)) {
	            sessaoService.limparAguardandoTaxaEntregaPadrao(idSessao);
	        }
	        if (sessaoService.isAguardandoTaxaEntregaBairro(idSessao)) {
	            sessaoService.limparAguardandoTaxaEntregaBairro(idSessao);
	        }
	        if (sessaoService.isAguardandoCepEstabelecimento(idSessao)) {
	            sessaoService.limparAguardandoCepEstabelecimento(idSessao);
	        }
	    }

	    // Fluxos do cliente
        
	 // =========================================================
	 // CLIENTE: Quantidade manual (digitação)
	 // =========================================================
	 if (sessaoService.isAguardandoQuantidadeManual(idSessao)) {

	     String raw = msg.safe(parse.safeTextoEntrada(req));

	     Integer quantidade = extrairQuantidadeInteira(raw);

	     if (quantidade == null || quantidade.intValue() <= 0) {

	         // Mantém aguardando quantidade manual
	         MensagemWhatsappSaidaDTO m = msg.texto(
	             whatsappCliente,
	             "Quantidade inválida 😕\n\n" +
	                 "Me informe um número inteiro maior que zero.\n\n" +
	                 "Exemplo: 15"
	         );

	         return new RoteamentoResultado("quantidade_manual_invalida", m);
	     }

	     Long idProduto = sessaoService.getIdProdutoQuantidadeManual(idSessao);

	     if (idProduto == null) {

	         // Segurança: se perdeu o contexto, limpa e volta menu
	         sessaoService.limparAguardando(idSessao);

	         MensagemWhatsappSaidaDTO fallback = temSaidaAnterior
	             ? menusClienteService.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente)
	             : menusClienteService.montarMenuPrincipal(estabelecimento, whatsappCliente);

	         return new RoteamentoResultado("quantidade_manual_sem_produto", fallback);
	     }

	     // ----------------------------------------------------------------
	     // IMPORTANTE:
	     // O carrinho é montado a partir de entradas "COMANDO|ADICIONAR_PRODUTO|..."
	     // Como aqui o cliente digitou "100" (texto livre), precisamos gravar
	     // uma entrada sintética equivalente ao comando de adicionar produto.
	     // ----------------------------------------------------------------
	     String comandoAdicionar = "COMANDO|ADICIONAR_PRODUTO|" + idProduto + "|" + quantidade;

	     // "payloadOriginal" aqui não existe (é uma entrada sintética), então vai null.
	     registroMensagemService.registrarEntrada(idSessao, comandoAdicionar, null);

	     // Agora sim: limpa o modo de digitação para não deixar estado preso
	     sessaoService.limparAguardando(idSessao);

	     // Reutiliza o fluxo já existente (monta mensagem + botões)
	     return fluxoClienteService.tratarAdicionarProduto(
	         estabelecimento,
	         whatsappCliente,
	         idProduto,
	         quantidade
	     );
	 }
        
        // =========================================================
        // CLIENTE: Endereço de entrega (digitação)
        // =========================================================
        if (sessaoService.isAguardandoEnderecoEntrega(idSessao)) {

            MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarEnderecoEntregaInformado(
                estabelecimento,
                whatsappCliente,
                idSessao,
                parse.safeTextoEntrada(req)
            );

            return new RoteamentoResultado("endereco_entrega_tratado", m);
        }
	    
	    if (sessaoService.isAguardandoCepEntrega(idSessao)) {
	        MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarCepEntregaInformado(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );
	        return new RoteamentoResultado("cep_entrega_tratado", m);
	    }

	    if (sessaoService.isAguardandoComplementoEndereco(idSessao)) {
	        MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarComplementoEnderecoInformado(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );
	        return new RoteamentoResultado("complemento_endereco_tratado", m);
	    }

	    if (sessaoService.isAguardandoEnderecoCompletoFallback(idSessao)) {
	        MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarEnderecoCompletoFallbackInformado(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );
	        return new RoteamentoResultado("endereco_fallback_tratado", m);
	    }

	    if (sessaoService.isAguardandoFormaPagamento(idSessao)) {
	    	MensagemWhatsappSaidaDTO m = menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao);
	        return new RoteamentoResultado("forma_pagamento_menu", m);
	    }

	    if (sessaoService.isAguardandoTrocoConfirmacao(idSessao)) {
	        MensagemWhatsappSaidaDTO m = menusClienteService.montarPerguntaTrocoSimNao(whatsappCliente);
	        return new RoteamentoResultado("troco_confirmacao_menu", m);
	    }

	    if (sessaoService.isAguardandoTrocoValor(idSessao)) {
	        MensagemWhatsappSaidaDTO m = fluxoClienteService.tratarValorTrocoInformado(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            parse.safeTextoEntrada(req)
	        );
	        return new RoteamentoResultado("troco_valor_registrado", m);
	    }

	    if (sessaoService.isAguardandoConfirmacaoFinal(idSessao)) {
	        var s = sessaoService.buscarPorId(idSessao);
	        MensagemWhatsappSaidaDTO m = menusClienteService.montarConfirmacaoFinalAntesDeEnviar(
	            estabelecimento,
	            whatsappCliente,
	            s
	        );
	        return new RoteamentoResultado("confirmacao_final", m);
	    }

	    // Fallback menu
	    MensagemWhatsappSaidaDTO fallback = temSaidaAnterior
	        ? menusClienteService.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente)
	        : menusClienteService.montarMenuPrincipal(estabelecimento, whatsappCliente);

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
}