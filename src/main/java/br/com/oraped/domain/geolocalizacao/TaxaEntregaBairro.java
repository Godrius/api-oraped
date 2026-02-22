// src/main/java/br/com/oraped/domain/geolocalizacao/TaxaEntregaBairro.java
package br.com.oraped.domain.geolocalizacao;

import java.math.BigDecimal;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.Estabelecimento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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
    name = "taxa_entrega_bairro",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_taxa_entrega_bairro_estab_bairro",
            columnNames = { "estabelecimento_id", "bairro_id" }
        )
    },
    indexes = {
        @Index(name = "ix_taxa_entrega_estab", columnList = "estabelecimento_id"),
        @Index(name = "ix_taxa_entrega_bairro", columnList = "bairro_id")
    }
)
public class TaxaEntregaBairro extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bairro_id", nullable = false)
    private Bairro bairro;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;
}