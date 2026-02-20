package br.com.oraped.dto.whatsapp.saida;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MensagemInterativaWhatsappDTO {

    private String type;

    private MensagemInterativaCorpoWhatsappDTO body;

    @JsonProperty("action")
    private Object action;
}