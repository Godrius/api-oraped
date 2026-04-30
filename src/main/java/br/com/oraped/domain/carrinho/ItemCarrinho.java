package br.com.oraped.domain.carrinho;

import java.util.ArrayList;
import java.util.List;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.produto.Produto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Finalidade:
 * Representar um produto escolhido dentro do carrinho temporário.
 *
 * Aplicação:
 * Usado para manter produto, quantidade e complementos antes da criação do pedido.
 *
 * Utilização:
 * Itens iguais com complementos diferentes devem ser mantidos como registros separados.
 */
@Getter
@Setter
@Entity
@Table(
    name = "item_carrinho",
    indexes = {
        @Index(name = "idx_item_carrinho_carrinho", columnList = "carrinho_id"),
        @Index(name = "idx_item_carrinho_produto", columnList = "produto_id")
    }
)
public class ItemCarrinho extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "carrinho_id", nullable = false)
    private Carrinho carrinho;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @OneToMany(mappedBy = "itemCarrinho", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComplementoItemCarrinho> complementos = new ArrayList<>();
}