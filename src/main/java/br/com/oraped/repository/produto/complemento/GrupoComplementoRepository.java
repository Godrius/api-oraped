package br.com.oraped.repository.produto.complemento;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.complemento.GrupoComplemento;

public interface GrupoComplementoRepository extends JpaRepository<GrupoComplemento, Long> {

    List<GrupoComplemento> findByEstabelecimentoIdOrderByNomeAsc(Long idEstabelecimento);

    List<GrupoComplemento> findByEstabelecimentoIdAndAtivoTrueOrderByNomeAsc(Long idEstabelecimento);

    List<GrupoComplemento> findByEstabelecimentoIdAndExcluidoFalseOrderByNomeAsc(Long idEstabelecimento);

    List<GrupoComplemento> findByEstabelecimentoIdAndAtivoTrueAndExcluidoFalseOrderByNomeAsc(Long idEstabelecimento);

}