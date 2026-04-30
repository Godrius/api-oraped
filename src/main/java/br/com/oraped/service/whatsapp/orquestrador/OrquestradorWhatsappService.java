package br.com.oraped.service.whatsapp.orquestrador;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.marketplace.Marketplace;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.OrquestradorContexto;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.dto.marktplace.CategoriaMarketplaceDisponivelDTO;
import br.com.oraped.dto.whatsapp.entrada.MensagemWhatsappEntradaDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.dto.whatsapp.saida.RespostaWhatsappDTO;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.geolocalizacao.GeolocalizacaoOrigemMarketplaceService;
import br.com.oraped.service.marketplace.MarketplaceCategoriaService;
import br.com.oraped.service.marketplace.MarketplaceService;
import br.com.oraped.service.whatsapp.MensagemAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.orquestrador.marketplace.OrquestradorMarketplaceMensagemService;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappMarketplaceService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Orquestrar o processamento das mensagens recebidas no WhatsApp, decidindo
 * se o fluxo seguirá para estabelecimento ou marketplace.
 *
 * Aplicação:
 * Utilizado como ponto central de entrada das mensagens do canal WhatsApp,
 * controlando captura de sessão, registro de histórico, validações iniciais,
 * fluxo de localização do marketplace e roteamento dos comandos conversacionais.
 *
 * Utilização:
 * Deve ser acionado pela camada de entrada/webhook para processar cada mensagem
 * recebida, retornando a resposta padronizada ao N8N com a mensagem principal
 * e eventuais mensagens extras.
 */
@Service
@RequiredArgsConstructor
public class OrquestradorWhatsappService {

    private final EstabelecimentoService estabelecimentoService;

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final SessaoWhatsappMarketplaceService sessaoMarketplaceService;
    
    private final MensagemAtendimentoWhatsappService mensagemService;

    private final WhatsappMensagemFactory msg;
    private final ObjectMapper objectMapper;

    private final OrquestradorContextoService contextoService;
    private final OrquestradorRegistroMensagemService registroMensagemService;
    private final OrquestradorTextoLivreService textoLivreService;
    private final OrquestradorRoteamentoService roteamentoService;
    private final OrquestradorParseService parseService;
    private final OrquestradorMensagemHelperService mensagemHelper;

    private final MarketplaceService marketplaceService;
    private final MarketplaceCategoriaService marketplaceCategoriaService;
    private final OrquestradorMarketplaceMensagemService marketplaceMensagemService;
    private final GeolocalizacaoOrigemMarketplaceService geolocalizacaoOrigemMarketplaceService;

    // ====================== PROCESSADOR DE MENSAGENS ======================
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

            Estabelecimento estabelecimento = null;
            Marketplace marketplace = null;

            try {
                estabelecimento = estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() != 404) {
                    throw ex;
                }
            }

            if (estabelecimento == null) {
                try {
                    marketplace = marketplaceService.buscarPorWhatsapp(whatsappReceptor);
                } catch (ResponseStatusException ex) {
                    if (ex.getStatusCode().value() != 404) {
                        throw ex;
                    }
                }
            }

            if (estabelecimento == null && marketplace == null) {

                MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                    whatsappCliente,
                    "Ops! 😕\n" +
                        "Não encontrei um atendimento configurado para esse número.\n\n" +
                        "Confira se você chamou o WhatsApp correto e tente novamente."
                );

                return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
            }

            SessaoAtendimentoWhatsapp sessao = sessaoService.obterOuCriar(
                whatsappCliente,
                whatsappReceptor,
                estabelecimento != null ? estabelecimento.getId() : null,
                marketplace != null ? marketplace.getId() : null
            );

            // Mantém histórico completo da conversa antes de qualquer desvio de fluxo.
            registroMensagemService.registrarEntrada(
                sessao.getId(),
                parseService.safeTextoEntrada(req),
                req.getPayloadOriginal()
            );

            // =========================================================
            // PARSE DO COMANDO (movido para antes da barreira do marketplace)
            // Motivo: precisamos interceptar MENU antes da validação de localização
            // =========================================================
            String textoOuComando = req.getTextoOuComando();
            ComandoWhatsapp comando = ComandoWhatsapp.parse(textoOuComando);

            // -------------------------
            // DEBUG ENTRADA (DTO)
            // -------------------------
            System.out.println("[WA] ENTRADA safeTextoEntrada=" + parseService.safeTextoEntrada(req));
            System.out.println("[WA] ENTRADA getTextoOuComando=" + req.getTextoOuComando());
            System.out.println("[WA] DTO texto=" + req.getTexto());
            System.out.println("[WA] DTO comando=" + req.getComando());
            System.out.println("[WA] DTO textoOuComando=" + req.getTextoOuComando());
            System.out.println("[WA] DTO isMensagemLocalizacao=" + req.isMensagemLocalizacao());
            System.out.println("[WA] DTO latitudeLocalizacao=" + req.getLatitudeLocalizacao());
            System.out.println("[WA] DTO longitudeLocalizacao=" + req.getLongitudeLocalizacao());
            System.out.println("[WA] DTO nomeLocalizacao=" + req.getNomeLocalizacao());
            System.out.println("[WA] DTO enderecoLocalizacao=" + req.getEnderecoLocalizacao());

            // -------------------------
            // DEBUG PAYLOAD ORIGINAL
            // -------------------------
            Object payloadOriginalObj = req.getPayloadOriginal();
            String payloadOriginal = payloadOriginalObj == null ? null : String.valueOf(payloadOriginalObj);

            System.out.println("[WA] PAYLOAD original null? " + (payloadOriginalObj == null));
            System.out.println("[WA] PAYLOAD original str size=" + (payloadOriginal == null ? 0 : payloadOriginal.length()));

            if (payloadOriginal != null) {
                String head = payloadOriginal.substring(0, Math.min(400, payloadOriginal.length())).replace("\n", " ");
                System.out.println("[WA] PAYLOAD head=" + head);
                System.out.println("[WA] PAYLOAD hasInteractive=" + payloadOriginal.contains("\"interactive\""));
                System.out.println("[WA] PAYLOAD hasListReply=" + payloadOriginal.contains("list_reply"));
                System.out.println("[WA] PAYLOAD hasButtonReply=" + payloadOriginal.contains("button_reply"));
                System.out.println("[WA] PAYLOAD hasCOMANDO=" + payloadOriginal.contains("COMANDO|"));
            }

            // =========================================================
            // BLOCO DO MARKETPLACE (com interceptação de MENU)
            // =========================================================
            if (marketplace != null && estabelecimento == null) {

                // =========================================================
                // REGRA NOVA: MENU no marketplace
                // - Se já tem localização -> vai direto para categorias
                // - Se não tem -> segue fluxo normal (boas-vindas)
                // =========================================================
                if (isComandoMenuMarketplace(comando, parseService.safeTextoEntrada(req))) {

                    if (sessaoMarketplaceService.hasLocalizacaoOrigemMarketplace(sessao.getId())) {

                        // Reaproveita a localização existente e volta direto para categorias
                        return responderMenuCategoriasMarketplace(
                            req,
                            whatsappCliente,
                            whatsappReceptor,
                            sessaoService.buscarPorId(sessao.getId()),
                            marketplace,
                            "marketplace_menu_categorias"
                        );
                    }

                    // Sem localização -> mantém comportamento padrão
                    MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                        whatsappCliente,
                        montarTextoBoasVindasMarketplace(marketplace)
                    );

                    registroMensagemService.registrarSaida(
                        sessao.getId(),
                        "marketplace_menu_sem_localizacao",
                        mensagemSaida
                    );

                    return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                }

                // O marketplace exige uma origem válida antes de seguir para o discovery.
                // A origem pode vir por localização compartilhada ou por CEP digitado.
                if (parseService.hasLocalizacaoCompartilhada(req)) {

                	sessaoMarketplaceService.salvarLocalizacaoOrigemMarketplace(
                        sessao.getId(),
                        parseService.getLatitudeLocalizacao(req),
                        parseService.getLongitudeLocalizacao(req)
                    );

                    return responderMenuCategoriasMarketplace(
                        req,
                        whatsappCliente,
                        whatsappReceptor,
                        sessaoService.buscarPorId(sessao.getId()),
                        marketplace,
                        "marketplace_localizacao_recebida"
                    );
                }

                String textoEntrada = parseService.safeTextoEntrada(req);
                String cepInformado = extrairCep(textoEntrada);

                if (StringUtils.hasText(cepInformado)) {
                    return processarCepOrigemMarketplace(
                        req,
                        whatsappCliente,
                        whatsappReceptor,
                        sessao,
                        marketplace,
                        cepInformado
                    );
                }

                // Quando ainda não existe origem salva, qualquer tentativa inicial inválida
                // deve ser interceptada aqui para orientar corretamente o cliente.
                if (!sessaoMarketplaceService.hasLocalizacaoOrigemMarketplace(sessao.getId())) {

                    if (pareceTentativaDeCep(textoEntrada)) {

                        MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                            whatsappCliente,
                            montarTextoCepInvalidoMarketplace(marketplace)
                        );

                        registroMensagemService.registrarSaida(
                            sessao.getId(),
                            "marketplace_cep_invalido",
                            mensagemSaida
                        );

                        return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                    }

                    MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                        whatsappCliente,
                        montarTextoBoasVindasMarketplace(marketplace)
                    );

                    registroMensagemService.registrarSaida(
                        sessao.getId(),
                        "marketplace_boas_vindas_localizacao",
                        mensagemSaida
                    );

                    return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                }
            }

            
            // Quando a conversa começou no marketplace mas o cliente já escolheu
            // um estabelecimento, os próximos passos devem seguir o fluxo padrão
            // da loja selecionada, independentemente do número receptor.
            if (estabelecimento == null && sessao.getIdEstabelecimento() != null) {
                estabelecimento = estabelecimentoService.buscar(sessao.getIdEstabelecimento());
            }
            
            boolean temSaidaAnterior = mensagemService.buscarUltimaSaida(sessao.getId()).isPresent();

            OrquestradorContexto ctx = contextoService.montarContexto(
                estabelecimento,
                sessao,
                whatsappCliente,
                whatsappReceptor,
                phoneNumberId,
                req.getNomeClienteWhatsapp(),
                temSaidaAnterior
            );

            // -------------------------
            // DEBUG PARSE FINAL
            // -------------------------
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

            if (cairTextoLivre
                && marketplace != null
                && isComandoTrocarLocalizacaoMarketplace(parseService.safeTextoEntrada(req))) {

                /*
                 * Texto livre como "voltar", "reiniciar" ou "sair" reinicia o discovery
                 * do marketplace.
                 */
            	sessaoMarketplaceService.trocarLocalizacaoMarketplace(sessao.getId(), marketplace.getId());

                MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                    whatsappCliente,
                    montarTextoBoasVindasMarketplace(marketplace)
                );

                registroMensagemService.registrarSaida(
                    sessao.getId(),
                    "marketplace_trocar_localizacao_texto_livre",
                    mensagemSaida
                );

                return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
            }

            if (cairTextoLivre) {

                RoteamentoResultado roteado = textoLivreService.tratarTextoLivre(ctx, req);

                registroMensagemService.registrarSaida(
                    sessao.getId(),
                    roteado.getChave(),
                    roteado.getMensagem()
                );

                return montarResposta(
                    req,
                    whatsappCliente,
                    whatsappReceptor,
                    roteado.getMensagem(),
                    roteado.getExtras()
                );
            }

            // =========================================================
            // 2) COMANDO -> roteia e retorna (com extras)
            // =========================================================
            System.out.println("[WA] FLOW entrandoRoteamento acao=" + comando.getAcao()
                + " raw=" + textoOuComando
            );

            RoteamentoResultado roteado;

            try {
                roteado = roteamentoService.rotearComando(
                    ctx,
                    comando,
                    req.getIdCorrelacao(),
                    req.getIdMensagem()
                );

            } catch (ResponseStatusException ex) {

                System.out.println("[WA] ERRO rotearComando(ResponseStatusException) status="
                    + ex.getStatusCode().value()
                    + " msg=" + ex.getReason()
                );

                if (ex.getStatusCode().value() == 400 && isMensagemEstabelecimentoFechado(ex.getReason())) {

                    String corpo =
                        "⏸️ Sinto te informar, mas o estabelecimento acabou de fechar.\n\n" +
                            "Quer que eu te avise quando reabrir?";

                    MensagemWhatsappSaidaDTO mensagemSaida = msg.botoes(
                        whatsappCliente,
                        msg.trunc(corpo, 1024),
                        List.of(
                            mensagemHelper.btn("COMANDO|CADASTRAR_NOTIFICACAO_ESTABELECIMENTO_ABERTO", "Sim, me avise")
                        )
                    );

                    registroMensagemService.registrarSaida(
                        sessao.getId(),
                        "estabelecimento_fechado",
                        mensagemSaida
                    );

                    return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
                }

                MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui processar sua solicitação.\n\n" +
                        "Tente novamente."
                );

                registroMensagemService.registrarSaida(
                    sessao.getId(),
                    "erro_roteamento_status",
                    mensagemSaida
                );

                return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);

            } catch (Exception e) {

                System.out.println("[WA] ERRO rotearComando: " + e.getClass().getName() + " - " + e.getMessage());
                e.printStackTrace();

                MensagemWhatsappSaidaDTO mensagemSaida = montarMensagemErroRoteamento(
                    whatsappCliente,
                    comando,
                    textoOuComando
                );

                registroMensagemService.registrarSaida(
                    sessao.getId(),
                    montarChaveErroRoteamento(comando),
                    mensagemSaida
                );

                return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
            }

            registroMensagemService.registrarSaida(
                sessao.getId(),
                roteado.getChave(),
                roteado.getMensagem()
            );

            return montarResposta(
                req,
                whatsappCliente,
                whatsappReceptor,
                roteado.getMensagem(),
                roteado.getExtras()
            );

        } catch (ResponseStatusException ex) {

            System.out.println("[WA] ERRO ResponseStatusException status="
                + ex.getStatusCode().value()
                + " msg=" + ex.getReason()
            );

            if (ex.getStatusCode().value() == 404) {

                MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                    whatsappCliente,
                    "Ops! 😕\n" +
                        "Não encontrei um atendimento configurado para esse número.\n\n" +
                        "Confira se você chamou o WhatsApp correto e tente novamente."
                );

                return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
            }

            throw ex;
        }
    }
    
    
    private RespostaWhatsappDTO processarCepOrigemMarketplace(
	    MensagemWhatsappEntradaDTO req,
	    String whatsappCliente,
	    String whatsappReceptor,
	    SessaoAtendimentoWhatsapp sessao,
	    Marketplace marketplace,
	    String cepInformado
	) {

	    try {
	        // Este método deve resolver o CEP em coordenadas válidas para reaproveitar
	        // todo o fluxo já existente de discovery por localização.
	        EnderecoResolvidoDTO enderecoResolvido = geolocalizacaoOrigemMarketplaceService.resolverOrigemClientePorCep(cepInformado);

	        if (enderecoResolvido == null
	            || enderecoResolvido.getLatitude() == null
	            || enderecoResolvido.getLongitude() == null) {

	            MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
	                whatsappCliente,
	                montarTextoCepNaoEncontradoMarketplace(marketplace)
	            );

	            registroMensagemService.registrarSaida(
	                sessao.getId(),
	                "marketplace_cep_nao_encontrado",
	                mensagemSaida
	            );

	            return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
	        }

	        sessaoMarketplaceService.salvarLocalizacaoOrigemMarketplace(
	            sessao.getId(),
	            enderecoResolvido.getLatitude(),
	            enderecoResolvido.getLongitude()
	        );

	        return responderMenuCategoriasMarketplace(
	            req,
	            whatsappCliente,
	            whatsappReceptor,
	            sessaoService.buscarPorId(sessao.getId()),
	            marketplace,
	            "marketplace_cep_recebido"
	        );

	    } catch (ResponseStatusException ex) {

	        System.out.println("[WA] ERRO resolver CEP marketplace status="
	            + ex.getStatusCode().value()
	            + " msg=" + ex.getReason()
	        );

	        MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
	            whatsappCliente,
	            montarTextoCepNaoEncontradoMarketplace(marketplace)
	        );

	        registroMensagemService.registrarSaida(
	            sessao.getId(),
	            "marketplace_cep_nao_encontrado",
	            mensagemSaida
	        );

	        return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
	    }
	}

	private String extrairCep(String texto) {

	    if (!StringUtils.hasText(texto)) {
	        return null;
	    }

	    String somenteDigitos = texto.replaceAll("\\D", "");
	    return somenteDigitos.length() == 8 ? somenteDigitos : null;
	}

	private boolean pareceTentativaDeCep(String texto) {

	    if (!StringUtils.hasText(texto)) {
	        return false;
	    }

	    String textoNormalizado = texto.trim();
	    String somenteDigitos = textoNormalizado.replaceAll("\\D", "");

	    if (textoNormalizado.toLowerCase().contains("cep")) {
	        return true;
	    }

	    // Intercepta entradas numéricas curtas que claramente parecem CEP digitado incompleto/incorreto.
	    return somenteDigitos.length() >= 5 && somenteDigitos.length() <= 7;
	}

	private String montarTextoBoasVindasMarketplace(Marketplace marketplace) {

	    String nomeMarketplace = marketplace != null && StringUtils.hasText(marketplace.getNome())
	        ? marketplace.getNome().trim()
	        : "Oraped";

	    return "📍 Olá! Este é o marketplace Oraped - " + nomeMarketplace + ".\n\n" +
	        "Aqui você encontra produtos da sua região e faz pedidos direto pelo WhatsApp 🛍️\n\n" +
	        "Para começar, preciso identificar sua localização.\n\n" +
	        "Você pode:\n" +
	        "- Compartilhar sua localização atual pelo WhatsApp 📍\n" +
	        "ou\n" +
	        "- Digitar o seu CEP\n\n" +
	        "Assim já te mostro o que está disponível perto de você 😊";
	}

	private String montarTextoCepInvalidoMarketplace(Marketplace marketplace) {

	    String nomeMarketplace = marketplace != null && StringUtils.hasText(marketplace.getNome())
	        ? marketplace.getNome().trim()
	        : "Oraped";

	    return "📍 Este é o marketplace Oraped - " + nomeMarketplace + ".\n\n" +
	        "Não consegui identificar um CEP válido.\n\n" +
	        "Envie um CEP com 8 números, com ou sem traço.\n\n" +
	        "Exemplos:\n" +
	        "- 24020125\n" +
	        "- 24020-125\n\n" +
	        "Se preferir, você também pode compartilhar sua localização atual pelo WhatsApp 📍";
	}

	private String montarTextoCepNaoEncontradoMarketplace(Marketplace marketplace) {

	    String nomeMarketplace = marketplace != null && StringUtils.hasText(marketplace.getNome())
	        ? marketplace.getNome().trim()
	        : "Oraped";

	    return "📍 Este é o marketplace Oraped - " + nomeMarketplace + ".\n\n" +
	        "Não consegui localizar esse CEP no momento.\n\n" +
	        "Confira se o CEP foi digitado corretamente e envie novamente.\n\n" +
	        "Se preferir, você também pode compartilhar sua localização atual pelo WhatsApp 📍";
	}

	private RespostaWhatsappDTO responderMenuCategoriasMarketplace(
	    MensagemWhatsappEntradaDTO req,
	    String whatsappCliente,
	    String whatsappReceptor,
	    SessaoAtendimentoWhatsapp sessao,
	    Marketplace marketplace,
	    String chaveRegistro
	) {

	    EnderecoResolvidoDTO enderecoResolvido = geolocalizacaoOrigemMarketplaceService.resolverOrigemCliente(
	        sessao.getLatitudeOrigemCliente(),
	        sessao.getLongitudeOrigemCliente()
	    );

	    List<CategoriaMarketplaceDisponivelDTO> categorias = marketplaceCategoriaService.listarCategoriasDisponiveis(
	        marketplace,
	        sessao
	    );

	    MensagemWhatsappSaidaDTO mensagemSaida;

	    if ("marketplace_localizacao_recebida".equals(chaveRegistro)) {
	        mensagemSaida = marketplaceMensagemService.montarMenuCategoriasAposReceberLocalizacao(
	            whatsappCliente,
	            marketplace,
	            enderecoResolvido,
	            categorias
	        );
	    } else if ("marketplace_cep_recebido".equals(chaveRegistro)) {
	        mensagemSaida = marketplaceMensagemService.montarMenuCategoriasAposReceberCep(
	            whatsappCliente,
	            marketplace,
	            enderecoResolvido,
	            categorias
	        );
	    } else {
	        // Quando a sessão já possui origem salva, reaproveitamos a localização atual.
	        mensagemSaida = marketplaceMensagemService.montarMenuCategoriasComLocalizacaoExistente(
	            whatsappCliente,
	            marketplace,
	            enderecoResolvido,
	            categorias
	        );
	    }

	    if (categorias == null || categorias.isEmpty()) {
	    	sessaoMarketplaceService.marcarAguardandoCepRefinarMarketplace(sessao.getId());
	    } else {
	    	sessaoMarketplaceService.limparAguardandoCepRefinarMarketplace(sessao.getId());
	    }

	    registroMensagemService.registrarSaida(
	        sessao.getId(),
	        chaveRegistro,
	        mensagemSaida
	    );

	    return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
	}

    private boolean isMensagemEstabelecimentoFechado(String reason) {

        if (!StringUtils.hasText(reason)) {
            return false;
        }

        String reasonNormalizada = reason.trim().toLowerCase();
        return reasonNormalizada.contains("estabelecimento")
            && reasonNormalizada.contains("fechado");
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
            int qtdExtras = extras == null ? 0 : extras.size();
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
            .wamidEntrada(req.getIdMensagem())
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
    
    
    
    private boolean isComandoMenuMarketplace(ComandoWhatsapp comando, String textoEntrada) {

        // Caso venha como comando estruturado (botão/lista)
        if (comando != null && StringUtils.hasText(comando.getAcao())) {
            String acao = comando.getAcao().trim().toUpperCase();

            // MENU é o principal gatilho para voltar às categorias
            if ("MENU".equals(acao)) {
                return true;
            }
        }

        // Caso venha como texto livre digitado pelo usuário
        if (!StringUtils.hasText(textoEntrada)) {
            return false;
        }

        String textoNormalizado = textoEntrada.trim().toLowerCase();

        // Mantém enxuto: só o que realmente faz sentido como "voltar pro menu"
        return "menu".equals(textoNormalizado);
    }
    
    private boolean isComandoTrocarLocalizacaoMarketplace(String texto) {

        if (!StringUtils.hasText(texto)) {
            return false;
        }

        String textoNormalizado = texto.trim().toLowerCase();

        return "voltar".equals(textoNormalizado)
            || "reiniciar".equals(textoNormalizado)
            || "inicio".equals(textoNormalizado)
            || "início".equals(textoNormalizado)
            || "sair".equals(textoNormalizado)
            || "trocar localização".equals(textoNormalizado)
            || "trocar localizacao".equals(textoNormalizado)
            || "mudar localização".equals(textoNormalizado)
            || "mudar localizacao".equals(textoNormalizado);
    }
    
    
    
    private MensagemWhatsappSaidaDTO montarMensagemErroRoteamento(
	    String whatsappCliente,
	    ComandoWhatsapp comando,
	    String textoOuComando
	) {

	    if (isErroFinalizacaoPedido(comando)) {
	        return msg.botoes(
	            whatsappCliente,
	            msg.trunc(
	                "Poxa, tive uma instabilidade ao finalizar seu pedido 😕\n\n" +
	                    "Ja notifiquei minha equipe técnica e eles já estão tratando esse caso.\n\n" +
	                    "Você pode aguardar uns instantes e tentar finalizar novamente.",
	                1024
	            ),
	            List.of(
	                mensagemHelper.btn(textoOuComando, "Tentar novamente")
	            )
	        );
	    }

	    return msg.texto(
	        whatsappCliente,
	        "Poxa, tive uma instabilidade por aqui 😕\n\n" +
	            "Tente novamente em alguns instantes."
	    );
	}

	private String montarChaveErroRoteamento(ComandoWhatsapp comando) {

	    if (isErroFinalizacaoPedido(comando)) {
	        return "erro_finalizacao_pedido";
	    }

	    return "erro_roteamento";
	}

	private boolean isErroFinalizacaoPedido(ComandoWhatsapp comando) {

	    if (comando == null || !StringUtils.hasText(comando.getAcao())) {
	        return false;
	    }

	    String acao = comando.getAcao().trim().toUpperCase();

	    // ENVIAR_PEDIDO é o comando real do botão "Confirmar e enviar".
	    return "ENVIAR_PEDIDO".equals(acao);
	}
}