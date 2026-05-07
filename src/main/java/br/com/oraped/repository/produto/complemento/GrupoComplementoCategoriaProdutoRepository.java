// src/main/java/br/com/oraped/repository/produto/CategoriaProdutoGrupoComplementoRepository.java
package br.com.oraped.repository.produto.complemento;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.complemento.GrupoComplementoCategoriaProduto;

public interface GrupoComplementoCategoriaProdutoRepository
    extends JpaRepository<GrupoComplementoCategoriaProduto, Long> {

    List<GrupoComplementoCategoriaProduto> findByCategoriaIdOrderByOrdemAsc(Long idCategoria);

    List<GrupoComplementoCategoriaProduto> findByCategoriaIdAndAtivoTrueOrderByOrdemAsc(Long idCategoria);

    Optional<GrupoComplementoCategoriaProduto> findByCategoriaIdAndGrupoId(Long idCategoria, Long idGrupo);
}