package br.com.oraped.domain.marketplace;

import br.com.oraped.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "categoria_marketplace")
public class CategoriaMarketplace extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column
    private Integer ordem;
}