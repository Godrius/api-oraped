package br.com.oraped.domain.carrinho;

import java.math.BigDecimal;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.produto.Complemento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Finalidade:
 * Representar um complemento escolhido para um item do carrinho temporário.
 *
 * Aplicação:
 * Usado para guardar quantidade e preço do complemento antes da criação do pedido.
 *
 * Utilização:
 * O preço unitário é salvo como snapshot para preservar o valor escolhido no momento da compra.
 */
@Getter
@Setter
@Entity
@Table(
    name = "complemento_item_carrinho",
    indexes = {
        @Index(name = "idx_complemento_item_carrinho_item", columnList = "item_carrinho_id"),
        @Index(name = "idx_complemento_item_carrinho_complemento", columnList = "complemento_id")
    }
)
public class ComplementoItemCarrinho extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_carrinho_id", nullable = false)
    private ItemCarrinho itemCarrinho;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "complemento_id", nullable = false)
    private Complemento complemento;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;
}