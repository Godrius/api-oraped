package br.com.oraped.dto;

import br.com.oraped.domain.MarcaProduto;
import lombok.Data;

@Data
public class MarcaProdutoResumoDTO {

  private Long idMarca;
  private String nomeMarca;

  public MarcaProdutoResumoDTO(MarcaProduto m) {
    this.idMarca = m.getId();
    this.nomeMarca = m.getNome();
  }
}
