package br.com.oraped.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "item_pedido")
public class ItemPedido extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @ManyToOne(optional = false)
  @JoinColumn(name = "produto_id", nullable = false)
  private Produto produto;

  @Column(nullable = false)
  private Integer quantidade;

  @Column(columnDefinition = "TEXT")
  private String observacoes;

  // snapshot
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal precoUnitarioProduto;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal subtotalItem;

  @OneToMany(mappedBy = "itemPedido", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ItemPedidoOpcional> opcionais = new ArrayList<>();
}
