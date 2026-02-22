// src/main/java/br/com/oraped/domain/Bairro.java
package br.com.oraped.domain.geolocalizacao;

import br.com.oraped.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "bairro",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_bairro_nome_cidade_uf", columnNames = { "nomeNormalizado", "cidade", "uf" })
    },
    indexes = {
        @Index(name = "ix_bairro_cidade_uf", columnList = "cidade,uf"),
        @Index(name = "ix_bairro_nome", columnList = "nome")
    }
)
public class Bairro extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 150)
    private String nomeNormalizado;
    
    @Column(nullable = false, length = 120)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column
    private Double latitude;

    @Column
    private Double longitude;
    
    
}