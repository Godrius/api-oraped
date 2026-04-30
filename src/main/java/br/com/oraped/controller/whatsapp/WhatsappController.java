package br.com.oraped.controller.whatsapp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.oraped.dto.whatsapp.entrada.MensagemWhatsappEntradaDTO;
import br.com.oraped.dto.whatsapp.saida.RespostaWhatsappDTO;
import br.com.oraped.integration.OrazzaWhatsappCallbackClient;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorWhatsappService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/whatsapp")
public class WhatsappController {

    private final OrquestradorWhatsappService orquestradorWhatsappService;
    private final ObjectMapper objectMapper;
    private final OrazzaWhatsappCallbackClient orazzaWhatsappCallbackClient;

    /**
     * - Orazza encaminha o payload cru (igual Meta).
     * - Aqui aceitamos 100% (sem validar assinatura).
     * - Processa e faz callback para o Orazza com RespostaWhatsappDTO.
     * - Responde rápido ao Orazza.
     */
    @PostMapping
    public ResponseEntity<String> receberEvento(
        @RequestBody byte[] corpoBruto,
        @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura256
    ) throws Exception {

        String payloadBruto = corpoBruto == null
            ? null
            : new String(corpoBruto, StandardCharsets.UTF_8);

        System.out.println("\n========== ORAPED WEBHOOK RECEBIDO ==========");
        System.out.println(payloadBruto);
        System.out.println("=============================================\n");

        JsonNode raiz = objectMapper.readTree(payloadBruto);

        List<MensagemWhatsappEntradaDTO> entradas = extrairMensagens(raiz);

        for (MensagemWhatsappEntradaDTO dto : entradas) {
            RespostaWhatsappDTO resposta = orquestradorWhatsappService.processar(dto);

            try {
                orazzaWhatsappCallbackClient.enviarRespostaAssincrono(resposta);
            } catch (Exception e) {
                log.error("Falha ao enfileirar callback para Orazza. idCorrelacao={} err={}",
                    safe(resposta == null ? null : resposta.getIdCorrelacao()),
                    safeMsg(e),
                    e
                );
            }
        }

        return ResponseEntity.ok("OK");
    }

    private List<MensagemWhatsappEntradaDTO> extrairMensagens(JsonNode raiz) {

        List<MensagemWhatsappEntradaDTO> saida = new ArrayList<>();

        JsonNode entradas = raiz.path("entry");
        if (!entradas.isArray()) {
            return saida;
        }

        for (JsonNode entry : entradas) {
            JsonNode mudancas = entry.path("changes");
            if (!mudancas.isArray()) {
                continue;
            }

            for (JsonNode change : mudancas) {
                JsonNode valor = change.path("value");

                JsonNode metadata = valor.path("metadata");
                String displayPhone = textoOuNulo(metadata, "display_phone_number");
                String phoneNumberId = textoOuNulo(metadata, "phone_number_id");

                String whatsappReceptor = normalizarSomenteDigitos(displayPhone);
                if (!StringUtils.hasText(whatsappReceptor)) {
                    whatsappReceptor = phoneNumberId;
                }

                // O nome do perfil do cliente vem no bloco contacts.profile.name.
                String nomeClienteWhatsapp = null;
                JsonNode contacts = valor.path("contacts");
                if (contacts.isArray() && !contacts.isEmpty()) {
                    JsonNode contact = contacts.get(0);
                    JsonNode profile = contact.path("profile");

                    if (profile != null && profile.isObject()) {
                        nomeClienteWhatsapp = textoOuNulo(profile, "name");
                    }
                }

                JsonNode mensagens = valor.path("messages");
                if (!mensagens.isArray()) {
                    continue;
                }

                for (JsonNode msg : mensagens) {
                    String whatsappCliente = normalizarSomenteDigitos(textoOuNulo(msg, "from"));
                    String idMensagem = textoOuNulo(msg, "id");
                    String tipo = textoOuNulo(msg, "type");

                    String idCorrelacao = null;
                    JsonNode contexto = msg.path("context");
                    if (contexto != null && contexto.isObject()) {
                        idCorrelacao = textoOuNulo(contexto, "id");
                    }

                    String texto = null;
                    String comando = null;

                    String tipoMidia = null;
                    String idMidia = null;
                    String mimeTypeMidia = null;
                    String sha256Midia = null;
                    String urlMidia = null;

                    Double latitudeLocalizacao = null;
                    Double longitudeLocalizacao = null;
                    String nomeLocalizacao = null;
                    String enderecoLocalizacao = null;

                    if ("text".equals(tipo)) {
                        texto = msg.path("text").path("body").asText(null);

                    } else if ("interactive".equals(tipo)) {
                        JsonNode interactive = msg.path("interactive");

                        JsonNode botao = interactive.path("button_reply");
                        if (botao.isObject()) {
                            comando = botao.path("id").asText(null);
                            if (!StringUtils.hasText(texto)) {
                                texto = botao.path("title").asText(null);
                            }
                        }

                        JsonNode lista = interactive.path("list_reply");
                        if (lista.isObject()) {
                            comando = lista.path("id").asText(null);
                            if (!StringUtils.hasText(texto)) {
                                texto = lista.path("title").asText(null);
                            }
                        }

                    } else if ("image".equals(tipo)) {
                        JsonNode image = msg.path("image");

                        tipoMidia = "image";
                        idMidia = textoOuNulo(image, "id");
                        mimeTypeMidia = textoOuNulo(image, "mime_type");
                        sha256Midia = textoOuNulo(image, "sha256");
                        urlMidia = textoOuNulo(image, "url");

                    } else if ("location".equals(tipo)) {
                        JsonNode location = msg.path("location");

                        // A localização compartilhada é usada pelo marketplace para descobrir quem pode atender o cliente.
                        if (location.isObject()) {
                            latitudeLocalizacao = location.path("latitude").isNumber()
                                ? location.path("latitude").asDouble()
                                : null;

                            longitudeLocalizacao = location.path("longitude").isNumber()
                                ? location.path("longitude").asDouble()
                                : null;

                            nomeLocalizacao = textoOuNulo(location, "name");
                            enderecoLocalizacao = textoOuNulo(location, "address");
                        }
                    }

                    MensagemWhatsappEntradaDTO dto = new MensagemWhatsappEntradaDTO();
                    dto.setPhoneNumberId(phoneNumberId);
                    dto.setWhatsappCliente(whatsappCliente);
                    dto.setWhatsappReceptor(whatsappReceptor);
                    dto.setNomeClienteWhatsapp(nomeClienteWhatsapp);
                    dto.setTexto(texto);
                    dto.setComando(comando);
                    dto.setIdMensagem(idMensagem);
                    dto.setIdCorrelacao(idCorrelacao);

                    // Dados de mídia são usados pelos fluxos administrativos de envio de foto.
                    dto.setTipoMidia(tipoMidia);
                    dto.setIdMidia(idMidia);
                    dto.setMimeTypeMidia(mimeTypeMidia);
                    dto.setSha256Midia(sha256Midia);
                    dto.setUrlMidia(urlMidia);

                    // Dados de localização alimentam o fluxo inicial do marketplace.
                    dto.setLatitudeLocalizacao(latitudeLocalizacao);
                    dto.setLongitudeLocalizacao(longitudeLocalizacao);
                    dto.setNomeLocalizacao(nomeLocalizacao);
                    dto.setEnderecoLocalizacao(enderecoLocalizacao);

                    dto.setPayloadOriginal(raiz);

                    saida.add(dto);
                }
            }
        }

        return saida;
    }

    private String textoOuNulo(JsonNode node, String campo) {

        if (node == null) return null;

        JsonNode v = node.path(campo);

        return v.isMissingNode() || v.isNull() ? null : v.asText(null);
    }

    private String normalizarSomenteDigitos(String s) {

        if (!StringUtils.hasText(s)) return null;

        String d = s.replaceAll("\\D+", "");

        return d.isEmpty() ? null : d;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String safeMsg(Throwable t) {
        String m = t == null ? null : t.getMessage();
        return m == null ? "" : m.replaceAll("[\\r\\n]+", " ").trim();
    }
}