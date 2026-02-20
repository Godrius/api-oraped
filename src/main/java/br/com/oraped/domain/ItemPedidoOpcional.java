package br.com.oraped.domain;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "item_pedido_opcional")
public class ItemPedidoOpcional extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "item_pedido_id", nullable = false)
  private ItemPedido itemPedido;

  @Column(nullable = false, length = 120)
  private String nome;

  @Column(nullable = false)
  private Integer quantidade;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal precoUnitario;
}
