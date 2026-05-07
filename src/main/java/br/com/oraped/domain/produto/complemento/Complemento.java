package br.com.oraped.domain.produto.complemento;

import java.math.BigDecimal;

import br.com.oraped.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Representa uma opção dentro de um grupo de complementos.
 *
 * Aplicação:
 * - exemplos: "Massa fina", "Massa grossa", "Borda recheada", "Catupiry", "Cheddar"
 * - pertence a um grupo reutilizável, permitindo que o mesmo conjunto de opções seja usado em vários produtos
 * - o preço adicional é somado ao item do pedido quando o complemento for escolhido
 */
@Getter
@Setter
@Entity
@Table(name = "complemento_produto")
public class Complemento extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "grupo_complemento_id", nullable = false)
    private GrupoComplemento grupo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    /**
     * Valor adicional aplicado ao item do pedido.
     * Use BigDecimal.ZERO para complementos sem acréscimo.
     */
    @Column(name = "preco_adicional", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoAdicional = BigDecimal.ZERO;

    /**
     * Complemento inativo permanece cadastrado, mas não deve aparecer para seleção.
     */
    @Column(nullable = false)
    private boolean ativo = true;
}