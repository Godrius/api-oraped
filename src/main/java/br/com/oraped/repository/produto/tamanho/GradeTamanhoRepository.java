package br.com.oraped.repository.produto.tamanho;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.tamanho.GradeTamanho;

public interface GradeTamanhoRepository extends JpaRepository<GradeTamanho, Long> {

    List<GradeTamanho> findByEstabelecimentoIdAndExcluidoFalseOrderByNomeAsc(Long idEstabelecimento);

    List<GradeTamanho> findByEstabelecimentoIdAndAtivoTrueAndExcluidoFalseOrderByNomeAsc(Long idEstabelecimento);

    Optional<GradeTamanho> findByCategoriaIdAndAtivoTrueAndExcluidoFalse(Long idCategoria);

    Optional<GradeTamanho> findByProdutoIdAndAtivoTrueAndExcluidoFalse(Long idProduto);

    boolean existsByCategoriaIdAndAtivoTrueAndExcluidoFalse(Long idCategoria);

    boolean existsByProdutoIdAndAtivoTrueAndExcluidoFalse(Long idProduto);
}