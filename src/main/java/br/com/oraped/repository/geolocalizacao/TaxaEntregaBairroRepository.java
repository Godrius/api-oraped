// src/main/java/br/com/oraped/repository/geolocalizacao/TaxaEntregaBairroRepository.java
package br.com.oraped.repository.geolocalizacao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.oraped.domain.geolocalizacao.TaxaEntregaBairro;

public interface TaxaEntregaBairroRepository extends JpaRepository<TaxaEntregaBairro, Long> {

    Optional<TaxaEntregaBairro> findByEstabelecimentoIdAndBairroId(Long estabelecimentoId, Long bairroId);

    boolean existsByEstabelecimentoIdAndBairroId(Long estabelecimentoId, Long bairroId);

    void deleteByEstabelecimentoIdAndBairroId(Long estabelecimentoId, Long bairroId);
    

    @Query("""
        select t
        from TaxaEntregaBairro t
        join fetch t.bairro
        where t.estabelecimento.id = :idEstabelecimento
          and t.bairro.id in :idsBairro
    """)
    List<TaxaEntregaBairro> findByEstabelecimentoAndBairros(
        @Param("idEstabelecimento") Long idEstabelecimento,
        @Param("idsBairro") List<Long> idsBairro
    );
    
    
}