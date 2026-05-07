package br.com.oraped.domain.produto.tamanho;

import java.math.BigDecimal;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.produto.Produto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Representa o preço de um tamanho para um produto específico.
 *
 * Aplicação:
 * - produto define o preço de cada tamanho
 *
 * Regra:
 * - preço é FINAL
 * - não é adicional
 */
@Getter
@Setter
@Entity
@Table(name = "opcao_tamanho_produto")
public class OpcaoTamanhoProduto extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(optional = false)
    @JoinColumn(name = "opcao_tamanho_id", nullable = false)
    private OpcaoTamanho opcaoTamanho;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private boolean ativo = true;
}