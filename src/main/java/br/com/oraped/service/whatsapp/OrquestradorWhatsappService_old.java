// src/main/java/br/com/oraped/service/whatsapp/OrquestradorWhatsappService.java
package br.com.oraped.service.whatsapp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.oraped.domain.CategoriaProduto;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.Pedido;
import br.com.oraped.domain.Produto;
import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.enums.TipoAtendimento;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.ClienteRequestDTO;
import br.com.oraped.dto.ItemPedidoRequestDTO;
import br.com.oraped.dto.PedidoRequestDTO;
import br.com.oraped.dto.PedidoResponseDTO;
import br.com.oraped.dto.whatsapp.entrada.MensagemWhatsappEntradaDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.dto.whatsapp.saida.RespostaWhatsappDTO;
import br.com.oraped.service.ClienteService;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.PedidoService;
import br.com.oraped.service.whatsapp.administrador.AdministradorWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorWhatsappService_old {

    private final AdministradorWhatsappService administradorWhatsappService;
    private final EstabelecimentoService estabelecimentoService;
    private final ClienteService clienteService;
    private final PedidoService pedidoService;
    
    private final SessaoAtendimentoWhatsappService sessaoService;
    private final MensagemAtendimentoWhatsappService mensagemService;
    
    private final WhatsappMensagemFactory msg;

    private final ObjectMapper objectMapper;

    
    
    //======================PROCESSADOR DE MENSAGENS=====================
    public RespostaWhatsappDTO processar(MensagemWhatsappEntradaDTO req) {

        String whatsappCliente = msg.normalizarSomenteDigitos(req.getWhatsappCliente());
        String whatsappReceptor = msg.normalizarSomenteDigitos(req.getWhatsappReceptor());
        String phoneNumberId = msg.safe(req.getPhoneNumberId());

        System.out.println("[WA] INICIO processar"
            + " waCliente=" + whatsappCliente
            + " waReceptor=" + whatsappReceptor
            + " idMsg=" + req.getIdMensagem()
            + " phoneNumberId=" + req.getPhoneNumberId()
        );

        try {

            Estabelecimento estabelecimento = estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);

            SessaoAtendimentoWhatsapp sessao = sessaoService.obterOuCriar(
                whatsappCliente,
                whatsappReceptor,
                estabelecimento.getId()
            );

            boolean temSaidaAnterior = mensagemService.buscarUltimaSaida(sessao.getId()).isPresent();

            // -------------------------
            // DEBUG ENTRADA (DTO)
            // -------------------------
            System.out.println("[WA] ENTRADA safeTextoEntrada=" + safeTextoEntrada(req));
            System.out.println("[WA] ENTRADA getTextoOuComando=" + req.getTextoOuComando());
            System.out.println("[WA] DTO texto=" + req.getTexto());
            System.out.println("[WA] DTO comando=" + req.getComando());
            System.out.println("[WA] DTO textoOuComando=" + req.getTextoOuComando());

            // registra entrada
            mensagemService.registrarEntrada(
                sessao.getId(),
                safeTextoEntrada(req),
                req.getPayloadOriginal()
            );

            // -------------------------
            // DEBUG PAYLOAD ORIGINAL
            // -------------------------
            Object poObj = req.getPayloadOriginal();
            String po = poObj == null ? null : String.valueOf(poObj);

            System.out.println("[WA] PAYLOAD original null? " + (poObj == null));
            System.out.println("[WA] PAYLOAD original str size=" + (po == null ? 0 : po.length()));

            if (po != null) {
                String head = po.substring(0, Math.min(400, po.length())).replace("\n", " ");
                System.out.println("[WA] PAYLOAD head=" + head);
                System.out.println("[WA] PAYLOAD hasInteractive=" + po.contains("\"interactive\""));
                System.out.println("[WA] PAYLOAD hasListReply=" + po.contains("list_reply"));
                System.out.println("[WA] PAYLOAD hasButtonReply=" + po.contains("button_reply"));
                System.out.println("[WA] PAYLOAD hasCOMANDO=" + po.contains("COMANDO|"));
            }

            // -------------------------
            // PARSE COMANDO
            // -------------------------
            String textoOuComando = req.getTextoOuComando();
            ComandoWhatsapp comando = ComandoWhatsapp.parse(textoOuComando);

            System.out.println("[WA] PARSE isEhComando=" + comando.isEhComando()
                + " acao=" + comando.getAcao()
                + " p2=" + comando.getParte(2)
                + " p3=" + comando.getParte(3)
                + " raw=" + textoOuComando
            );

            // =========================================================
            // 1) TEXTO LIVRE -> estados da sessão / fallback
            // =========================================================
            boolean cairTextoLivre = (!StringUtils.hasText(textoOuComando) || !comando.isEhComando());
            System.out.println("[WA] FLOW cairTextoLivre=" + cairTextoLivre);

            if (cairTextoLivre) {

                String txtLivre = msg.safe(safeTextoEntrada(req));
                if (StringUtils.hasText(txtLivre)) {

                    String upper = txtLivre.trim().toUpperCase(Locale.ROOT);

                    if ("MENU".equals(upper)) {

                        sessaoService.limparAguardando(sessao.getId());
                        
                        MensagemWhatsappSaidaDTO mensagemSaida = temSaidaAnterior
                            ? montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente)
                            : montarMenuPrincipal(estabelecimento, whatsappCliente);

                        mensagemService.registrarSaida(sessao.getId(), "menu_principal", mensagemSaida);
                        return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                    }

                    
                }

                System.out.println("[WA] FLOW entrouTextoLivre"
                    + " aguardandoEndereco=" + sessaoService.isAguardandoEnderecoEntrega(sessao.getId())
                    + " aguardandoPagamento=" + sessaoService.isAguardandoFormaPagamento(sessao.getId())
                    + " aguardandoTrocoConfirm=" + sessaoService.isAguardandoTrocoConfirmacao(sessao.getId())
                    + " aguardandoTrocoValor=" + sessaoService.isAguardandoTrocoValor(sessao.getId())
                    + " aguardandoConfirmacaoFinal=" + sessaoService.isAguardandoConfirmacaoFinal(sessao.getId())
                    + " aguardandoNovoPreco=" + sessaoService.isAguardandoNovoPreco(sessao.getId())
                );

                // 0) Se admin estiver aguardando algo digitado, trata ANTES de qualquer coisa
                if (administradorWhatsappService.isAdminAtivo(estabelecimento, whatsappCliente)) {

                    if (sessaoService.isAguardandoNovoPreco(sessao.getId())) {

                        var r = administradorWhatsappService.concluirPrecoManualProdutoPorDigitacao(
                            estabelecimento,
                            whatsappCliente,
                            sessao.getId(),
                            safeTextoEntrada(req)
                        );

                        String corpoConfirmacao =
                            "✅ Preço atualizado!\n\n" +
                                "*" + msg.trunc(msg.safe(r.nomeProduto), 80) + "*\n" +
                                msg.trunc(msg.safe(r.descricaoProduto), 500) + "\n\n" +
                                "*Novo preço:* " + msg.formatarMoeda(r.novoPreco);

                        MensagemWhatsappSaidaDTO confirmacao = msg.texto(whatsappCliente, msg.trunc(corpoConfirmacao, 1024));
                        List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

                        RoteamentoResultado roteado = new RoteamentoResultado("admin_preco_atualizado_digitacao", confirmacao, extras);

                        mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
                        return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);
                    }

                    if (sessaoService.isAguardandoNovoNomeProduto(sessao.getId())) {

                        var r = administradorWhatsappService.concluirAlteracaoNomeProdutoPorDigitacao(
                            estabelecimento,
                            whatsappCliente,
                            sessao.getId(),
                            safeTextoEntrada(req)
                        );

                        RoteamentoResultado roteado = new RoteamentoResultado(r.chave, r.mensagem);

                        mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
                        return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);
                    }

                    if (sessaoService.isAguardandoNovaDescricaoProduto(sessao.getId())) {

                        var r = administradorWhatsappService.concluirAlteracaoDescricaoProdutoPorDigitacao(
                            estabelecimento,
                            whatsappCliente,
                            sessao.getId(),
                            safeTextoEntrada(req)
                        );

                        RoteamentoResultado roteado = new RoteamentoResultado(r.chave, r.mensagem);

                        mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
                        return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);
                    }

                    if (sessaoService.isAguardandoNovaMarca(sessao.getId())) {

                        var r = administradorWhatsappService.concluirCadastroMarcaPorDigitacao(
                            estabelecimento,
                            whatsappCliente,
                            sessao.getId(),
                            safeTextoEntrada(req)
                        );

                        String corpoConfirmacao =
                            "✅ Marca cadastrada!\n\n" +
                                "*" + msg.trunc(msg.safe(r.nomeMarca), 120) + "*";

                        MensagemWhatsappSaidaDTO confirmacao = msg.texto(whatsappCliente, msg.trunc(corpoConfirmacao, 1024));
                        List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

                        RoteamentoResultado roteado = new RoteamentoResultado("admin_marca_criada_digitacao", confirmacao, extras);

                        mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
                        return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);
                    }

                    if (sessaoService.isAguardandoEditarMarcaNome(sessao.getId())) {

                        var r = administradorWhatsappService.concluirAlteracaoNomeMarcaPorDigitacao(
                            estabelecimento,
                            whatsappCliente,
                            sessao.getId(),
                            safeTextoEntrada(req)
                        );

                        String corpoConfirmacao =
                            "✅ Nome da marca atualizado!\n\n" +
                                "*" + msg.trunc(msg.safe(r.nomeMarca), 120) + "*";

                        MensagemWhatsappSaidaDTO confirmacao = msg.texto(whatsappCliente, msg.trunc(corpoConfirmacao, 1024));
                        List<MensagemWhatsappSaidaDTO> extras = List.of(r.admin.mensagem);

                        RoteamentoResultado roteado = new RoteamentoResultado("admin_marca_nome_atualizado_digitacao", confirmacao, extras);

                        mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
                        return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);
                    }
                    
                    
                    if (sessaoService.isAguardandoCepEstabelecimento(sessao.getId())) {

                        var r = administradorWhatsappService.concluirCadastroCepLojaPorDigitacao(
                            estabelecimento,
                            whatsappCliente,
                            sessao.getId(),
                            safeTextoEntrada(req)
                        );

                        boolean salvouCep = r != null && r.chave != null && r.chave.startsWith("admin_entregas_cep_salvo");

                        if (!salvouCep) {
                            RoteamentoResultado roteado = new RoteamentoResultado(r.chave, r.mensagem);
                            mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
                            return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);
                        }

                        // Recarrega estabelecimento para refletir CEP/bairro base atualizados
                        Estabelecimento atualizado = estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);

                        MensagemWhatsappSaidaDTO menuEntregas =
                            administradorWhatsappService.montarMenuEntregas(atualizado, whatsappCliente).mensagem;

                        RoteamentoResultado roteado = new RoteamentoResultado(
                            r.chave,
                            r.mensagem,
                            List.of(menuEntregas)
                        );

                        mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
                        return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);
                    }
                    
                    
                    if (sessaoService.isAguardandoTaxaEntregaBairro(sessao.getId())) {

                        AdministradorWhatsappService.ResultadoAdmin r =
                            administradorWhatsappService.concluirCadastroTaxaEntregaBairroPorDigitacao(
                                estabelecimento,
                                whatsappCliente,
                                sessao.getId(),
                                safeTextoEntrada(req)
                            );

                        RoteamentoResultado roteado = new RoteamentoResultado(r.chave, r.mensagem);

                        mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
                        return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);
                    }
                }
                
                if (sessaoService.isAguardandoTaxaEntregaPadrao(sessao.getId())) {

                    AdministradorWhatsappService.ResultadoAdmin r =
                        administradorWhatsappService.concluirCadastroTaxaEntregaPadraoPorDigitacao(
                            estabelecimento,
                            whatsappCliente,
                            sessao.getId(),
                            safeTextoEntrada(req)
                        );

                    // recarrega estabelecimento para refletir a taxa nova
                    Estabelecimento atualizado = estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);

                    MensagemWhatsappSaidaDTO menuEntregas =
                        administradorWhatsappService.montarMenuEntregas(atualizado, whatsappCliente).mensagem;

                    // ou, se preferir, voltar pro menu de taxas:
                    // MensagemWhatsappSaidaDTO menuTaxas = administradorWhatsappService.montarMenuTaxasEntrega(atualizado, whatsappCliente).mensagem;

                    RoteamentoResultado roteado = new RoteamentoResultado(
                        r.chave,
                        r.mensagem,
                        List.of(menuEntregas)
                    );

                    mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
                    return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);
                }

                if (sessaoService.isAguardandoEnderecoEntrega(sessao.getId())) {

                    MensagemWhatsappSaidaDTO mensagemSaida = tratarEnderecoEntregaInformado(
                        estabelecimento,
                        whatsappCliente,
                        sessao.getId(),
                        safeTextoEntrada(req)
                    );

                    mensagemService.registrarSaida(sessao.getId(), "endereco_entrega_registrado", mensagemSaida);
                    return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                }

                if (sessaoService.isAguardandoFormaPagamento(sessao.getId())) {

                    MensagemWhatsappSaidaDTO mensagemSaida = montarEscolhaFormaPagamento(whatsappCliente);

                    mensagemService.registrarSaida(sessao.getId(), "forma_pagamento_menu", mensagemSaida);
                    return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                }

                if (sessaoService.isAguardandoTrocoConfirmacao(sessao.getId())) {

                    MensagemWhatsappSaidaDTO mensagemSaida = montarPerguntaTrocoSimNao(whatsappCliente);

                    mensagemService.registrarSaida(sessao.getId(), "troco_confirmacao_menu", mensagemSaida);
                    return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                }

                if (sessaoService.isAguardandoTrocoValor(sessao.getId())) {

                    MensagemWhatsappSaidaDTO mensagemSaida = tratarValorTrocoInformado(
                        estabelecimento,
                        whatsappCliente,
                        sessao.getId(),
                        safeTextoEntrada(req)
                    );

                    mensagemService.registrarSaida(sessao.getId(), "troco_valor_registrado", mensagemSaida);
                    return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                }

                if (sessaoService.isAguardandoConfirmacaoFinal(sessao.getId())) {

                    SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(sessao.getId());
                    MensagemWhatsappSaidaDTO mensagemSaida = montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);
                    
                    mensagemService.registrarSaida(sessao.getId(), "confirmacao_final", mensagemSaida);
                    return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                }

                MensagemWhatsappSaidaDTO mensagemSaida = temSaidaAnterior
                    ? montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente)
                    : montarMenuPrincipal(estabelecimento, whatsappCliente);

                mensagemService.registrarSaida(sessao.getId(), "menu_principal", mensagemSaida);
                return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
            }

            // =========================================================
            // 2) COMANDO -> roteia e retorna (com extras)
            // =========================================================
            System.out.println("[WA] FLOW entrandoRoteamento acao=" + comando.getAcao()
                + " raw=" + textoOuComando
            );

            RoteamentoResultado roteado;
            try {
                roteado = rotearComando(estabelecimento, whatsappCliente, whatsappReceptor, phoneNumberId, sessao.getId(), comando);
            } catch (Exception e) {

                System.out.println("[WA] ERRO rotearComando: " + e.getClass().getName() + " - " + e.getMessage());
                e.printStackTrace();

                MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                    whatsappCliente,
                    "⚠️ Erro ao processar sua solicitação.\n\nTente novamente."
                );

                mensagemService.registrarSaida(sessao.getId(), "erro_roteamento", mensagemSaida);
                return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
            }

            mensagemService.registrarSaida(sessao.getId(), roteado.chave, roteado.mensagem);
            return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.mensagem, roteado.extras);

        } catch (ResponseStatusException ex) {

            System.out.println("[WA] ERRO ResponseStatusException status="
                + ex.getStatusCode().value()
                + " msg=" + ex.getReason()
            );

            if (ex.getStatusCode().value() == 404) {

                MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                    whatsappCliente,
                    "Ops! 😕\n"
                        + "Não encontrei o estabelecimento para esse número.\n\n"
                        + "Confira se você chamou o WhatsApp correto e tente novamente."
                );

                return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
            }

            throw ex;
        }
    }

    // ======================================================================
    // RESPOSTA AO N8N
    // ======================================================================

    private RespostaWhatsappDTO montarResposta(
        MensagemWhatsappEntradaDTO req,
        String whatsappCliente,
        String whatsappReceptor,
        MensagemWhatsappSaidaDTO mensagemSaida
    ) {
        return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida, List.of());
    }

 

    private RespostaWhatsappDTO montarResposta(
        MensagemWhatsappEntradaDTO req,
        String whatsappCliente,
        String whatsappReceptor,
        MensagemWhatsappSaidaDTO mensagemSaida,
        List<MensagemWhatsappSaidaDTO> extras
    ) {

        try {
            String jsonMsg = objectMapper.writeValueAsString(mensagemSaida);
            System.out.println("[WA] SAIDA msgJson=" + jsonMsg);
        } catch (Exception e) {
            System.out.println("[WA] SAIDA msgJson=ERRO serializando mensagemSaida: " + e.getMessage());
        }

        try {
            int qtdExtras = (extras == null ? 0 : extras.size());
            System.out.println("[WA] SAIDA extrasQtd=" + qtdExtras);

            if (extras != null && !extras.isEmpty()) {
                String jsonExtras = objectMapper.writeValueAsString(extras);
                System.out.println("[WA] SAIDA extrasJson=" + jsonExtras);
            }
        } catch (Exception e) {
            System.out.println("[WA] SAIDA extrasJson=ERRO serializando extras: " + e.getMessage());
        }

        RespostaWhatsappDTO resp = RespostaWhatsappDTO.builder()
            .idCorrelacao(StringUtils.hasText(req.getIdCorrelacao()) ? req.getIdCorrelacao() : UUID.randomUUID().toString())
            .timestamp(OffsetDateTime.now().toString())
            .canal("WHATSAPP")
            .whatsappCliente(whatsappCliente)
            .whatsappReceptor(whatsappReceptor)
            .phoneNumberId(req.getPhoneNumberId())
            .wamidEntrada(req.getIdMensagem()) // ✅ aqui
            .mensagem(mensagemSaida)
            .mensagensExtras(extras == null ? List.of() : extras)
            .build();

        try {
            String jsonResp = objectMapper.writeValueAsString(resp);
            System.out.println("[WA] RETURN respJson=" + jsonResp);
        } catch (Exception e) {
            System.out.println("[WA] RETURN respJson=ERRO serializando resp: " + e.getMessage());
        }

        return resp;
    }

    private static class RoteamentoResultado {
        final String chave;
        final MensagemWhatsappSaidaDTO mensagem;
        final List<MensagemWhatsappSaidaDTO> extras;

        RoteamentoResultado(String chave, MensagemWhatsappSaidaDTO mensagem) {
            this(chave, mensagem, List.of());
        }

        RoteamentoResultado(String chave, MensagemWhatsappSaidaDTO mensagem, List<MensagemWhatsappSaidaDTO> extras) {
            this.chave = chave;
            this.mensagem = mensagem;
            this.extras = extras == null ? List.of() : extras;
        }
    }

    // ======================================================================
    // ROTEAMENTO DE COMANDOS
    // ======================================================================

    private RoteamentoResultado rotearComando(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    String whatsappReceptor,
	    String phoneNumberId,
	    Long idSessao,
	    ComandoWhatsapp cmd
	) {

	    System.out.println("[WA] ROTEAR_COMANDO acao=" + cmd.getAcao()
	        + " p2=" + cmd.getParte(2)
	        + " p3=" + cmd.getParte(3)
	    );

	    String acao = cmd.getAcao();

	    if (acao != null && acao.startsWith("ADMIN_")) {
	        System.out.println("[WA] ROTEAR_COMANDO -> ADMIN acao=" + acao);
	        return rotearAdmin(estabelecimento, whatsappCliente, idSessao, cmd);
	    }

	    switch (acao) {

	        case "FAZER_PEDIDO":
	        case "INCLUIR_OUTRO_ITEM":
	            return new RoteamentoResultado("lista_categorias", montarListaCategorias(estabelecimento, whatsappCliente));

	        case "VISUALIZAR_CARRINHO":
	            return new RoteamentoResultado("visualizar_carrinho", montarVisualizacaoCarrinho(estabelecimento, whatsappCliente, idSessao));

	        case "LIMPAR_CARRINHO":
	            sessaoService.limparPedidoEmAndamento(idSessao);
	            return new RoteamentoResultado("carrinho_limpo", montarCarrinhoLimpo(estabelecimento, whatsappCliente, idSessao));

	        case "INFORMAR_ENDERECO":
	            return tratarFluxoEndereco(estabelecimento, whatsappCliente, idSessao);
	            
	        case "FAZER_PEDIDO_COM_ENDERECO_ANTERIOR":
	            return tratarConfirmacaoEnderecoAnterior(estabelecimento, whatsappCliente, whatsappReceptor, phoneNumberId, idSessao);

	        case "INFORMAR_OUTRO_ENDERECO":
	            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
	            MensagemWhatsappSaidaDTO mEnd = montarSolicitacaoEnderecoEntrega(whatsappCliente);
	            return new RoteamentoResultado("solicitar_endereco_entrega", mEnd);

	        case "SELECIONAR_PAGAMENTO":
	            return tratarSelecaoPagamento(estabelecimento, whatsappCliente, whatsappReceptor, phoneNumberId, idSessao, cmd.getParte(2));

	        case "TROCO":
	            return tratarTroco(estabelecimento, whatsappCliente, whatsappReceptor, phoneNumberId, idSessao, cmd.getParte(2));
	            
	        case "ENVIAR_PEDIDO":
	            // ✅ ao enviar, não pode continuar aguardando confirmação final
	            sessaoService.desmarcarAguardandoConfirmacaoFinal(idSessao);
	            return tratarEnvioPedidoDefinitivo(estabelecimento, whatsappCliente, idSessao);

	        case "LISTA_PRODUTOS": {
	            Long idCategoria = parseLongObrigatorio(cmd.getParte(2), "idCategoria");
	            Integer quantidadeMultipla = parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
	            return new RoteamentoResultado(
	                "lista_produtos",
	                montarListaProdutosPorCategoriaPaginada(estabelecimento, whatsappCliente, idCategoria, quantidadeMultipla, 0)
	            );
	        }

	        case "LISTA_PRODUTOS_PAG": {
	            Long idCategoria = parseLongObrigatorio(cmd.getParte(2), "idCategoria");
	            Integer quantidadeMultipla = parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
	            Integer offset = parseIntObrigatorio(cmd.getParte(4), "offset");
	            return new RoteamentoResultado(
	                "lista_produtos",
	                montarListaProdutosPorCategoriaPaginada(estabelecimento, whatsappCliente, idCategoria, quantidadeMultipla, offset)
	            );
	        }

	        case "LISTAR_QUANTIDADES": {
	            Long idCategoria = parseLongObrigatorio(cmd.getParte(2), "idCategoria");
	            Integer quantidadeMultipla = parseIntObrigatorio(cmd.getParte(3), "quantidadeMultipla");
	            Long idProduto = parseLongObrigatorio(cmd.getParte(4), "idProduto");
	            return new RoteamentoResultado(
	                "listar_quantidades",
	                montarListaQuantidades(estabelecimento, whatsappCliente, idCategoria, quantidadeMultipla, idProduto)
	            );
	        }

	        case "ADICIONAR_PRODUTO": {
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer quantidade = parseIntObrigatorio(cmd.getParte(3), "quantidade");
	            return tratarAdicionarProduto(estabelecimento, whatsappCliente, idProduto, quantidade);
	        }

	        case "SOLICITAR_QUANTIDADE": {
	            return new RoteamentoResultado(
	                "solicitar_quantidade_manual",
	                msg.texto(
	                    whatsappCliente,
	                    "Certo! Me informe a quantidade desejada para o produto.\n\nExemplo: 15"
	                )
	            );
	        }
	        
	        case "ULTIMO_PEDIDO":
	            return tratarUltimoPedidoParaRevisao(estabelecimento, whatsappCliente);

	        // =========================
	        // REVISÃO DE PEDIDO (cliente)
	        // =========================
	        case "PEDIDO_REVISAR": {
	            Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");
	            return tratarTelaRevisaoPedido(estabelecimento, whatsappCliente, idPedido);
	        }

            case "REVISAO_ADICIONAR_ITENS": {
                Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");

                PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(
                    estabelecimento.getId(),
                    idPedido,
                    whatsappCliente
                );

                if (pedido == null || pedido.getStatus() == null) {
                    return new RoteamentoResultado(
                        "revisao_pedido_nao_encontrado",
                        msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
                    );
                }

                if (pedido.getStatus() != StatusPedido.CRIADO) {
                    MensagemWhatsappSaidaDTO tela = montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado("revisao_bloqueio_adicionar_itens", aviso, List.of(tela));
                }

                return new RoteamentoResultado(
                    "revisao_lista_categorias",
                    montarListaCategoriasRevisao(estabelecimento, whatsappCliente, idPedido)
                );
            }

            case "REVISAO_LISTA_PRODUTOS": {
                Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");
                Long idCategoria = parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer quantidadeMultipla = parseIntObrigatorio(cmd.getParte(4), "quantidadeMultipla");

                PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(
                    estabelecimento.getId(),
                    idPedido,
                    whatsappCliente
                );

                if (pedido == null || pedido.getStatus() == null) {
                    return new RoteamentoResultado(
                        "revisao_pedido_nao_encontrado",
                        msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
                    );
                }

                if (pedido.getStatus() != StatusPedido.CRIADO) {
                    MensagemWhatsappSaidaDTO tela = montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado("revisao_bloqueio_listar_produtos", aviso, List.of(tela));
                }

                return new RoteamentoResultado(
                    "revisao_lista_produtos",
                    montarListaProdutosPorCategoriaPaginadaRevisao(estabelecimento, whatsappCliente, idPedido, idCategoria, quantidadeMultipla, 0)
                );
            }

            case "REVISAO_LISTA_PRODUTOS_PAG": {
                Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");
                Long idCategoria = parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer quantidadeMultipla = parseIntObrigatorio(cmd.getParte(4), "quantidadeMultipla");
                Integer offset = parseIntObrigatorio(cmd.getParte(5), "offset");

                PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(
                    estabelecimento.getId(),
                    idPedido,
                    whatsappCliente
                );

                if (pedido == null || pedido.getStatus() == null) {
                    return new RoteamentoResultado(
                        "revisao_pedido_nao_encontrado",
                        msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
                    );
                }

                if (pedido.getStatus() != StatusPedido.CRIADO) {
                    MensagemWhatsappSaidaDTO tela = montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado("revisao_bloqueio_listar_produtos_pag", aviso, List.of(tela));
                }

                return new RoteamentoResultado(
                    "revisao_lista_produtos",
                    montarListaProdutosPorCategoriaPaginadaRevisao(estabelecimento, whatsappCliente, idPedido, idCategoria, quantidadeMultipla, offset)
                );
            }

            case "REVISAO_LISTAR_QUANTIDADES": {
                Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");
                Long idCategoria = parseLongObrigatorio(cmd.getParte(3), "idCategoria");
                Integer quantidadeMultipla = parseIntObrigatorio(cmd.getParte(4), "quantidadeMultipla");
                Long idProduto = parseLongObrigatorio(cmd.getParte(5), "idProduto");

                PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(
                    estabelecimento.getId(),
                    idPedido,
                    whatsappCliente
                );

                if (pedido == null || pedido.getStatus() == null) {
                    return new RoteamentoResultado(
                        "revisao_pedido_nao_encontrado",
                        msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
                    );
                }

                if (pedido.getStatus() != StatusPedido.CRIADO) {
                    MensagemWhatsappSaidaDTO tela = montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);
                    MensagemWhatsappSaidaDTO aviso = msg.texto(
                        whatsappCliente,
                        "⚠️ Você só pode *adicionar itens* enquanto o pedido está *aguardando confirmação*."
                    );
                    return new RoteamentoResultado("revisao_bloqueio_listar_quantidades", aviso, List.of(tela));
                }

                return new RoteamentoResultado(
                    "revisao_listar_quantidades",
                    montarListaQuantidadesRevisao(estabelecimento, whatsappCliente, idPedido, idCategoria, quantidadeMultipla, idProduto)
                );
            }

	        case "REVISAO_ADICIONAR_PRODUTO": {
	            Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");
	            Long idProduto = parseLongObrigatorio(cmd.getParte(3), "idProduto");
	            Integer quantidade = parseIntObrigatorio(cmd.getParte(4), "quantidade");
	            return tratarRevisaoAdicionarProduto(estabelecimento, whatsappCliente, idPedido, idProduto, quantidade);
	        }

	        case "REVISAO_CANCELAR_PEDIDO": {
	            Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");
	            return tratarRevisaoCancelarPedido(estabelecimento, whatsappCliente, idPedido);
	        }

	        case "REVISAO_CONFIRMAR_ENTREGA": {
	            Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");
	            return tratarRevisaoConfirmarEntrega(estabelecimento, whatsappCliente, idPedido);
	        }
	        default:
	            return new RoteamentoResultado("comando_desconhecido", montarMenuPrincipalSemSaudacao(estabelecimento, whatsappCliente));
	    }
	}

    private RoteamentoResultado rotearAdmin(
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
	            StatusPedido status = parseStatusPedidoObrigatorio(cmd.getParte(2));
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.listarPedidosPorStatus(estabelecimento, whatsappAdmin, status, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PEDIDO_DETALHE": {
	            Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.montarDetalhePedido(estabelecimento, whatsappAdmin, idPedido);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ACEITAR_PEDIDO":
	            return executarAcaoPedidoAdmin(
	                estabelecimento,
	                whatsappAdmin,
	                cmd,
	                AdministradorWhatsappService.AcaoPedidoAdmin.ACEITAR
	            );

	        case "ADMIN_RECUSAR_PEDIDO":
	            return executarAcaoPedidoAdmin(
	                estabelecimento,
	                whatsappAdmin,
	                cmd,
	                AdministradorWhatsappService.AcaoPedidoAdmin.RECUSAR
	            );

	        case "ADMIN_PREPARAR_PEDIDO":
	            return executarAcaoPedidoAdmin(
	                estabelecimento,
	                whatsappAdmin,
	                cmd,
	                AdministradorWhatsappService.AcaoPedidoAdmin.PREPARAR
	            );

	        case "ADMIN_CANCELAR_PEDIDO":
	            return executarAcaoPedidoAdmin(
	                estabelecimento,
	                whatsappAdmin,
	                cmd,
	                AdministradorWhatsappService.AcaoPedidoAdmin.CANCELAR
	            );

	        case "ADMIN_INICIAR_ENTREGA":
	            return executarAcaoPedidoAdmin(
	                estabelecimento,
	                whatsappAdmin,
	                cmd,
	                AdministradorWhatsappService.AcaoPedidoAdmin.INICIAR_ENTREGA
	            );

	        // ============== PRODUTOS (suspender/liberar) ==============
	        case "ADMIN_SUSPENDER_PRODUTO_MENU": {
	            Integer offset = parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.listarProdutosParaSuspender(estabelecimento, whatsappAdmin, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_LIBERAR_PRODUTO_MENU": {
	            Integer offset = parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.listarProdutosParaLiberar(estabelecimento, whatsappAdmin, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_SUSPENDER_PRODUTO": {
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.suspenderProduto(estabelecimento, whatsappAdmin, idProduto);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_LIBERAR_PRODUTO": {
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");

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
	            Integer offset = parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.montarMenuCardapioProdutos(estabelecimento, whatsappAdmin, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_CARDAPIO_PRODUTO": {
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.montarMenuAcoesProduto(estabelecimento, whatsappAdmin, idProduto, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        // ============== PRODUTO: PREÇO ==============
	        case "ADMIN_PROD_PRECO_MENU": {
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.montarMenuAjustePrecoProduto(estabelecimento, whatsappAdmin, idProduto, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_PRECO_APLICAR": {
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer deltaCentavos = parseIntDefaultZeroAllowNegative(cmd.getParte(3));
	            Integer offset = parseIntDefaultZero(cmd.getParte(4));

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
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

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
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

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
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

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
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.confirmarExclusaoProduto(estabelecimento, whatsappAdmin, idProduto, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_EXCLUIR": {
	            Long idProduto = parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.excluirProduto(estabelecimento, whatsappAdmin, idProduto, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        // ============== MARCAS ==============
	        case "ADMIN_CARDAPIO_MARCAS_MENU": {
	            Integer offset = parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.montarMenuMarcas(estabelecimento, whatsappAdmin, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_MARCA_DETALHE": {
	            Long idMarca = parseLongObrigatorio(cmd.getParte(2), "idMarca");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.montarDetalheMarca(estabelecimento, whatsappAdmin, idMarca, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_MARCA_NOVA_MENU": {
	            Integer offset = parseIntDefaultZero(cmd.getParte(2));

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
	            Long idMarca = parseLongObrigatorio(cmd.getParte(2), "idMarca");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

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
	            Long idMarca = parseLongObrigatorio(cmd.getParte(2), "idMarca");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.confirmarExclusaoMarca(estabelecimento, whatsappAdmin, idMarca, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_MARCA_EXCLUIR": {
	            Long idMarca = parseLongObrigatorio(cmd.getParte(2), "idMarca");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

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

	        case "ADMIN_ENTREGAS_CEP_MENU": {

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.iniciarCadastroCepLojaPorDigitacao(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idSessao
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        
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
	            // no próximo passo: iniciar fluxo de digitação do valor (taxa padrão)
	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.montarMenuTaxaPadrao(estabelecimento, whatsappAdmin);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_ENTREGAS_BAIRROS_MENU": {
	            Integer offset = parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.montarMenuTaxaPorBairros(estabelecimento, whatsappAdmin, offset);

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        case "ADMIN_ENTREGAS_BAIRRO_SELECIONAR": {
	            Long idBairro = parseLongObrigatorio(cmd.getParte(2), "idBairro");
	            Integer offset = parseIntDefaultZero(cmd.getParte(3));

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
	            // offsetVoltar: pode ser 0 ou vir em cmd.getParte(2)
	            Integer offsetVoltar = parseIntDefaultZero(cmd.getParte(2));

	            AdministradorWhatsappService.ResultadoAdmin r =
	                administradorWhatsappService.iniciarCadastroTaxaEntregaPadraoPorDigitacao(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idSessao,
	                    offsetVoltar
	                );

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

        Long idPedido = parseLongObrigatorio(cmd.getParte(2), "idPedido");

        var r = administradorWhatsappService.executarAcaoPedido(
            estabelecimento,
            whatsappAdmin,
            idPedido,
            acao
        );

        List<MensagemWhatsappSaidaDTO> extras = new ArrayList<>();

        if (StringUtils.hasText(r.whatsappCliente) && StringUtils.hasText(r.textoCliente)) {
            extras.add(msg.texto(r.whatsappCliente, r.textoCliente));
        }

        return new RoteamentoResultado(r.admin.chave, r.admin.mensagem, extras);
    }

    

    // ======================================================================
    // FLUXOS (cliente)
    // ======================================================================

    private RoteamentoResultado tratarFluxoEndereco(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        Map<Long, Integer> carrinho = montarCarrinhoAtual(idSessao);

        if (carrinho.isEmpty()) {

            MensagemWhatsappSaidaDTO saida = msg.botoes(
                whatsappCliente,
                msg.trunc("Seu carrinho está vazio 🛒\n\nInclua pelo menos 1 item para concluir o pedido.", 1024),
                List.of(
                    btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Incluir outro item"),
                    btn("COMANDO|FAZER_PEDIDO", "🛍️ Fazer um pedido")
                )
            );

            return new RoteamentoResultado("bloqueio_carrinho_vazio", saida);
        }

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        if (StringUtils.hasText(s.getEnderecoEntrega())) {

            if (s.getFormaPagamento() == null) {
                sessaoService.marcarAguardandoFormaPagamento(idSessao);
                return new RoteamentoResultado("forma_pagamento_menu", montarEscolhaFormaPagamento(whatsappCliente));
            }

            if (s.getFormaPagamento() == FormaPagamentoPedido.DINHEIRO
                && Boolean.TRUE.equals(s.getPrecisaTroco())
                && s.getTrocoPara() == null
            ) {
                sessaoService.marcarAguardandoTrocoValor(idSessao);
                return new RoteamentoResultado("troco_valor", montarSolicitacaoTrocoValor(whatsappCliente));
            }

            return new RoteamentoResultado("confirmacao_final", montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s));
        }

        String enderecoAnterior = clienteService
            .buscarUltimoEnderecoEntrega(estabelecimento, whatsappCliente)
            .orElse(null);

        if (StringUtils.hasText(enderecoAnterior)) {
            return new RoteamentoResultado("sugestao_endereco_anterior", montarSugestaoEnderecoAnterior(whatsappCliente, enderecoAnterior));
        }

        sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
        return new RoteamentoResultado("solicitar_endereco_entrega", montarSolicitacaoEnderecoEntrega(whatsappCliente));
    }

    private RoteamentoResultado tratarConfirmacaoEnderecoAnterior(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    String whatsappReceptor,
	    String phoneNumberId,
	    Long idSessao
	) {

	    Map<Long, Integer> carrinho = montarCarrinhoAtual(idSessao);

	    if (carrinho.isEmpty()) {
	        return new RoteamentoResultado(
	            "bloqueio_carrinho_vazio",
	            msg.texto(whatsappCliente, "Seu carrinho está vazio 🛒\n\nInclua itens antes de enviar o pedido.")
	        );
	    }

	    String enderecoAnterior = clienteService
	        .buscarUltimoEnderecoEntrega(estabelecimento, whatsappCliente)
	        .orElse(null);

	    if (!StringUtils.hasText(enderecoAnterior)) {
	        sessaoService.marcarAguardandoEnderecoEntrega(idSessao);

	        MensagemWhatsappSaidaDTO m = montarSolicitacaoEnderecoEntrega(whatsappCliente);
	        return new RoteamentoResultado("solicitar_endereco_entrega", m);
	    }

	    sessaoService.salvarEnderecoEntrega(idSessao, enderecoAnterior, null);
	    sessaoService.marcarAguardandoFormaPagamento(idSessao);

	    MensagemWhatsappSaidaDTO m = montarEscolhaFormaPagamento(whatsappCliente);
	    return new RoteamentoResultado("forma_pagamento_menu", m);
	}

    private RoteamentoResultado tratarSelecaoPagamento(
            Estabelecimento estabelecimento,
            String whatsappCliente,
            String whatsappReceptor,
            String phoneNumberId,
            Long idSessao,
            String rawTipo
    ) {
        FormaPagamentoPedido fp = parseFormaPagamento(rawTipo);

        sessaoService.salvarFormaPagamento(idSessao, fp);

        if (fp == FormaPagamentoPedido.DINHEIRO) {
            sessaoService.marcarAguardandoTrocoConfirmacao(idSessao);

            MensagemWhatsappSaidaDTO m = montarPerguntaTrocoSimNao(whatsappCliente);
            return new RoteamentoResultado("troco_confirmacao", m);
        }

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        sessaoService.marcarAguardandoConfirmacaoFinal(idSessao);
        MensagemWhatsappSaidaDTO m = montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);

        return new RoteamentoResultado("confirmacao_final", m);
    }

    private RoteamentoResultado tratarTroco(
            Estabelecimento estabelecimento,
            String whatsappCliente,
            String whatsappReceptor,
            String phoneNumberId,
            Long idSessao,
            String escolha
    ) {

        if ("NAO".equalsIgnoreCase(escolha)) {
            sessaoService.salvarTrocoNecessidade(idSessao, false);

            SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

            sessaoService.marcarAguardandoConfirmacaoFinal(idSessao);
            MensagemWhatsappSaidaDTO m = montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);

            return new RoteamentoResultado("confirmacao_final", m);
        }

        if ("SIM".equalsIgnoreCase(escolha)) {
            sessaoService.salvarTrocoNecessidade(idSessao, true);
            sessaoService.marcarAguardandoTrocoValor(idSessao);

            MensagemWhatsappSaidaDTO m = montarSolicitacaoTrocoValor(whatsappCliente);
            return new RoteamentoResultado("troco_valor", m);
        }

        sessaoService.marcarAguardandoTrocoConfirmacao(idSessao);

        MensagemWhatsappSaidaDTO m = montarPerguntaTrocoSimNao(whatsappCliente);
        return new RoteamentoResultado("troco_confirmacao", m);
    }

    private RoteamentoResultado tratarEnvioPedidoDefinitivo(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        Map<Long, Integer> carrinho = montarCarrinhoAtual(idSessao);

        if (carrinho.isEmpty()) {
            return new RoteamentoResultado(
                "bloqueio_carrinho_vazio",
                msg.texto(whatsappCliente, "Seu carrinho está vazio 🛒\n\nInclua itens antes de enviar o pedido.")
            );
        }

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        if (!StringUtils.hasText(s.getEnderecoEntrega())) {
            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
            return new RoteamentoResultado("solicitar_endereco_entrega", montarSolicitacaoEnderecoEntrega(whatsappCliente));
        }

        if (s.getFormaPagamento() == null) {
            sessaoService.marcarAguardandoFormaPagamento(idSessao);
            return new RoteamentoResultado("forma_pagamento_menu", montarEscolhaFormaPagamento(whatsappCliente));
        }

        if (s.getFormaPagamento() == FormaPagamentoPedido.DINHEIRO
            && Boolean.TRUE.equals(s.getPrecisaTroco())
            && s.getTrocoPara() == null
        ) {
            sessaoService.marcarAguardandoTrocoValor(idSessao);
            return new RoteamentoResultado("troco_valor", montarSolicitacaoTrocoValor(whatsappCliente));
        }

        return enviarPedidoDefinitivo(estabelecimento, whatsappCliente, idSessao, carrinho);
    }

    private RoteamentoResultado tratarAdicionarProduto(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idProduto,
        Integer quantidade
    ) {

        Produto produto = extrairProduto(estabelecimento, idProduto);

        if (produto == null) {
            return new RoteamentoResultado("produto_nao_encontrado", msg.texto(whatsappCliente, "Produto não encontrado."));
        }

        int qtd = quantidade == null ? 0 : quantidade;
        if (qtd < 1) {
            return new RoteamentoResultado("quantidade_invalida", msg.texto(whatsappCliente, "Quantidade inválida."));
        }

        String nome = msg.safe(produto.getNome());
        String descricao = msg.safe(produto.getDescricao());
        BigDecimal total = calcularPrecoPorQuantidade(produto, qtd);

        StringBuilder sb = new StringBuilder();
        sb.append("Perfeito! ✅\n");
        sb.append("Adicionado ao carrinho:\n\n");
        sb.append("*").append(nome).append("*\n");
        if (StringUtils.hasText(descricao)) sb.append(descricao).append("\n");
        sb.append("Quantidade: ").append(qtd).append("\n");
        sb.append("Valor total: ").append(msg.formatarMoeda(total)).append("\n\n");
        sb.append("Vamos continuar seu pedido 👇");

        MensagemWhatsappSaidaDTO saida = msg.botoes(
            whatsappCliente,
            msg.trunc(sb.toString(), 1024),
            List.of(
                btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Comprar mais"),
                btn("COMANDO|VISUALIZAR_CARRINHO", "🛒 Ver o carrinho"),
                btn("COMANDO|INFORMAR_ENDERECO", "🏍️ Ir para entrega")
            )
        );

        return new RoteamentoResultado("produto_adicionado", saida);
    }

    // ======================================================================
    // ENVIO DEFINITIVO
    // ======================================================================

    private RoteamentoResultado enviarPedidoDefinitivo(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        Map<Long, Integer> carrinho
    ) {

        if (carrinho == null || carrinho.isEmpty()) {
            return new RoteamentoResultado(
                "bloqueio_carrinho_vazio",
                msg.texto(whatsappCliente, "Seu carrinho está vazio 🛒\n\nInclua itens antes de enviar o pedido.")
            );
        }

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        if (!StringUtils.hasText(s.getEnderecoEntrega())) {
            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
            return new RoteamentoResultado("solicitar_endereco_entrega", montarSolicitacaoEnderecoEntrega(whatsappCliente));
        }

        PedidoRequestDTO pedidoReq = new PedidoRequestDTO();
        pedidoReq.setIdEstabelecimento(estabelecimento.getId());

        ClienteRequestDTO cli = new ClienteRequestDTO();
        cli.setTelefone(whatsappCliente);
        cli.setNome(null);
        pedidoReq.setCliente(cli);

        pedidoReq.setTipoAtendimento(TipoAtendimento.ENTREGA);
        pedidoReq.setEnderecoEntrega(s.getEnderecoEntrega());
        pedidoReq.setObservacoes(s.getObservacoesEntrega());
        pedidoReq.setTaxaEntrega(BigDecimal.ZERO);
        pedidoReq.setTaxaServico(BigDecimal.ZERO);

        if (s.getFormaPagamento() != null) {
            pedidoReq.setFormaPagamento(s.getFormaPagamento());
            pedidoReq.setPrecisaTroco(s.getPrecisaTroco());
            pedidoReq.setTrocoPara(s.getTrocoPara());
        }

        List<ItemPedidoRequestDTO> itens = carrinho.entrySet().stream()
            .map(e -> {
                ItemPedidoRequestDTO it = new ItemPedidoRequestDTO();
                it.setIdProduto(e.getKey());
                it.setQuantidade(e.getValue());
                it.setObservacoes(null);
                it.setOpcionais(List.of());
                return it;
            })
            .collect(Collectors.toList());

        pedidoReq.setItens(itens);

        PedidoResponseDTO resp = pedidoService.criar(pedidoReq);

        String resumoItens = montarResumoItensDoCarrinho(estabelecimento, carrinho);

        MensagemWhatsappSaidaDTO saidaCliente = montarConfirmacaoPedidoEnviado(
            whatsappCliente,
            resp.getId(),
            s,
            resumoItens,
            resp.getTotal()
        );

        List<String> admins = administradorWhatsappService.listarWhatsappsAdministradoresAtivos(estabelecimento);

        if (admins.isEmpty()) {

            MensagemWhatsappSaidaDTO saidaSemAdmin = msg.texto(
                whatsappCliente,
                msg.trunc(
                    "✅ Pedido enviado!\n\n" +
                        "Número do pedido: *#" + resp.getId() + "*\n" +
                        "No momento não encontrei atendentes ativos para confirmar.\n" +
                        "Assim que possível retornaremos. 🙂",
                    4096
                )
            );

            limparSessaoAposEnviar(idSessao);
            return new RoteamentoResultado("pedido_pendente_sem_admin", saidaSemAdmin);
        }

        List<MensagemWhatsappSaidaDTO> extras = admins.stream()
            .map(admin -> administradorWhatsappService.montarNotificacaoPedidoParaAdmin(
                admin,
                resp.getId(),
                whatsappCliente,
                s.getEnderecoEntrega(),
                s.getObservacoesEntrega(),
                resumoItens,
                resp.getTotal()
            ))
            .collect(Collectors.toList());

        limparSessaoAposEnviar(idSessao);

        return new RoteamentoResultado("pedido_pendente", saidaCliente, extras);
    }

    private void limparSessaoAposEnviar(Long idSessao) {
        sessaoService.limparPedidoEmAndamento(idSessao);
        mensagemService.registrarEntrada(idSessao, "COMANDO|LIMPAR_CARRINHO", null);
    }

    // ======================================================================
    // MENSAGENS: pagamento / troco / confirmação
    // ======================================================================

    private MensagemWhatsappSaidaDTO montarEscolhaFormaPagamento(String whatsappCliente) {

        String corpo =
            "Como deseja pagar?\n\n" +
                "Escolha uma opção:";

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
            btn("COMANDO|SELECIONAR_PAGAMENTO|DINHEIRO", "💵 Dinheiro"),
            btn("COMANDO|SELECIONAR_PAGAMENTO|CREDITO", "💳 Cartão (Crédito)"),
            btn("COMANDO|SELECIONAR_PAGAMENTO|DEBITO_PIX", "🏧 Débito/PIX")
        );

        return msg.botoes(whatsappCliente, msg.trunc(corpo, 1024), botoes);
    }

    private MensagemWhatsappSaidaDTO montarPerguntaTrocoSimNao(String whatsappCliente) {

        String corpo =
            "Você precisa de troco?\n\n" +
                "Escolha uma opção:";

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
            btn("COMANDO|TROCO|NAO", "✅ Não"),
            btn("COMANDO|TROCO|SIM", "💵 Sim, preciso")
        );

        return msg.botoes(whatsappCliente, msg.trunc(corpo, 1024), botoes);
    }

    private MensagemWhatsappSaidaDTO montarSolicitacaoTrocoValor(String whatsappCliente) {

        String corpo =
            "Troco para quanto?\n\n" +
                "Exemplos:\n" +
                "- 50\n" +
                "- 100,00\n" +
                "- R$ 20";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    private MensagemWhatsappSaidaDTO montarConfirmacaoFinalAntesDeEnviar(
            Estabelecimento estabelecimento,
            String whatsappCliente,
            SessaoAtendimentoWhatsapp s
    ) {

        String pagamento = formatarPagamentoParaTexto(s);

        Map<Long, Integer> carrinho = montarCarrinhoAtual(s.getId());

        String itensTexto = "(sem itens)";
        BigDecimal total = BigDecimal.ZERO;

        if (carrinho != null && !carrinho.isEmpty()) {

            StringBuilder sb = new StringBuilder();

            for (var e : carrinho.entrySet()) {

                Long idProduto = e.getKey();
                int qtd = e.getValue() == null ? 0 : e.getValue();

                Produto p = extrairProduto(estabelecimento, idProduto);

                String nome = (p == null ? ("Produto #" + idProduto) : msg.safe(p.getNome()));

                BigDecimal subtotal = (p == null ? BigDecimal.ZERO : calcularPrecoPorQuantidade(p, qtd));
                total = total.add(subtotal);

                sb.append("- ")
                    .append(nome)
                    .append(" x").append(qtd)
                    .append(" = ").append(msg.formatarMoeda(subtotal))
                    .append("\n");
            }

            itensTexto = sb.toString().trim();
        }

        String endereco = msg.trunc(msg.safe(s.getEnderecoEntrega()), 650);

        String obs = msg.safe(s.getObservacoesEntrega());
        String obsFmt = StringUtils.hasText(obs)
            ? ("\n*Obs:* " + msg.trunc(obs, 250) + "\n")
            : "\n";

        String corpo =
            "🔎 *Revise seu pedido antes de enviar*\n\n" +
                "🛒 *Itens:*\n" +
                msg.trunc(itensTexto, 650) + "\n\n" +
                "*Total:* " + msg.formatarMoeda(total) + "\n\n" +
                "📍 *Entrega:*\n" +
                "*" + endereco + "*\n" +
                obsFmt +
                "💳 *Pagamento:* " + pagamento + "\n\n" +
                "Se estiver tudo certo, confirme o envio ✅";

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = List.of(
            btn("COMANDO|ENVIAR_PEDIDO", "✅ Confirmar e enviar"),
            btn("COMANDO|VISUALIZAR_CARRINHO", "✏️ Ajustar carrinho"),
            btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Adicionar itens")
        );

        return msg.botoes(whatsappCliente, msg.trunc(corpo, 1024), botoes);
    }

    private String formatarPagamentoParaTexto(SessaoAtendimentoWhatsapp s) {

        FormaPagamentoPedido fp = s.getFormaPagamento();
        if (fp == null) return "Não informado";

        if (fp == FormaPagamentoPedido.CREDITO) return "Cartão (Crédito)";
        if (fp == FormaPagamentoPedido.DEBITO_PIX) return "Débito/PIX";

        if (Boolean.TRUE.equals(s.getPrecisaTroco())) {
            if (s.getTrocoPara() != null) {
                return "Dinheiro (troco para " + msg.formatarMoeda(s.getTrocoPara()) + ")";
            }
            return "Dinheiro (com troco)";
        }

        if (Boolean.FALSE.equals(s.getPrecisaTroco())) {
            return "Dinheiro (sem troco)";
        }

        return "Dinheiro";
    }

    // ======================================================================
    // ENDEREÇO (parse + persist) -> depois pagamento
    // ======================================================================

    private static class ParsedEndereco {
        final String endereco;
        final String observacoes;

        ParsedEndereco(String endereco, String observacoes) {
            this.endereco = endereco;
            this.observacoes = observacoes;
        }
    }

    private MensagemWhatsappSaidaDTO tratarEnderecoEntregaInformado(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        String textoCliente
    ) {

        String raw = msg.safe(textoCliente);

        if (!StringUtils.hasText(raw) || "(vazio)".equals(raw)) {
            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
            return msg.texto(
                whatsappCliente,
                "Não consegui identificar o endereço 😕\n\n" +
                    "Por favor, me envie o endereço de entrega (com número) e observações pro entregador."
            );
        }

        ParsedEndereco parsed = parseEnderecoEObservacoes(raw);

        if (!StringUtils.hasText(parsed.endereco)) {
            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
            return msg.texto(
                whatsappCliente,
                "Não consegui identificar o endereço 😕\n\n" +
                    "Me envie o endereço (rua, número, bairro) e, se quiser, observações pro entregador."
            );
        }

        sessaoService.salvarEnderecoEntrega(idSessao, parsed.endereco, parsed.observacoes);
        sessaoService.desmarcarAguardandoEnderecoEntrega(idSessao);

        sessaoService.marcarAguardandoFormaPagamento(idSessao);
        return montarEscolhaFormaPagamento(whatsappCliente);
    }

    private MensagemWhatsappSaidaDTO tratarValorTrocoInformado(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        String textoCliente
    ) {

        String raw = msg.safe(textoCliente);

        BigDecimal valor = parseValorMonetario(raw);
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            sessaoService.marcarAguardandoTrocoValor(idSessao);
            return msg.texto(
                whatsappCliente,
                "Não consegui entender o valor do troco 😕\n\n" +
                    "Me informe um valor válido.\n\n" +
                    "Exemplos: 50 | 100,00 | R$ 20"
            );
        }

        sessaoService.salvarTrocoValor(idSessao, valor);

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);
        return montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);
    }

    private ParsedEndereco parseEnderecoEObservacoes(String raw) {

        String txt = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(txt)) return new ParsedEndereco("", null);

        String lower = txt.toLowerCase(Locale.ROOT);

        int idx = indexOfAny(lower,
            "obs:", "obs.:", "observacao:", "observação:", "observacoes:", "observações:"
        );

        if (idx < 0) {
            return new ParsedEndereco(txt, null);
        }

        String endereco = txt.substring(0, idx).trim();

        String obs = txt.substring(idx).trim();
        obs = obs.replaceFirst("(?i)^(obs\\s*:?\\s*\\.?|observa(c|ç)\\w*\\s*:?\\s*)", "").trim();

        return new ParsedEndereco(endereco, StringUtils.hasText(obs) ? obs : null);
    }

    private int indexOfAny(String haystackLower, String... needlesLower) {
        int best = -1;
        for (String n : needlesLower) {
            int i = haystackLower.indexOf(n);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }
    
    
	

    // ======================================================================
    // MENUS / LISTAS / CARRINHO
    // ======================================================================

    private MensagemWhatsappSaidaDTO montarMenuPrincipal(Estabelecimento estabelecimento, String whatsappCliente) {

        if (administradorWhatsappService.isAdminAtivo(estabelecimento, whatsappCliente)) {
            return administradorWhatsappService.montarMenuAdmin(estabelecimento, whatsappCliente).mensagem;
        }

        String cabecalho =
            "Olá! 👋\n" +
                "Você está falando com *" + msg.safe(estabelecimento.getNome()) + "*.\n\n" +
                "Escolha uma opção:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = List.of(
            row("COMANDO|FAZER_PEDIDO", "🛍️ Fazer um pedido", "Ver categorias e escolher produtos"),
            row("COMANDO|ULTIMO_PEDIDO", "Meu último pedido", "Status / dúvidas do pedido mais recente")
        );

        return msg.lista(whatsappCliente, cabecalho, "Ver opções", "Opções", itens);
    }

    private MensagemWhatsappSaidaDTO montarMenuPrincipalSemSaudacao(Estabelecimento estabelecimento, String whatsappCliente) {

        if (administradorWhatsappService.isAdminAtivo(estabelecimento, whatsappCliente)) {
            return administradorWhatsappService.montarMenuAdmin(estabelecimento, whatsappCliente).mensagem;
        }

        String cabecalho = "Escolha uma opção:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = List.of(
            row("COMANDO|FAZER_PEDIDO", "Fazer um pedido", "Ver categorias e escolher produtos"),
            row("COMANDO|ULTIMO_PEDIDO", "Meu último pedido", "Status / dúvidas do pedido mais recente")
        );

        return msg.lista(whatsappCliente, cabecalho, "Ver opções", "Opções", itens);
    }

    private MensagemWhatsappSaidaDTO montarListaCategorias(Estabelecimento estabelecimento, String whatsappCliente) {

        List<CategoriaProduto> categorias = extrairCategoriasDoEstabelecimento(estabelecimento);

        if (categorias.isEmpty()) {
            return msg.texto(whatsappCliente, "No momento não encontrei categorias disponíveis para este estabelecimento.");
        }

        String cabecalho = "Escolha uma categoria:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = categorias.stream()
            .sorted(Comparator.comparing(c -> msg.safe(c.getNome()), String.CASE_INSENSITIVE_ORDER))
            .map(c -> {
                Integer qm = c.getQuantidadeMultipla() == null ? 1 : c.getQuantidadeMultipla();
                return row(
                    "COMANDO|LISTA_PRODUTOS|" + c.getId() + "|" + qm,
                    msg.safe(c.getNome()),
                    "Clique para ver produtos"
                );
            })
            .collect(Collectors.toList());

        return msg.lista(whatsappCliente, cabecalho, "Categorias", "Categorias", itens);
    }

    private MensagemWhatsappSaidaDTO montarListaProdutosPorCategoriaPaginada(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idCategoria,
        Integer quantidadeMultipla,
        Integer offset
    ) {

        List<Produto> produtos = extrairProdutosPorCategoria(estabelecimento, idCategoria);

        if (produtos.isEmpty()) {
            return msg.texto(whatsappCliente, "Não encontrei produtos para esta categoria.");
        }

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;
        int pageSizeProdutos = 9;

        List<Produto> ordenados = produtos.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(p -> msg.safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        int total = ordenados.size();
        if (safeOffset >= total) safeOffset = 0;

        int endExclusive = Math.min(safeOffset + pageSizeProdutos, total);
        List<Produto> page = ordenados.subList(safeOffset, endExclusive);

        String nomeCategoria = extrairNomeCategoria(estabelecimento, idCategoria);
        String tituloCategoria = (nomeCategoria == null ? ("Categoria #" + idCategoria) : nomeCategoria);

        int paginaAtual = (safeOffset / pageSizeProdutos) + 1;
        int paginasTotal = (int) Math.ceil(total / (double) pageSizeProdutos);

        String cabecalho =
            "Produtos - " + tituloCategoria + ":\n" +
                "Página " + paginaAtual + " de " + paginasTotal;

        List<MensagemInterativaItemListaWhatsappDTO> itens = page.stream()
            .map(p -> {
                String nome = msg.safe(p.getNome());
                String preco = msg.formatarMoeda(p.getPreco());

                String title = msg.trunc(nome + " • " + preco, 24);

                String desc = msg.safe(p.getDescricao());
                String description = StringUtils.hasText(desc) ? msg.trunc(desc, 72) : msg.trunc("Unit: " + preco, 72);

                return MensagemInterativaItemListaWhatsappDTO.builder()
                    .id("COMANDO|LISTAR_QUANTIDADES|" + idCategoria + "|" + quantidadeMultipla + "|" + p.getId())
                    .title(title)
                    .description(description)
                    .build();
            })
            .collect(Collectors.toList());

        if (endExclusive < total) {
            int nextOffset = endExclusive;
            itens.add(row(
                "COMANDO|LISTA_PRODUTOS_PAG|" + idCategoria + "|" + quantidadeMultipla + "|" + nextOffset,
                "➡️ Mais produtos",
                "Ver próxima página"
            ));
        }

        return msg.lista(
            whatsappCliente,
            msg.truncWord(cabecalho, 1024),
            msg.truncWord("Produtos", 20),
            msg.truncWord("Produtos", 24),
            itens
        );
    }

    private MensagemWhatsappSaidaDTO montarListaQuantidades(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idCategoria,
        Integer quantidadeMultipla,
        Long idProduto
    ) {

        Produto produto = extrairProduto(estabelecimento, idProduto);

        if (produto == null) {
            return msg.texto(whatsappCliente, "Produto não encontrado.");
        }

        int qm = (quantidadeMultipla == null || quantidadeMultipla < 1) ? 1 : quantidadeMultipla;

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (int i = 1; i <= 9; i++) {
            int quantidade = qm * i;
            BigDecimal preco = calcularPrecoPorQuantidade(produto, quantidade);

            itens.add(row(
                "COMANDO|ADICIONAR_PRODUTO|" + idProduto + "|" + quantidade,
                quantidade + " unidades",
                "Valor total: " + msg.formatarMoeda(preco)
            ));
        }

        itens.add(row(
            "COMANDO|SOLICITAR_QUANTIDADE|" + idProduto,
            "Outra quantidade",
            "Informar manualmente"
        ));

        String cabecalho =
            "Quantidades - " + msg.safe(produto.getNome()) + "\n" +
                "Escolha uma opção:";

        return msg.lista(whatsappCliente, cabecalho, "Quantidades", "Quantidades", itens);
    }

    private MensagemWhatsappSaidaDTO montarVisualizacaoCarrinho(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        Map<Long, Integer> qtdPorProduto = montarCarrinhoAtual(idSessao);

        if (qtdPorProduto.isEmpty()) {

            String corpo = "Seu carrinho está vazio 🛒\n\nQuer incluir algum item?";

            return msg.botoes(
                whatsappCliente,
                msg.trunc(corpo, 1024),
                List.of(
                    btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Incluir outro item"),
                    btn("COMANDO|FAZER_PEDIDO", "Fazer um pedido")
                )
            );
        }

        BigDecimal total = BigDecimal.ZERO;

        StringBuilder sb = new StringBuilder();
        sb.append("*Seu carrinho* 🛒\n\n");

        for (var entry : qtdPorProduto.entrySet()) {

            Long idProduto = entry.getKey();
            int qtd = entry.getValue();

            Produto p = extrairProduto(estabelecimento, idProduto);

            if (p == null) {
                sb.append("- Produto #").append(idProduto).append(" x").append(qtd).append("\n");
                continue;
            }

            BigDecimal subtotal = calcularPrecoPorQuantidade(p, qtd);
            total = total.add(subtotal);

            String nome = msg.safe(p.getNome());
            String desc = msg.safe(p.getDescricao());

            sb.append("- ").append(nome);

            if (StringUtils.hasText(desc)) {
                sb.append(" (").append(msg.trunc(desc, 24)).append(")");
            }

            sb.append(" x").append(qtd)
                .append(" = ").append(msg.formatarMoeda(subtotal))
                .append("\n");
        }

        sb.append("\n*Total:* ").append(msg.formatarMoeda(total)).append("\n");

        return msg.botoes(
            whatsappCliente,
            msg.trunc(sb.toString(), 1024),
            List.of(
                btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Incluir outro item"),
                btn("COMANDO|LIMPAR_CARRINHO", "🗑️ Limpar carrinho"),
                btn("COMANDO|INFORMAR_ENDERECO", "🏍️ Ir para entrega")
            )
        );
    }

    private Map<Long, Integer> montarCarrinhoAtual(Long idSessao) {

        Map<Long, Integer> qtdPorProduto = new LinkedHashMap<>();

        for (var m : mensagemService.listarEntradas(idSessao)) {

            String txt = (m == null) ? null : m.getConteudoTexto();
            if (!StringUtils.hasText(txt)) continue;

            if (txt.startsWith("COMANDO|LIMPAR_CARRINHO")) {
                qtdPorProduto.clear();
                continue;
            }

            if (!txt.startsWith("COMANDO|ADICIONAR_PRODUTO|")) continue;

            ComandoWhatsapp c = ComandoWhatsapp.parse(txt);

            Long idProduto = parseLongObrigatorio(c.getParte(2), "idProduto");
            Integer quantidade = parseIntObrigatorio(c.getParte(3), "quantidade");

            if (quantidade == null || quantidade < 1) continue;

            qtdPorProduto.merge(idProduto, quantidade, Integer::sum);
        }

        return qtdPorProduto;
    }

    private MensagemWhatsappSaidaDTO montarCarrinhoLimpo(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        String corpo =
            "Carrinho limpo ✅🛒\n\n" +
                "O que você deseja fazer agora?";

        return msg.botoes(
            whatsappCliente,
            msg.trunc(corpo, 1024),
            List.of(
                btn("COMANDO|INCLUIR_OUTRO_ITEM", "Incluir outro item"),
                btn("COMANDO|VISUALIZAR_CARRINHO", "Visualizar carrinho"),
                btn("COMANDO|INFORMAR_ENDERECO", "Concluir pedido")
            )
        );
    }

    // ======================================================================
    // CONFIRMAÇÕES / TEXTOS
    // ======================================================================

    private MensagemWhatsappSaidaDTO montarConfirmacaoPedidoEnviado(
            String whatsappCliente,
            Long idPedido,
            SessaoAtendimentoWhatsapp sessao,
            String resumoItens,
            BigDecimal total
    ) {

        String endereco = sessao == null ? "" : msg.safe(sessao.getEnderecoEntrega());
        String pagamento = formatarPagamentoParaTexto(sessao);

        String itens = msg.safe(resumoItens);
        itens = StringUtils.hasText(itens) ? itens : "(sem itens)";

        String corpo =
            "✅ *Pedido enviado com sucesso!*\n\n" +
                "📌 *Pedido:* #" + idPedido + "\n" +
                "⏳ *Status:* aguardando confirmação do estabelecimento\n\n" +
                "🛒 *Itens:*\n" +
                msg.trunc(itens, 1400) + "\n\n" +
                "💰 *Total:* " + msg.formatarMoeda(total) + "\n\n" +
                "📍 *Entrega:*\n" +
                msg.trunc(endereco, 700) + "\n\n" +
                "💳 *Pagamento:* " + pagamento + "\n\n" +
                "Você vai receber atualizações quando:\n" +
                "- o pedido for *aceito*\n" +
                "- o pedido *sair para entrega*\n\n" +
                "Se precisar, envie *MENU* para voltar ao início.";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    private MensagemWhatsappSaidaDTO montarSugestaoEnderecoAnterior(String whatsappCliente, String enderecoAnterior) {

        String corpo =
            "Encontrei um endereço usado no seu último pedido:\n\n" +
                "*" + msg.trunc(enderecoAnterior, 900) + "*\n\n" +
                "Deseja usar esse mesmo?";

        return msg.botoes(
            whatsappCliente,
            msg.trunc(corpo, 1024),
            List.of(
                btn("COMANDO|FAZER_PEDIDO_COM_ENDERECO_ANTERIOR", "✅ Usar esse mesmo"),
                btn("COMANDO|INFORMAR_OUTRO_ENDERECO", "✏️ Alterar endereço")
            )
        );
    }

    private MensagemWhatsappSaidaDTO montarSolicitacaoEnderecoEntrega(String whatsappCliente) {

        String corpo =
            "Perfeito! ✅ Agora me informe o *endereço de entrega*.\n\n" +
                "Inclua também *observações úteis pro entregador*, como:\n" +
                "- ponto de referência\n" +
                "- bloco/apto\n" +
                "- interfone\n" +
                "- portaria / instruções de acesso\n\n" +
                "Exemplo:\n" +
                "Rua X, 123 - Apto 45, Bairro Y. Obs: interfone 45, portaria 24h.";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    private String montarResumoItensDoCarrinho(Estabelecimento estabelecimento, Map<Long, Integer> carrinho) {

        if (carrinho == null || carrinho.isEmpty()) return "(sem itens)";

        StringBuilder sb = new StringBuilder();

        for (var e : carrinho.entrySet()) {

            Produto p = extrairProduto(estabelecimento, e.getKey());

            String nome = (p == null ? ("Produto #" + e.getKey()) : msg.safe(p.getNome()));
            int qtd = e.getValue() == null ? 0 : e.getValue();

            BigDecimal subtotal = (p == null ? BigDecimal.ZERO : calcularPrecoPorQuantidade(p, qtd));

            sb.append("- ").append(nome)
                .append(" x").append(qtd)
                .append(" = ").append(msg.formatarMoeda(subtotal))
                .append("\n");
        }

        return sb.toString().trim();
    }

    
    // ======================================================================
    // REVISÃO DO ÚLTIMO PEDIDO (cliente)
    // ======================================================================

    private RoteamentoResultado tratarUltimoPedidoParaRevisao(
        Estabelecimento estabelecimento,
        String whatsappCliente
    ) {

        // ✅ você já tem PedidoService; aqui usamos um método que PRECISA existir no seu PedidoService:
        // buscarUltimoPedidoDoCliente(estabelecimentoId, whatsappCliente)
        PedidoResponseDTO ultimo = pedidoService.buscarUltimoPedidoDoCliente(estabelecimento.getId(), whatsappCliente);

        if (ultimo == null || ultimo.getId() == null) {
            MensagemWhatsappSaidaDTO saida = msg.botoes(
                whatsappCliente,
                msg.trunc("Não encontrei pedidos recentes para revisar. 🛒", 1024),
                List.of(
                    btn("COMANDO|FAZER_PEDIDO", "🛍️ Fazer um pedido"),
                    btn("COMANDO|MENU", "⬅️ Menu")
                )
            );
            return new RoteamentoResultado("revisao_sem_pedidos", saida);
        }

        return tratarTelaRevisaoPedido(estabelecimento, whatsappCliente, ultimo.getId());
    }

    private RoteamentoResultado tratarTelaRevisaoPedido(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido
    ) {

        PedidoResponseDTO pedido = pedidoService.buscarResumoPedidoParaCliente(estabelecimento.getId(), idPedido, whatsappCliente);

        if (pedido == null) {
            return new RoteamentoResultado(
                "revisao_pedido_nao_encontrado",
                msg.texto(whatsappCliente, "Não encontrei esse pedido para revisão.")
            );
        }

        MensagemWhatsappSaidaDTO tela = montarTelaRevisaoPedido(estabelecimento, whatsappCliente, pedido);

        return new RoteamentoResultado("revisao_pedido_tela", tela);
    }

    private MensagemWhatsappSaidaDTO montarTelaRevisaoPedido(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        PedidoResponseDTO pedido
    ) {

        StatusPedido st = pedido == null ? null : pedido.getStatus();

        String resumoItens = msg.safe(pedido == null ? null : pedido.getResumoItens());
        if (!StringUtils.hasText(resumoItens)) {
            resumoItens = "(sem itens)";
        }

        String totalFmt = msg.formatarMoeda(
            (pedido == null || pedido.getTotal() == null) ? BigDecimal.ZERO : pedido.getTotal()
        );

        String statusLabel;

        if (StringUtils.hasText(msg.safe(pedido == null ? null : pedido.getStatusLabel()))) {
            statusLabel = msg.safe(pedido.getStatusLabel());
        } else {
            statusLabel = formatarStatusFallback(st);
        }

        String corpo =
            "🔎 *Revisão do pedido*\n\n" +
                "📌 *Pedido:* #" + (pedido == null ? "" : pedido.getId()) + "\n" +
                "⏳ *Status:* " + statusLabel + "\n\n" +
                "🛒 *Itens:*\n" +
                msg.trunc(resumoItens, 1400) + "\n\n" +
                "💰 *Total:* " + totalFmt + "\n\n" +
                "O que deseja fazer?";

        List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = new ArrayList<>();

        if (st == StatusPedido.CRIADO) {
            botoes.add(btn("COMANDO|REVISAO_ADICIONAR_ITENS|" + pedido.getId(), "➕ Adicionar itens"));
            botoes.add(btn("COMANDO|REVISAO_CANCELAR_PEDIDO|" + pedido.getId(), "🗑️ Cancelar"));
        } else if (st == StatusPedido.EM_PREPARO) {
            botoes.add(btn("COMANDO|REVISAO_CANCELAR_PEDIDO|" + pedido.getId(), "🗑️ Cancelar"));
        } else if (st == StatusPedido.PRONTO) {
            botoes.add(btn("COMANDO|REVISAO_CONFIRMAR_ENTREGA|" + pedido.getId(), "✅ Confirmar entrega"));
        }

        botoes.add(btn("COMANDO|MENU", "⬅️ Menu"));

        return msg.botoes(whatsappCliente, msg.trunc(corpo, 1024), botoes);
    }

    private String formatarStatusFallback(StatusPedido st) {
        if (st == null) return "desconhecido";
        switch (st) {
            case CRIADO:
                return "aguardando confirmação do estabelecimento";
            case EM_PREPARO:
                return "em preparo";
            case PRONTO:
                return "saiu para entrega";
            case ENTREGUE:
                return "entregue";
            case CANCELADO:
                return "cancelado";
            default:
                return st.name();
        }
    }

    

    // ======================================================================
    // REVISÃO: adicionar itens (navegação por listas com idPedido no comando)
    // ======================================================================

    private MensagemWhatsappSaidaDTO montarListaCategoriasRevisao(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido
    ) {

        List<CategoriaProduto> categorias = extrairCategoriasDoEstabelecimento(estabelecimento);

        if (categorias.isEmpty()) {
            return msg.texto(whatsappCliente, "No momento não encontrei categorias disponíveis para este estabelecimento.");
        }

        String cabecalho =
            "➕ Adicionar itens\n\n" +
                "Pedido #" + idPedido + "\n" +
                "Escolha uma categoria:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = categorias.stream()
            .sorted(Comparator.comparing(c -> msg.safe(c.getNome()), String.CASE_INSENSITIVE_ORDER))
            .map(c -> {
                Integer qm = c.getQuantidadeMultipla() == null ? 1 : c.getQuantidadeMultipla();
                return row(
                    "COMANDO|REVISAO_LISTA_PRODUTOS|" + idPedido + "|" + c.getId() + "|" + qm,
                    msg.safe(c.getNome()),
                    "Clique para ver produtos"
                );
            })
            .collect(Collectors.toList());

        return msg.lista(whatsappCliente, cabecalho, "Categorias", "Categorias", itens);
    }

    private MensagemWhatsappSaidaDTO montarListaProdutosPorCategoriaPaginadaRevisao(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido,
        Long idCategoria,
        Integer quantidadeMultipla,
        Integer offset
    ) {

        List<Produto> produtos = extrairProdutosPorCategoria(estabelecimento, idCategoria);

        if (produtos.isEmpty()) {
            return msg.texto(whatsappCliente, "Não encontrei produtos para esta categoria.");
        }

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;
        int pageSizeProdutos = 9;

        List<Produto> ordenados = produtos.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(p -> msg.safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        int total = ordenados.size();
        if (safeOffset >= total) safeOffset = 0;

        int endExclusive = Math.min(safeOffset + pageSizeProdutos, total);
        List<Produto> page = ordenados.subList(safeOffset, endExclusive);

        String nomeCategoria = extrairNomeCategoria(estabelecimento, idCategoria);
        String tituloCategoria = (nomeCategoria == null ? ("Categoria #" + idCategoria) : nomeCategoria);

        int paginaAtual = (safeOffset / pageSizeProdutos) + 1;
        int paginasTotal = (int) Math.ceil(total / (double) pageSizeProdutos);

        String cabecalho =
            "➕ Adicionar itens (Pedido #" + idPedido + ")\n" +
                "Produtos - " + tituloCategoria + ":\n" +
                "Página " + paginaAtual + " de " + paginasTotal;

        List<MensagemInterativaItemListaWhatsappDTO> itens = page.stream()
            .map(p -> {
                String nome = msg.safe(p.getNome());
                String preco = msg.formatarMoeda(p.getPreco());

                String title = msg.trunc(nome + " • " + preco, 24);

                String desc = msg.safe(p.getDescricao());
                String description = StringUtils.hasText(desc)
                    ? msg.trunc(desc, 72)
                    : msg.trunc("Unit: " + preco, 72);

                return MensagemInterativaItemListaWhatsappDTO.builder()
                    .id("COMANDO|REVISAO_LISTAR_QUANTIDADES|" + idPedido + "|" + idCategoria + "|" + quantidadeMultipla + "|" + p.getId())
                    .title(title)
                    .description(description)
                    .build();
            })
            .collect(Collectors.toList());

        if (endExclusive < total) {
            int nextOffset = endExclusive;
            itens.add(row(
                "COMANDO|REVISAO_LISTA_PRODUTOS_PAG|" + idPedido + "|" + idCategoria + "|" + quantidadeMultipla + "|" + nextOffset,
                "➡️ Mais produtos",
                "Ver próxima página"
            ));
        }

        return msg.lista(
            whatsappCliente,
            msg.truncWord(cabecalho, 1024),
            msg.truncWord("Produtos", 20),
            msg.truncWord("Produtos", 24),
            itens
        );
    }

    private MensagemWhatsappSaidaDTO montarListaQuantidadesRevisao(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idPedido,
        Long idCategoria,
        Integer quantidadeMultipla,
        Long idProduto
    ) {

        Produto produto = extrairProduto(estabelecimento, idProduto);

        if (produto == null) {
            return msg.texto(whatsappCliente, "Produto não encontrado.");
        }

        int qm = (quantidadeMultipla == null || quantidadeMultipla < 1) ? 1 : quantidadeMultipla;

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (int i = 1; i <= 9; i++) {
            int quantidade = qm * i;
            BigDecimal preco = calcularPrecoPorQuantidade(produto, quantidade);

            itens.add(row(
                "COMANDO|REVISAO_ADICIONAR_PRODUTO|" + idPedido + "|" + idProduto + "|" + quantidade,
                quantidade + " unidades",
                "Valor total: " + msg.formatarMoeda(preco)
            ));
        }

        String cabecalho =
            "➕ Adicionar itens (Pedido #" + idPedido + ")\n\n" +
                "Quantidades - " + msg.safe(produto.getNome()) + "\n" +
                "Escolha uma opção:";

        return msg.lista(whatsappCliente, cabecalho, "Quantidades", "Quantidades", itens);
    }

    // ======================================================================
    // REVISÃO: ações que alteram o pedido (e notificam admins)
    // ======================================================================

    private RoteamentoResultado tratarRevisaoAdicionarProduto(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idPedido,
	    Long idProduto,
	    Integer quantidade
	) {

	    if (quantidade == null || quantidade < 1) {
	        return new RoteamentoResultado(
	            "revisao_quantidade_invalida",
	            msg.texto(whatsappCliente, "Quantidade inválida.")
	        );
	    }

	    // Atualiza pedido (retorna DTO para cliente)
	    PedidoResponseDTO atualizado = pedidoService.adicionarItemNoPedidoDoCliente(
	        estabelecimento.getId(),
	        idPedido,
	        whatsappCliente,
	        idProduto,
	        quantidade
	    );

	    // ===============================
	    // Mensagem para o cliente
	    // ===============================

	    String textoCliente =
	        "✅ Item adicionado ao pedido!\n\n" +
	        "Pedido #" + idPedido + "\n" +
	        "Total atualizado: " +
	        msg.formatarMoeda(atualizado.getTotal() == null
	            ? BigDecimal.ZERO
	            : atualizado.getTotal()
	        ) +
	        "\n\nDeseja fazer mais alguma alteração?";

	    MensagemWhatsappSaidaDTO saidaCliente = msg.botoes(
	        whatsappCliente,
	        msg.trunc(textoCliente, 1024),
	        List.of(
	            btn("COMANDO|PEDIDO_REVISAR|" + idPedido, "🔎 Voltar à revisão"),
	            btn("COMANDO|REVISAO_ADICIONAR_ITENS|" + idPedido, "➕ Adicionar mais"),
	            btn("COMANDO|MENU", "⬅️ Menu")
	        )
	    );

	    // ===============================
	    // Busca entidade completa para admin
	    // ===============================

	    Pedido pedidoEntidade = pedidoService.buscarEntidadeComItens(
	        estabelecimento.getId(),
	        idPedido
	    );

	    // ===============================
	    // Notificação para admins
	    // ===============================

	    List<MensagemWhatsappSaidaDTO> extras =
	        administradorWhatsappService.montarNotificacoesMudancaPedidoParaAdmins(
	            estabelecimento,
	            idPedido,
	            whatsappCliente,
	            "➕ Cliente adicionou itens",
	            pedidoEntidade.getStatus(),
	            administradorWhatsappService.montarResumoItensDoPedido(pedidoEntidade),
	            pedidoEntidade.getTotal()
	        );

	    return new RoteamentoResultado(
	        "revisao_item_adicionado",
	        saidaCliente,
	        extras
	    );
	}

    private RoteamentoResultado tratarRevisaoCancelarPedido(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idPedido
	) {

	    pedidoService.cancelarPedidoDoCliente(
	        estabelecimento.getId(),
	        idPedido,
	        whatsappCliente
	    );

	    MensagemWhatsappSaidaDTO saidaCliente = msg.texto(
	        whatsappCliente,
	        msg.trunc(
	            "✅ Pedido cancelado.\n\n" +
	                "Pedido #" + idPedido + "\n" +
	                "Se quiser, envie *MENU* para voltar ao início.",
	            4096
	        )
	    );

	    // Busca entidade completa
	    Pedido pedidoEntidade = pedidoService.buscarEntidadeComItens(
	        estabelecimento.getId(),
	        idPedido
	    );

	    List<MensagemWhatsappSaidaDTO> extras =
	        administradorWhatsappService.montarNotificacoesMudancaPedidoParaAdmins(
	            estabelecimento,
	            idPedido,
	            whatsappCliente,
	            "🗑️ Cliente cancelou o pedido",
	            pedidoEntidade.getStatus(),
	            administradorWhatsappService.montarResumoItensDoPedido(pedidoEntidade),
	            pedidoEntidade.getTotal()
	        );

	    return new RoteamentoResultado(
	        "revisao_pedido_cancelado",
	        saidaCliente,
	        extras
	    );
	}
    
    
    private RoteamentoResultado tratarRevisaoConfirmarEntrega(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idPedido
	) {

	    pedidoService.confirmarEntregaDoCliente(
	        estabelecimento.getId(),
	        idPedido,
	        whatsappCliente
	    );

	    MensagemWhatsappSaidaDTO saidaCliente = msg.texto(
	        whatsappCliente,
	        msg.trunc(
	            "✅ Entrega confirmada! Obrigado. 🙂\n\n" +
	                "Pedido #" + idPedido + "\n" +
	                "Se precisar, envie *MENU*.",
	            4096
	        )
	    );

	    // Busca entidade completa
	    Pedido pedidoEntidade = pedidoService.buscarEntidadeComItens(
	        estabelecimento.getId(),
	        idPedido
	    );

	    List<MensagemWhatsappSaidaDTO> extras =
	        administradorWhatsappService.montarNotificacoesMudancaPedidoParaAdmins(
	            estabelecimento,
	            idPedido,
	            whatsappCliente,
	            "✅ Cliente confirmou a entrega",
	            pedidoEntidade.getStatus(),
	            administradorWhatsappService.montarResumoItensDoPedido(pedidoEntidade),
	            pedidoEntidade.getTotal()
	        );

	    return new RoteamentoResultado(
	        "revisao_entrega_confirmada",
	        saidaCliente,
	        extras
	    );
	}

    
    
    // ======================================================================
    // EXTRAÇÃO DE DADOS
    // ======================================================================

    private List<CategoriaProduto> extrairCategoriasDoEstabelecimento(Estabelecimento e) {

        if (e == null || e.getProdutos() == null) return List.of();

        Map<Long, CategoriaProduto> mapa = new LinkedHashMap<>();

        for (Produto p : e.getProdutos()) {
            if (p == null) continue;

            CategoriaProduto c = p.getCategoria();
            if (c == null || c.getId() == null) continue;

            mapa.putIfAbsent(c.getId(), c);
        }

        return new ArrayList<>(mapa.values());
    }

    private List<Produto> extrairProdutosPorCategoria(Estabelecimento e, Long idCategoria) {

        if (e == null || e.getProdutos() == null) return List.of();

        return e.getProdutos().stream()
            .filter(Objects::nonNull)
            .filter(p -> p.getCategoria() != null && Objects.equals(p.getCategoria().getId(), idCategoria))
            .collect(Collectors.toList());
    }

    private Produto extrairProduto(Estabelecimento e, Long idProduto) {

        if (e == null || e.getProdutos() == null) return null;

        return e.getProdutos().stream()
            .filter(Objects::nonNull)
            .filter(p -> Objects.equals(p.getId(), idProduto))
            .findFirst()
            .orElse(null);
    }

    private String extrairNomeCategoria(Estabelecimento e, Long idCategoria) {

        if (e == null || e.getProdutos() == null) return null;

        return e.getProdutos().stream()
            .filter(Objects::nonNull)
            .map(Produto::getCategoria)
            .filter(Objects::nonNull)
            .filter(c -> Objects.equals(c.getId(), idCategoria))
            .map(CategoriaProduto::getNome)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null);
    }

    // ======================================================================
    // REGRAS DE PREÇO
    // ======================================================================

    private BigDecimal calcularPrecoPorQuantidade(Produto produto, int quantidade) {
        BigDecimal precoUnit = produto == null || produto.getPreco() == null ? BigDecimal.ZERO : produto.getPreco();
        return precoUnit.multiply(BigDecimal.valueOf(quantidade));
    }

    // ======================================================================
    // HELPERS / PARSERS
    // ======================================================================

    private String safeTextoEntrada(MensagemWhatsappEntradaDTO req) {
        String v = req.getTextoOuComando();
        if (!StringUtils.hasText(v)) return "(vazio)";
        return v.length() <= 5000 ? v : v.substring(0, 5000);
    }

    private Long parseLongObrigatorio(String v, String nomeCampo) {
        if (!StringUtils.hasText(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nomeCampo + " é obrigatório");
        }
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nomeCampo + " inválido");
        }
    }

    private Integer parseIntObrigatorio(String v, String nomeCampo) {
        if (!StringUtils.hasText(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nomeCampo + " é obrigatório");
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nomeCampo + " inválido");
        }
    }

    private Integer parseIntDefaultZero(String raw) {
        if (!StringUtils.hasText(raw)) return 0;
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(v, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private Integer parseIntDefaultZeroAllowNegative(String raw) {
        if (!StringUtils.hasText(raw)) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private StatusPedido parseStatusPedidoObrigatorio(String raw) {

        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "statusPedido é obrigatório");
        }

        String v = raw.trim().toUpperCase(Locale.ROOT);

        try {
            return StatusPedido.valueOf(v);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "statusPedido inválido");
        }
    }

    private FormaPagamentoPedido parseFormaPagamento(String tipo) {

        if (!StringUtils.hasText(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formaPagamento é obrigatória");
        }

        String t = tipo.trim().toUpperCase(Locale.ROOT);

        if ("DINHEIRO".equals(t)) return FormaPagamentoPedido.DINHEIRO;
        if ("CREDITO".equals(t) || "CRÉDITO".equals(t)) return FormaPagamentoPedido.CREDITO;
        if ("DEBITO_PIX".equals(t) || "DEBITO".equals(t) || "DÉBITO".equals(t) || "PIX".equals(t)) return FormaPagamentoPedido.DEBITO_PIX;

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formaPagamento inválida");
    }

    private BigDecimal parseValorMonetario(String raw) {

        if (!StringUtils.hasText(raw)) return null;

        String s = raw.trim()
            .replace("R$", "")
            .replace("r$", "")
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".");

        s = s.replaceAll("[^0-9.]", "");

        if (!StringUtils.hasText(s)) return null;

        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private MensagemInterativaBotaoReplyWhatsappDTO btn(String id, String title) {
        return MensagemInterativaBotaoReplyWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 20))
            .build();
    }

    private MensagemInterativaItemListaWhatsappDTO row(String id, String title, String description) {
        return MensagemInterativaItemListaWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 24))
            .description(msg.trunc(msg.safe(description), 72))
            .build();
    }
}