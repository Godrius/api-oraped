package br.com.oraped.repository.produto.tamanho;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.tamanho.GradeTamanhoCategoriaProduto;

public interface GradeTamanhoCategoriaProdutoRepository
    extends JpaRepository<GradeTamanhoCategoriaProduto, Long> {

    Optional<GradeTamanhoCategoriaProduto> findFirstByCategoriaIdAndAtivoTrue(Long idCategoria);

    Optional<GradeTamanhoCategoriaProduto> findByCategoriaIdAndGradeId(Long idCategoria, Long idGrade);

    boolean existsByCategoriaIdAndAtivoTrue(Long idCategoria);
}