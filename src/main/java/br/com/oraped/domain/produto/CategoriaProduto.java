// src/main/java/br/com/oraped/domain/CategoriaProduto.java
package br.com.oraped.domain.produto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.Estabelecimento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "categoria_produto")
public class CategoriaProduto extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "estabelecimento_id", nullable = false)
  private Estabelecimento estabelecimento;

  @Column(nullable = false, length = 80)
  private String nome;

  @Column(nullable = false)
  private boolean ativa = true;

  private Integer ordem;
  
  private Integer quantidadeMultipla;
  
  @JsonIgnore
  @OneToMany(mappedBy = "categoria", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Produto> produtos = new ArrayList<>();
  
  @JsonIgnore
  @OneToMany(mappedBy = "categoria", cascade = CascadeType.ALL, orphanRemoval = false)
  private List<GrupoComplementoCategoriaProduto> gruposComplemento = new ArrayList<>();
}
