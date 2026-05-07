package br.com.oraped.repository.produto.tamanho;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.tamanho.OpcaoTamanho;

public interface OpcaoTamanhoRepository extends JpaRepository<OpcaoTamanho, Long> {

    List<OpcaoTamanho> findByGradeIdOrderByOrdemAscNomeAsc(Long idGrade);

    List<OpcaoTamanho> findByGradeIdAndAtivoTrueOrderByOrdemAscNomeAsc(Long idGrade);

    long countByGradeId(Long idGrade);
}