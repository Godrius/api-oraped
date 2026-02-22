// src/main/java/br/com/oraped/domain/BairroVizinhanca.java
package br.com.oraped.domain.geolocalizacao;

import br.com.oraped.domain.BaseEntity;
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
    name = "bairro_vizinhanca",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_bairro_vizinho", columnNames = { "bairro_id", "vizinho_id" })
    },
    indexes = {
        @Index(name = "ix_bairro_vizinhanca_bairro", columnList = "bairro_id"),
        @Index(name = "ix_bairro_vizinhanca_vizinho", columnList = "vizinho_id")
    }
)
public class BairroVizinhanca extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bairro_id", nullable = false)
    private Bairro bairro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vizinho_id", nullable = false)
    private Bairro vizinho;

    @Column(nullable = false)
    private Integer ordem;

    @Column
    private Integer distanciaMetros;
}