// src/main/java/br/com/oraped/domain/AdministradorEstabelecimento.java
package br.com.oraped.domain;

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
@Table(name = "administrador_estabelecimento")
public class AdministradorEstabelecimento extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "estabelecimento_id", nullable = false)
  private Estabelecimento estabelecimento;

  @Column(nullable = false, length = 120)
  private String nome;

  @Column(nullable = false, length = 30)
  private String whatsapp;

  @Column(nullable = false)
  private boolean ativo = true;
}
