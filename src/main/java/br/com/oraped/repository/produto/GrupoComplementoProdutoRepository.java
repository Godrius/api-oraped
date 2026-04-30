package br.com.oraped.repository.produto;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.GrupoComplementoProduto;

public interface GrupoComplementoProdutoRepository extends JpaRepository<GrupoComplementoProduto, Long> {

    List<GrupoComplementoProduto> findByProdutoIdOrderByOrdemAsc(Long idProduto);

    List<GrupoComplementoProduto> findByProdutoIdAndAtivoTrueOrderByOrdemAsc(Long idProduto);

    Optional<GrupoComplementoProduto> findByProdutoIdAndGrupoId(Long idProduto, Long idGrupo);
}