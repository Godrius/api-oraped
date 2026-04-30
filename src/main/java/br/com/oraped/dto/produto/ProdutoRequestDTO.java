// src/main/java/br/com/oraped/dto/ProdutoRequestDTO.java
package br.com.oraped.dto.produto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProdutoRequestDTO {

  // Categoria: pode vir id ou nome (criação automática)
  private Long idCategoria;
  private String nomeCategoria;

  // Marca: pode vir id ou nome (criação automática)
  private Long idMarca;
  private String nomeMarca;

  @NotBlank(message = "nome é obrigatório")
  @Size(max = 120, message = "nome deve ter no máximo 120 caracteres")
  private String nome;

  // opcional
  private String descricao;

  @NotNull(message = "preco é obrigatório")
  private BigDecimal preco;

  // default true quando null (no service)
  private Boolean disponivelParaVenda = true;
}
