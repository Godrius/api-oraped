package br.com.oraped.repository.produto.tamanho;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.produto.tamanho.OpcaoTamanhoProduto;

public interface OpcaoTamanhoProdutoRepository extends JpaRepository<OpcaoTamanhoProduto, Long> {

    List<OpcaoTamanhoProduto> findByProdutoIdOrderByOpcaoTamanhoOrdemAscOpcaoTamanhoNomeAsc(Long idProduto);

    List<OpcaoTamanhoProduto> findByProdutoIdAndAtivoTrueOrderByOpcaoTamanhoOrdemAscOpcaoTamanhoNomeAsc(Long idProduto);

    Optional<OpcaoTamanhoProduto> findByProdutoIdAndOpcaoTamanhoId(Long idProduto, Long idOpcaoTamanho);

    boolean existsByProdutoIdAndOpcaoTamanhoId(Long idProduto, Long idOpcaoTamanho);
    
    boolean existsByProdutoIdAndAtivoTrueAndPrecoGreaterThan(Long idProduto, BigDecimal preco);
}