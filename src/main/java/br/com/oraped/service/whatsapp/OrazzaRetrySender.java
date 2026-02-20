package br.com.oraped.service.whatsapp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.com.oraped.dto.whatsapp.saida.MensagemInterativaCorpoWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemTextoWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.dto.whatsapp.saida.RespostaWhatsappDTO;
import br.com.oraped.integrations.OrazzaWhatsappCallbackClient;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrazzaRetrySender {

    private final OrazzaWhatsappCallbackClient callbackClient;
    private final WhatsappMensagemFactory msg;

    public void enviarAssincrono(RespostaWhatsappDTO resp) {
        Objects.requireNonNull(resp, "resp é obrigatório");
        callbackClient.enviarRespostaAssincrono(resp);
    }

    public RespostaWhatsappDTO montarRespostaParaReenvio(
        String correlationId,
        String whatsappCliente,
        String whatsappReceptor,
        String phoneNumberId,
        MensagemWhatsappSaidaDTO mensagem
    ) {

        return RespostaWhatsappDTO.builder()
            .idCorrelacao(StringUtils.hasText(correlationId) ? correlationId : ("retry-" + System.nanoTime()))
            .timestamp(OffsetDateTime.now().toString())
            .canal("WHATSAPP")
            .whatsappCliente(whatsappCliente)
            .whatsappReceptor(whatsappReceptor)
            .phoneNumberId(phoneNumberId)
            .wamidEntrada(null)
            .mensagem(mensagem)
            .mensagensExtras(List.of())
            .build();
    }

    public MensagemWhatsappSaidaDTO mensagemFimDeTentativas(String whatsappCliente) {
        return msg.texto(
            whatsappCliente,
            "⚠️ Não consegui processar sua resposta.\n\n"
                + "Digite *REENVIAR* para repetir a última pergunta ou *MENU* para voltar ao início."
        );
    }

    public MensagemWhatsappSaidaDTO prefixarMensagemDeReenvio(MensagemWhatsappSaidaDTO original) {

        String prefixo = "⚠️ Não consegui processar sua opção. Por favor, responda novamente.\n\n";

        if (original == null) {
            return null;
        }

        if ("text".equalsIgnoreCase(original.getType())) {

            MensagemTextoWhatsappDTO t = original.getText();
            String body = (t == null ? "" : msg.safe(t.getBody()));

            return MensagemWhatsappSaidaDTO.builder()
                .messagingProduct(original.getMessagingProduct())
                .to(original.getTo())
                .type("text")
                .text(MensagemTextoWhatsappDTO.builder()
                    .body(msg.trunc(prefixo + body, 4096))
                    .previewUrl(false)
                    .build())
                .build();
        }

        if ("interactive".equalsIgnoreCase(original.getType()) && original.getInteractive() != null) {

            MensagemInterativaWhatsappDTO inter = original.getInteractive();
            MensagemInterativaCorpoWhatsappDTO body = inter.getBody();

            String bodyTxt = body == null ? "" : msg.safe(body.getText());

            MensagemInterativaWhatsappDTO interOut = MensagemInterativaWhatsappDTO.builder()
                .type(inter.getType())
                .body(MensagemInterativaCorpoWhatsappDTO.builder()
                    .text(msg.trunc(prefixo + bodyTxt, 1024))
                    .build())
                .action(inter.getAction())
                .build();

            return MensagemWhatsappSaidaDTO.builder()
                .messagingProduct(original.getMessagingProduct())
                .to(original.getTo())
                .type("interactive")
                .interactive(interOut)
                .build();
        }

        // fallback simples
        return msg.texto(original.getTo(), prefixo + "Por favor, responda novamente.");
    }
}