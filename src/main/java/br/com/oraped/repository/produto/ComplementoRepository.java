package br.com.oraped.repository.produto;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.Complemento;

public interface ComplementoRepository extends JpaRepository<Complemento, Long> {

    List<Complemento> findByGrupoIdOrderByNomeAsc(Long idGrupo);

    List<Complemento> findByGrupoIdAndAtivoTrueOrderByNomeAsc(Long idGrupo);
    
    long countByGrupoId(Long idGrupo);
}