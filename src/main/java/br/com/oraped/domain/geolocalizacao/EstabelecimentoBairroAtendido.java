package br.com.oraped.domain.geolocalizacao;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.Estabelecimento;
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
    name = "estabelecimento_bairro_atendido",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_estab_bairro_atendido",
            columnNames = { "estabelecimento_id", "bairro_id" }
        )
    },
    indexes = {
        @Index(name = "ix_estab_bairro_atendido_estab", columnList = "estabelecimento_id"),
        @Index(name = "ix_estab_bairro_atendido_bairro", columnList = "bairro_id")
    }
)
public class EstabelecimentoBairroAtendido extends BaseEntity {

    // A existência do vínculo já significa que o bairro é atendido.
    // Não usamos flag "ativo" para manter a modelagem simples e sem ambiguidade.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;

    // O bairro atendido deve existir no cadastro geográfico e, na prática,
    // será escolhido a partir da vizinhança calculada do estabelecimento.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bairro_id", nullable = false)
    private Bairro bairro;
}