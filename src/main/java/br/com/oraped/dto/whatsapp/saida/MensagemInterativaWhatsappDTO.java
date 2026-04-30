package br.com.oraped.dto.whatsapp.saida;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MensagemInterativaWhatsappDTO {

    private String type;

    private MensagemInterativaHeaderWhatsappDTO header;

    private MensagemInterativaCorpoWhatsappDTO body;

    @JsonProperty("action")
    private Object action;

}