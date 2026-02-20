// src/main/java/br/com/oraped/dto/whatsapp/saida/RespostaWhatsappDTO.java
package br.com.oraped.dto.whatsapp.saida;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

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
public class RespostaWhatsappDTO {

    private String idCorrelacao;
    private String timestamp;
    private String canal;

    private String whatsappCliente;
    private String whatsappReceptor;

    // usado pelo Orazza para chamar /{phoneNumberId}/messages
    private String phoneNumberId;

    /**
     * ID (wamid.*) da mensagem recebida do cliente (entrada).
     * O Orazza usa para typing indicator antes de enviar resposta.
     */
    private String wamidEntrada;

    private MensagemWhatsappSaidaDTO mensagem;
    private List<MensagemWhatsappSaidaDTO> mensagensExtras;
}