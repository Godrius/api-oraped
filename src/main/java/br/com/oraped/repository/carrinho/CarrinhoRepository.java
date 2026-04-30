package br.com.oraped.repository.carrinho;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.oraped.domain.carrinho.Carrinho;
import br.com.oraped.domain.carrinho.ItemCarrinho;

public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {

    Optional<Carrinho> findBySessaoId(Long idSessao);

    void deleteBySessaoId(Long idSessao);

    @Query("""
        select distinct c
        from Carrinho c
        left join fetch c.itens i
        left join fetch i.produto p
        where c.sessao.id = :idSessao
    """)
    Optional<Carrinho> buscarComItens(
        @Param("idSessao") Long idSessao
    );

    @Query("""
        select distinct i
        from ItemCarrinho i
        left join fetch i.complementos comp
        left join fetch comp.complemento complemento
        where i.carrinho.sessao.id = :idSessao
    """)
    List<ItemCarrinho> buscarItensComComplementos(
        @Param("idSessao") Long idSessao
    );
}