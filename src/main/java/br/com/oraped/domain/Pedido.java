// src/main/java/br/com/oraped/domain/Pedido.java
package br.com.oraped.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.enums.TipoAtendimento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pedido")
public class Pedido extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "estabelecimento_id", nullable = false)
  private Estabelecimento estabelecimento;

  @ManyToOne(optional = false)
  @JoinColumn(name = "cliente_id", nullable = false)
  private Cliente cliente;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private StatusPedido status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TipoAtendimento tipoAtendimento;

  // Snapshot (opcional, mas útil)
  @Column(length = 30)
  private String clienteTelefone;

  @Column(length = 120)
  private String clienteNome;

  @Column(length = 20)
  private String numeroMesa;

  @Column(columnDefinition = "TEXT")
  private String enderecoEntrega;

  @Column(columnDefinition = "TEXT")
  private String observacoes;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal subtotal = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal taxaServico = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal taxaEntrega = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal total = BigDecimal.ZERO;

  @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ItemPedido> itens = new ArrayList<>();
}