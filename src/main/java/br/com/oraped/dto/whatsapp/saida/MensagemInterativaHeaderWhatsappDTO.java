package br.com.oraped.dto.whatsapp.saida;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MensagemInterativaHeaderWhatsappDTO {

    private String type;

    private MensagemInterativaImagemWhatsappDTO image;

}