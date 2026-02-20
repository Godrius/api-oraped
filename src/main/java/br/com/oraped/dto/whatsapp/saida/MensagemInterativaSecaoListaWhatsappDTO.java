// src/main/java/br/com/oraped/dto/whatsapp/saida/MensagemInterativaSecaoListaWhatsappDTO.java
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
public class MensagemInterativaSecaoListaWhatsappDTO {

  private String title;

  private List<MensagemInterativaItemListaWhatsappDTO> rows;
}