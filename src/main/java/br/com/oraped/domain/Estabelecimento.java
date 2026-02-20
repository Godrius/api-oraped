// src/main/java/br/com/oraped/domain/Estabelecimento.java
package br.com.oraped.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "estabelecimento")
public class Estabelecimento extends BaseEntity {

  @Column(nullable = false, length = 120)
  private String nome;

  @Column(nullable = false)
  private boolean ativo = true;

  /**
   * Regra operacional (WhatsApp):
   * - aberto=true  => aceita pedidos
   * - aberto=false => não aceita pedidos
   */
  @Column(nullable = false)
  private boolean aberto = true;

  @Column(length = 40)
  private String timezone;

  @Column(length = 30)
  private String whatsapp;

  @Column(columnDefinition = "TEXT")
  private String endereco;

  @Column(columnDefinition = "TEXT")
  private String configuracoesJson;

  @JsonIgnore
  @OneToMany(mappedBy = "estabelecimento", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<CategoriaProduto> categorias = new ArrayList<>();

  @JsonIgnore
  @OneToMany(mappedBy = "estabelecimento", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<MarcaProduto> marcas = new ArrayList<>();

  @JsonIgnore
  @OneToMany(mappedBy = "estabelecimento", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Produto> produtos = new ArrayList<>();

  @JsonIgnore
  @OneToMany(mappedBy = "estabelecimento", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<AdministradorEstabelecimento> administradores = new ArrayList<>();

  
}
