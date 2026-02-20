// src/main/java/br/com/oraped/dto/estabelecimento/CategoriaMenuDTO.java
package br.com.oraped.dto.estabelecimento;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoriaMenuDTO {

  private Long idCategoria;
  private String nome;
  private Integer quantidadeMultipla;
  
  private List<MarcaMenuDTO> marcas;
}
