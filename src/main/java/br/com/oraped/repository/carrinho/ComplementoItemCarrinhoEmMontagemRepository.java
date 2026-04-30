package br.com.oraped.repository.carrinho;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.carrinho.ComplementoItemCarrinhoEmMontagem;

public interface ComplementoItemCarrinhoEmMontagemRepository
    extends JpaRepository<ComplementoItemCarrinhoEmMontagem, Long> {

    List<ComplementoItemCarrinhoEmMontagem> findBySessaoIdOrderByIdAsc(Long idSessao);

    Optional<ComplementoItemCarrinhoEmMontagem> findBySessaoIdAndComplementoId(Long idSessao, Long idComplemento);

    void deleteBySessaoId(Long idSessao);
}