// src/main/java/br/com/oraped/repository/ProdutoRepository.java
package br.com.oraped.repository.produto;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.Produto;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

  List<Produto> findByIdIn(Collection<Long> ids);

  List<Produto> findByEstabelecimentoIdOrderByNomeAsc(Long idEstabelecimento);

  List<Produto> findByEstabelecimentoIdOrderByDescricaoAsc(Long estabelecimentoId);
  List<Produto> findByEstabelecimentoIdAndDisponivelParaVendaTrueOrderByDescricaoAsc(Long estabelecimentoId);

  List<Produto> findByEstabelecimentoIdAndDisponivelParaVendaFalseOrderByDescricaoAsc(Long estabelecimentoId);

  List<Produto> findByEstabelecimentoIdAndCategoriaIdOrderByDescricaoAsc(Long estabelecimentoId, Long categoriaId);
  List<Produto> findByEstabelecimentoIdAndCategoriaIdAndDisponivelParaVendaTrueOrderByDescricaoAsc(Long estabelecimentoId, Long categoriaId);

  List<Produto> findByEstabelecimentoIdAndCategoriaIdAndDisponivelParaVendaFalseOrderByDescricaoAsc(Long estabelecimentoId, Long categoriaId);

  List<Produto> findByEstabelecimentoIdAndMarcaIdOrderByDescricaoAsc(Long estabelecimentoId, Long marcaId);
  List<Produto> findByEstabelecimentoIdAndMarcaIdAndDisponivelParaVendaTrueOrderByDescricaoAsc(Long estabelecimentoId, Long marcaId);

  List<Produto> findByEstabelecimentoIdAndMarcaIdAndDisponivelParaVendaFalseOrderByDescricaoAsc(Long estabelecimentoId, Long marcaId);

  List<Produto> findByEstabelecimentoIdAndCategoriaIdAndMarcaIdOrderByDescricaoAsc(Long estabelecimentoId, Long categoriaId, Long marcaId);
  List<Produto> findByEstabelecimentoIdAndCategoriaIdAndMarcaIdAndDisponivelParaVendaTrueOrderByDescricaoAsc(Long estabelecimentoId, Long categoriaId, Long marcaId);

  List<Produto> findByEstabelecimentoIdAndCategoriaIdAndMarcaIdAndDisponivelParaVendaFalseOrderByDescricaoAsc(Long estabelecimentoId, Long categoriaId, Long marcaId);
}