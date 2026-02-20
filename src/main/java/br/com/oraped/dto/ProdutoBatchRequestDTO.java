// src/main/java/br/com/oraped/dto/ProdutoBatchRequestDTO.java
package br.com.oraped.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProdutoBatchRequestDTO {

  @NotNull
  private Long idEstabelecimento;

  @Valid
  @NotNull
  @Size(min = 1)
  private List<ProdutoRequestDTO> produtos = new ArrayList<>();
}
