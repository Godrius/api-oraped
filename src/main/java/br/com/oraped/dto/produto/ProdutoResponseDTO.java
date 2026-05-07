package br.com.oraped.dto.produto;

import java.math.BigDecimal;

import br.com.oraped.domain.produto.Produto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProdutoResponseDTO {

  private Long idProduto;
  private Long idEstabelecimento;

  private Long idCategoria;
  private String nomeCategoria;

  private Long idMarca;
  private String nomeMarca;

  private String nome;
  private String descricao;
  private BigDecimal preco;

  private boolean disponivelParaVenda;

  public ProdutoResponseDTO(Produto produto) {

    if (produto == null) {
      return;
    }

    this.idProduto = produto.getId();

    if (produto.getEstabelecimento() != null) {
      this.idEstabelecimento = produto.getEstabelecimento().getId();
    }

    if (produto.getCategoria() != null) {
      this.idCategoria = produto.getCategoria().getId();
      this.nomeCategoria = produto.getCategoria().getNome();
    }

    if (produto.getMarca() != null) {
      this.idMarca = produto.getMarca().getId();
      this.nomeMarca = produto.getMarca().getNome();
    }

    this.nome = produto.getNome();
    this.descricao = produto.getDescricao();
    this.preco = produto.getPreco();
    this.disponivelParaVenda = produto.isDisponivelParaVenda();
  }
}