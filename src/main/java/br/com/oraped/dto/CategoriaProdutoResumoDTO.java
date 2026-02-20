package br.com.oraped.dto;

import br.com.oraped.domain.CategoriaProduto;
import lombok.Data;

@Data
public class CategoriaProdutoResumoDTO {

  private Long idCategoria;
  private String nomeCategoria;

  public CategoriaProdutoResumoDTO(CategoriaProduto c) {
    this.idCategoria = c.getId();
    this.nomeCategoria = c.getNome();
  }
}
