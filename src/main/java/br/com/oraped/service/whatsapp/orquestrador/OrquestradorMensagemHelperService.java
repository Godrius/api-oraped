// src/main/java/br/com/oraped/service/whatsapp/orquestrador/OrquestradorMensagemHelperService.java
package br.com.oraped.service.whatsapp.orquestrador;

import org.springframework.stereotype.Service;

import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorMensagemHelperService {

    private final WhatsappMensagemFactory msg;

    public MensagemInterativaBotaoReplyWhatsappDTO btn(String id, String title) {
        return MensagemInterativaBotaoReplyWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 20))
            .build();
    }

    public MensagemInterativaItemListaWhatsappDTO row(String id, String title, String description) {
        return MensagemInterativaItemListaWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 24))
            .description(msg.trunc(msg.safe(description), 72))
            .build();
    }
}