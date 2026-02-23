// src/main/java/br/com/oraped/service/whatsapp/orquestrador/OrquestradorTextoLivreService.java
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

    private final OrquestradorMenusClienteService menusClienteService;
    private final OrquestradorFluxoClienteService fluxoClienteService;
    private final OrquestradorParseService parse;

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

	    // =========================================================
	    // Comando "MENU" (atalho)
	    // =========================================================
	    String txtLivre = msg.safe(parse.safeTextoEntrada(req));
	    if (StringUtils.hasText(txtLivre)) {

	        String upper = txtLivre.trim().toUpperCase(Locale.ROOT);

	        if ("MENU".equals(upper)) {

	            // Limpa apenas o fluxo genérico do cliente (campo "aguardando")
	            // Obs: não mexe em flags de admin; admin tem seus próprios fluxos.
	            sessaoService.limparAguardando(idSessao);

	            MensagemWhatsappSaidaDTO mensagemSaida = temSaidaAnterior
	                ? menusClienteService.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente)
	                : menusClienteService.montarMenuPrincipal(estabelecimento, whatsappCliente);

	            return new RoteamentoResultado("menu_principal", mensagemSaida);
	        }
	    }

	    // =========================================================
	    // 0) Admin aguardando digitação (prioridade máxima)
	    //
	    // IMPORTANTE:
	    // - TUDO que for "admin por digitação" deve ficar dentro desse if.
	    // - Caso contrário, um cliente pode cair em um estado de admin
	    //   e o texto dele será interpretado como comando/configuração.
	    // =========================================================
	    boolean isAdminAtivo = administradorWhatsappService.isAdminAtivo(estabelecimento, whatsappCliente);

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

	        // ✅ CORREÇÃO: Taxa padrão também é fluxo de ADMIN por digitação
	        // Se isso ficar fora do if(isAdminAtivo), um cliente pode “cair” aqui e salvar o CEP como dinheiro.
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

	    // =========================================================
	    // FAILSAFE (recomendado):
	    // Se NÃO é admin ativo, ignoramos qualquer estado de admin remanescente.
	    //
	    // Motivo:
	    // - evita “sessão contaminada” (ex.: admin abriu menu e largou, depois cliente manda CEP).
	    // - não depende de fluxo perfeito para não causar dano.
	    //
	    // Observação:
	    // - Só limpamos estados de admin, não mexe no fluxo cliente.
	    // =========================================================
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

	    // =========================================================
	    // 1) Fluxo cliente aguardando texto (CEP -> complemento -> fallback)
	    // =========================================================
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
	        MensagemWhatsappSaidaDTO m = menusClienteService.montarEscolhaFormaPagamento(whatsappCliente);
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

	    // =========================================================
	    // 2) Fallback: menu
	    // =========================================================
	    MensagemWhatsappSaidaDTO fallback = temSaidaAnterior
	        ? menusClienteService.montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente)
	        : menusClienteService.montarMenuPrincipal(estabelecimento, whatsappCliente);

	    return new RoteamentoResultado("menu_principal", fallback);
	}
}