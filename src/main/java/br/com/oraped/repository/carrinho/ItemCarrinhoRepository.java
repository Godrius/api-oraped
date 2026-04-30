package br.com.oraped.repository.carrinho;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.carrinho.ItemCarrinho;

public interface ItemCarrinhoRepository extends JpaRepository<ItemCarrinho, Long> {

    List<ItemCarrinho> findByCarrinhoIdOrderByIdAsc(Long idCarrinho);

    void deleteByCarrinhoSessaoId(Long idSessao);
}