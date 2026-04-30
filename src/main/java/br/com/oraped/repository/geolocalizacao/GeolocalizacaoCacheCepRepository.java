package br.com.oraped.repository.geolocalizacao;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.oraped.domain.geolocalizacao.GeolocalizacaoCacheCep;

/**
 * Finalidade:
 * Acessar os registros persistidos de cache geográfico por CEP.
 *
 * Aplicação:
 * Utilizado pelos services de geolocalização para localizar entradas válidas
 * ainda não expiradas antes de consultar provedores externos.
 *
 * Utilização:
 * Deve ser consumido apenas pelas camadas de serviço responsáveis pelo cache
 * geográfico, preservando a regra de negócio fora do repositório.
 */
@Repository
public interface GeolocalizacaoCacheCepRepository extends JpaRepository<GeolocalizacaoCacheCep, Long> {

    Optional<GeolocalizacaoCacheCep> findByCepAndExpiraEmAfter(String cep, OffsetDateTime dataHora);
}