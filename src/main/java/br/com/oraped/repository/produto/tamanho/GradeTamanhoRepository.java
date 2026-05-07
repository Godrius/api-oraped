package br.com.oraped.repository.produto.tamanho;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.tamanho.GradeTamanho;

public interface GradeTamanhoRepository extends JpaRepository<GradeTamanho, Long> {

    List<GradeTamanho> findByEstabelecimentoIdOrderByNomeAsc(Long idEstabelecimento);

    List<GradeTamanho> findByEstabelecimentoIdAndAtivoTrueOrderByNomeAsc(Long idEstabelecimento);

    List<GradeTamanho> findByEstabelecimentoIdAndExcluidoFalseOrderByNomeAsc(Long idEstabelecimento);

    List<GradeTamanho> findByEstabelecimentoIdAndAtivoTrueAndExcluidoFalseOrderByNomeAsc(Long idEstabelecimento);

    Optional<GradeTamanho> findFirstByEstabelecimentoIdAndExcluidoFalseOrderByIdAsc(Long idEstabelecimento);
}