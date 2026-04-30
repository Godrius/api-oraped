package br.com.oraped.domain.geolocalizacao;

import java.time.OffsetDateTime;

import br.com.oraped.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Finalidade:
 * Persistir em banco o resultado da resolução geográfica de coordenadas,
 * evitando chamadas repetidas ao provedor externo para locais já consultados.
 *
 * Aplicação:
 * Utilizado no fluxo de marketplace para reaproveitar a conversão de latitude/longitude
 * em bairro, cidade e UF, inclusive entre sessões de usuários distintos.
 *
 * Utilização:
 * Deve ser consultado antes de qualquer chamada externa de reverse geocoding.
 * A chave do cache é baseada em coordenadas arredondadas para maximizar reaproveitamento
 * sem perder precisão relevante para o discovery.
 */
@Getter
@Setter
@Entity
@Table(
    name = "geolocalizacao_cache_coordenada",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_geo_cache_coord_chave", columnNames = "chave_consulta")
    },
    indexes = {
        @Index(name = "ix_geo_cache_coord_expira", columnList = "expira_em"),
        @Index(name = "ix_geo_cache_coord_lat_lon", columnList = "latitude_arredondada,longitude_arredondada")
    }
)
public class GeolocalizacaoCacheCoordenada extends BaseEntity {

    @Column(name = "chave_consulta", nullable = false, length = 80)
    private String chaveConsulta;

    @Column(name = "latitude_arredondada", nullable = false)
    private Double latitudeArredondada;

    @Column(name = "longitude_arredondada", nullable = false)
    private Double longitudeArredondada;

    @Column(name = "bairro", length = 120)
    private String bairro;

    @Column(name = "cidade", length = 120)
    private String cidade;

    @Column(name = "uf", length = 2)
    private String uf;

    @Column(name = "latitude_original")
    private Double latitudeOriginal;

    @Column(name = "longitude_original")
    private Double longitudeOriginal;

    @Column(name = "fonte", length = 60)
    private String fonte;

    @Column(name = "quantidade_hits", nullable = false)
    private Long quantidadeHits;

    @Column(name = "primeira_consulta_em", nullable = false)
    private OffsetDateTime primeiraConsultaEm;

    @Column(name = "ultima_consulta_em", nullable = false)
    private OffsetDateTime ultimaConsultaEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;
}