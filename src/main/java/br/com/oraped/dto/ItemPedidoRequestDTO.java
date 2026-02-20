// src/main/java/br/com/oraped/api/dto/ItemPedidoRequestDTO.java
package br.com.oraped.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItemPedidoRequestDTO {

  @NotNull
  private Long idProduto;

  @NotNull
  @Min(1)
  private Integer quantidade;

  @Size(max = 2000)
  private String observacoes;

  @Valid
  private List<ItemPedidoOpcionalRequestDTO> opcionais = new ArrayList<>();
}
