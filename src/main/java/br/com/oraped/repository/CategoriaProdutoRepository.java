// src/main/java/br/com/oraped/repository/CategoriaProdutoRepository.java
package br.com.oraped.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.CategoriaProduto;
import br.com.oraped.domain.Estabelecimento;

public interface CategoriaProdutoRepository extends JpaRepository<CategoriaProduto, Long> {

  List<CategoriaProduto> findByEstabelecimentoIdAndAtivaTrueOrderByNomeAsc(Long estabelecimentoId);

  Optional<CategoriaProduto> findByEstabelecimentoAndNomeIgnoreCase(Estabelecimento estabelecimento, String nome);

  boolean existsByEstabelecimentoAndNomeIgnoreCase(Estabelecimento estabelecimento, String nome);
  
}
