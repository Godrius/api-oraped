package br.com.oraped.domain.marketplace;

import br.com.oraped.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "marketplace",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_marketplace_whatsapp", columnNames = "whatsapp")
    }
)
public class Marketplace extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 30)
    private String whatsapp;

    @Column(nullable = false)
    private boolean ativo = true;
}