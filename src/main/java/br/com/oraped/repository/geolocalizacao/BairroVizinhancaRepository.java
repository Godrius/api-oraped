// src/main/java/br/com/oraped/repository/geolocalizacao/BairroVizinhancaRepository.java
package br.com.oraped.repository.geolocalizacao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.geolocalizacao.BairroVizinhanca;

public interface BairroVizinhancaRepository extends JpaRepository<BairroVizinhanca, Long> {

    boolean existsByBairroId(Long bairroId);

    boolean existsByBairroIdAndVizinhoId(Long bairroId, Long vizinhoId);

    void deleteByBairroId(Long bairroId);

    @Query("""
	    select v
	    from BairroVizinhanca bv
	    join bv.vizinho v
	    where bv.bairro.id = :bairroId
	    order by lower(v.nome) asc
	""")
	List<Bairro> findVizinhosOrdenados(@Param("bairroId") Long bairroId);
}