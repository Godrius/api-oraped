// src/main/java/br/com/oraped/dto/whatsapp/saida/MensagemInterativaItemListaWhatsappDTO.java
package br.com.oraped.dto.whatsapp.saida;

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
public class MensagemInterativaItemListaWhatsappDTO {

  // este campo é o mais importante: nele vai COMANDO|...
  private String id;

  private String title;

  private String description;
}