// src/main/java/br/com/oraped/api/dto/ItemPedidoOpcionalRequestDTO.java
package br.com.oraped.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItemPedidoOpcionalRequestDTO {

  @NotBlank
  @Size(max = 120)
  private String nome;

  @NotNull
  @Min(1)
  private Integer quantidade;

  @NotNull
  private BigDecimal preco;
}
