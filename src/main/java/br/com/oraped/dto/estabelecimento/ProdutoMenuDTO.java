// src/main/java/br/com/oraped/dto/estabelecimento/ProdutoMenuDTO.java
package br.com.oraped.dto.estabelecimento;

import java.math.BigDecimal;

import br.com.oraped.domain.Produto;
import lombok.Getter;

@Getter
public class ProdutoMenuDTO {

  private Long idProduto;
  private String nome;
  private String descricao;
  private BigDecimal preco;

  public ProdutoMenuDTO(Produto p) {
    this.idProduto = p.getId();
    this.nome = p.getNome();
    this.descricao = p.getDescricao();
    this.preco = p.getPreco();
  }
}
