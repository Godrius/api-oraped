// src/main/java/br/com/oraped/dto/estabelecimento/MarcaMenuDTO.java
package br.com.oraped.dto.estabelecimento;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarcaMenuDTO {

  private Long idMarca;
  private String nome;

  private List<ProdutoMenuDTO> produtos;
}
