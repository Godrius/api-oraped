package br.com.oraped.dto.produto;

import br.com.oraped.domain.produto.CategoriaProduto;
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
