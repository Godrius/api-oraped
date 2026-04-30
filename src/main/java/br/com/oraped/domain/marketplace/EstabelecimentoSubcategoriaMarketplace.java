package br.com.oraped.domain.marketplace;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.Estabelecimento;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
    name = "estabelecimento_subcategoria_marketplace",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_estab_subcategoria_marketplace",
            columnNames = { "estabelecimento_id", "subcategoria_marketplace_id" }
        )
    }
)
public class EstabelecimentoSubcategoriaMarketplace extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcategoria_marketplace_id", nullable = false)
    private SubcategoriaMarketplace subcategoriaMarketplace;
}