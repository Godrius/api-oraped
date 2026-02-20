// src/main/java/br/com/oraped/api/dto/ClienteRequestDTO.java
package br.com.oraped.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteRequestDTO {

  @NotBlank
  @Size(max = 30)
  private String telefone;

  @Size(max = 120)
  private String nome;
}
