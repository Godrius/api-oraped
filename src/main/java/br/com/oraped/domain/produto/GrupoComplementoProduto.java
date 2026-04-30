package br.com.oraped.domain.produto;

import br.com.oraped.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Associação entre produto e grupo de complementos.
 *
 * Aplicação:
 * - permite reaproveitar o mesmo grupo em vários produtos
 * - define a ordem em que os grupos serão exibidos no fluxo de compra pelo WhatsApp
 * - permite ativar/desativar um grupo para um produto sem apagar o cadastro reutilizável
 */
@Getter
@Setter
@Entity
@Table(name = "produto_grupo_complemento")
public class GrupoComplementoProduto extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(optional = false)
    @JoinColumn(name = "grupo_complemento_id", nullable = false)
    private GrupoComplemento grupo;

    /**
     * Ordem de apresentação no fluxo de compra.
     * Exemplo: 1 = massa, 2 = borda, 3 = recheio da borda.
     */
    @Column(nullable = false)
    private Integer ordem = 1;

    /**
     * Permite desabilitar a associação sem remover o vínculo histórico/configurado.
     */
    @Column(nullable = false)
    private boolean ativo = true;
}