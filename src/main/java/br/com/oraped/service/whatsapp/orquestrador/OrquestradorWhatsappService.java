// src/main/java/br/com/oraped/service/whatsapp/orquestrador/OrquestradorWhatsappService.java
package br.com.oraped.service.whatsapp.orquestrador;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.OrquestradorContexto;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.whatsapp.entrada.MensagemWhatsappEntradaDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.dto.whatsapp.saida.RespostaWhatsappDTO;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.whatsapp.ComandoWhatsapp;
import br.com.oraped.service.whatsapp.MensagemAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorWhatsappService {

    private final EstabelecimentoService estabelecimentoService;

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final MensagemAtendimentoWhatsappService mensagemService;

    private final WhatsappMensagemFactory msg;
    private final ObjectMapper objectMapper;

    private final OrquestradorContextoService contextoService;
    private final OrquestradorRegistroMensagemService registroMensagemService;
    private final OrquestradorTextoLivreService textoLivreService;
    private final OrquestradorRoteamentoService roteamentoService;
    private final OrquestradorParseService parseService;

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

            OrquestradorContexto ctx = contextoService.montarContexto(
                estabelecimento,
                sessao,
                whatsappCliente,
                whatsappReceptor,
                phoneNumberId,
                temSaidaAnterior
            );

            // -------------------------
            // DEBUG ENTRADA (DTO)
            // -------------------------
            System.out.println("[WA] ENTRADA safeTextoEntrada=" + parseService.safeTextoEntrada(req));
            System.out.println("[WA] ENTRADA getTextoOuComando=" + req.getTextoOuComando());
            System.out.println("[WA] DTO texto=" + req.getTexto());
            System.out.println("[WA] DTO comando=" + req.getComando());
            System.out.println("[WA] DTO textoOuComando=" + req.getTextoOuComando());

            // registra entrada
            registroMensagemService.registrarEntrada(
                sessao.getId(),
                parseService.safeTextoEntrada(req),
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

                RoteamentoResultado roteado = textoLivreService.tratarTextoLivre(ctx, req);

                registroMensagemService.registrarSaida(sessao.getId(), roteado.getChave(), roteado.getMensagem());
                return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.getMensagem(), roteado.getExtras());
            }

            // =========================================================
            // 2) COMANDO -> roteia e retorna (com extras)
            // =========================================================
            System.out.println("[WA] FLOW entrandoRoteamento acao=" + comando.getAcao()
                + " raw=" + textoOuComando
            );

            RoteamentoResultado roteado;

            try {
                roteado = roteamentoService.rotearComando(ctx, comando);
            } catch (Exception e) {

                System.out.println("[WA] ERRO rotearComando: " + e.getClass().getName() + " - " + e.getMessage());
                e.printStackTrace();

                MensagemWhatsappSaidaDTO mensagemSaida = msg.texto(
                    whatsappCliente,
                    "⚠️ Erro ao processar sua solicitação.\n\nTente novamente."
                );

                registroMensagemService.registrarSaida(sessao.getId(), "erro_roteamento", mensagemSaida);
                return montarResposta(req, whatsappCliente, whatsappReceptor, mensagemSaida);
            }

            registroMensagemService.registrarSaida(sessao.getId(), roteado.getChave(), roteado.getMensagem());
            return montarResposta(req, whatsappCliente, whatsappReceptor, roteado.getMensagem(), roteado.getExtras());

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
}