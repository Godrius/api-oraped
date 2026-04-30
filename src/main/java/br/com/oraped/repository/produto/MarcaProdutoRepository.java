// src/main/java/br/com/oraped/repository/MarcaProdutoRepository.java
package br.com.oraped.repository.produto;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.MarcaProduto;

public interface MarcaProdutoRepository extends JpaRepository<MarcaProduto, Long> {

    // Fluxo cliente (já existente)
    List<MarcaProduto> findDistinctByProdutosEstabelecimentoIdAndProdutosCategoriaIdAndProdutosDisponivelParaVendaTrueAndAtivaTrueOrderByNomeAsc(
        Long idEstabelecimento,
        Long idCategoria
    );

    // Admin: listar todas as marcas do estabelecimento (ativas e inativas)
    List<MarcaProduto> findByEstabelecimentoIdOrderByNomeAsc(Long idEstabelecimento);

    // Admin: impedir exclusão quando existir produto vinculado
    boolean existsByEstabelecimentoIdAndId(Long idEstabelecimento, Long idMarca);

    boolean existsByIdAndEstabelecimentoId(Long idMarca, Long idEstabelecimento);

    boolean existsById(Long idMarca);

    boolean existsByProdutosMarcaId(Long idMarca);

    // Alternativa mais restritiva (se quiser validar pelo estabelecimento também):
    boolean existsByProdutosMarcaIdAndProdutosEstabelecimentoId(Long idMarca, Long idEstabelecimento);
}