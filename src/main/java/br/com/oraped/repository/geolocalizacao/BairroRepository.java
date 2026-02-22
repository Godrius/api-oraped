// src/main/java/br/com/oraped/repository/geolocalizacao/BairroRepository.java
package br.com.oraped.repository.geolocalizacao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.geolocalizacao.Bairro;

public interface BairroRepository extends JpaRepository<Bairro, Long> {

    Optional<Bairro> findByNomeIgnoreCaseAndCidadeIgnoreCaseAndUfIgnoreCase(String nome, String cidade, String uf);

    Optional<Bairro> findByNomeNormalizadoIgnoreCaseAndCidadeIgnoreCaseAndUfIgnoreCase(
        String nomeNormalizado,
        String cidade,
        String uf
    );

}