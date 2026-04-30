package br.com.oraped.repository.geolocalizacao;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.geolocalizacao.GeolocalizacaoCacheCoordenada;

public interface GeolocalizacaoCacheCoordenadaRepository extends JpaRepository<GeolocalizacaoCacheCoordenada, Long> {

    Optional<GeolocalizacaoCacheCoordenada> findByChaveConsultaAndExpiraEmAfter(
        String chaveConsulta,
        OffsetDateTime agora
    );
}