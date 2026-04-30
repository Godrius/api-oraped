package br.com.oraped.domain.geolocalizacao;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Finalidade:
 * Persistir o resultado de consultas de geolocalização realizadas a partir de CEP.
 *
 * Aplicação:
 * Utilizado para evitar chamadas repetidas ao provedor externo sempre que o mesmo
 * CEP for informado novamente no fluxo de marketplace.
 *
 * Utilização:
 * Deve ser manipulado exclusivamente pelos services de cache geográfico, servindo
 * como armazenamento persistente da resolução de CEP em endereço e coordenadas.
 */
@Entity
@Table(
    name = "geolocalizacao_cache_cep",
    indexes = {
        @Index(name = "idx_geo_cache_cep_cep", columnList = "cep"),
        @Index(name = "idx_geo_cache_cep_expira_em", columnList = "expira_em")
    }
)
@Getter
@Setter
public class GeolocalizacaoCacheCep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // O CEP é a chave natural da consulta e deve permanecer sempre normalizado com 8 dígitos.
    @Column(name = "cep", nullable = false, length = 8, unique = true)
    private String cep;

    @Column(name = "logradouro", length = 255)
    private String logradouro;

    @Column(name = "bairro", length = 150)
    private String bairro;

    @Column(name = "cidade", length = 150)
    private String cidade;

    @Column(name = "uf", length = 2)
    private String uf;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "fonte", length = 50)
    private String fonte;

    // A contagem de hits ajuda a medir reaproveitamento do cache e valor da estratégia.
    @Column(name = "quantidade_hits")
    private Long quantidadeHits;

    @Column(name = "primeira_consulta_em")
    private OffsetDateTime primeiraConsultaEm;

    @Column(name = "ultima_consulta_em")
    private OffsetDateTime ultimaConsultaEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;
}