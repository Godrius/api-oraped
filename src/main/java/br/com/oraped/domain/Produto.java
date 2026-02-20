// src/main/java/br/com/oraped/domain/Produto.java
package br.com.oraped.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "produto")
public class Produto extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "estabelecimento_id", nullable = false)
  private Estabelecimento estabelecimento;

  @ManyToOne
  @JoinColumn(name = "categoria_id")
  private CategoriaProduto categoria;
  
  @ManyToOne(optional = false)
  @JoinColumn(name = "marca_id", nullable = false)
  private MarcaProduto marca;

  @Column(nullable = false, length = 120)
  private String nome;

  @Column(columnDefinition = "TEXT")
  private String descricao;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal preco;

  /**
   * Regra operacional (WhatsApp):
   * - disponivelParaVenda=true  => aparece no cardápio / pode ser pedido
   * - disponivelParaVenda=false => fica oculto / não pode ser pedido
   */
  @Column(nullable = false)
  private boolean disponivelParaVenda = true;
}
