// src/main/java/br/com/oraped/domain/Cliente.java
package br.com.oraped.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
  name = "cliente",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_cliente_estabelecimento_telefone", columnNames = {"estabelecimento_id", "telefone"})
  }
)
public class Cliente extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "estabelecimento_id", nullable = false)
  private Estabelecimento estabelecimento;

  @Column(nullable = false, length = 30)
  private String telefone;

  @Column(length = 120)
  private String nome;
}
