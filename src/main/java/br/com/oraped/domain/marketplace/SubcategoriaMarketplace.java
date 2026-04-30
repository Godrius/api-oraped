package br.com.oraped.domain.marketplace;

import br.com.oraped.domain.BaseEntity;
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
@Table(name = "subcategoria_marketplace")
public class SubcategoriaMarketplace extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_marketplace_id", nullable = false)
    private CategoriaMarketplace categoria;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column
    private Integer ordem;
}