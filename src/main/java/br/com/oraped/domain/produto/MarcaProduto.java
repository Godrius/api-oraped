package br.com.oraped.domain.produto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.Estabelecimento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "marca_produto")
public class MarcaProduto extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "estabelecimento_id", nullable = false)
  private Estabelecimento estabelecimento;

  @Column(nullable = false, length = 80)
  private String nome;

  @Column(nullable = false)
  private boolean ativa = true;

  @OneToMany(mappedBy = "marca", fetch = FetchType.LAZY)
  @JsonIgnore
  private List<Produto> produtos = new ArrayList<>();

}
