package br.com.oraped.domain.carrinho;

import java.math.BigDecimal;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.produto.Complemento;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
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
 * Representar um complemento escolhido temporariamente durante a montagem de um item.
 *
 * Aplicação:
 * Usado antes da escolha da quantidade do produto principal.
 *
 * Utilização:
 * Ao finalizar a montagem, estes registros serão convertidos em complementos do ItemCarrinho.
 */
@Getter
@Setter
@Entity
@Table(
    name = "complemento_item_carrinho_em_montagem",
    indexes = {
        @Index(name = "idx_complemento_montagem_sessao", columnList = "sessao_id"),
        @Index(name = "idx_complemento_montagem_complemento", columnList = "complemento_id")
    }
)
public class ComplementoItemCarrinhoEmMontagem extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_id", nullable = false)
    private SessaoAtendimentoWhatsapp sessao;

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