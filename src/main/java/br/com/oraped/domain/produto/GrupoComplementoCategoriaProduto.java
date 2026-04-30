// src/main/java/br/com/oraped/domain/produto/CategoriaProdutoGrupoComplemento.java
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
 * Associação entre categoria de produto e grupo de complementos.
 *
 * Aplicação:
 * - define grupos de complementos herdados pelos produtos da categoria
 * - evita configurar os mesmos complementos produto por produto
 * - permite ativar/desativar a associação sem perder histórico/configuração
 */
@Getter
@Setter
@Entity
@Table(name = "categoria_produto_grupo_complemento")
public class GrupoComplementoCategoriaProduto extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_produto_id", nullable = false)
    private CategoriaProduto categoria;

    @ManyToOne(optional = false)
    @JoinColumn(name = "grupo_complemento_id", nullable = false)
    private GrupoComplemento grupo;

    /**
     * Ordem de apresentação dos grupos herdados no fluxo de compra.
     */
    @Column(nullable = false)
    private Integer ordem = 1;

    /**
     * Associação inativa deixa de ser herdada pelos produtos da categoria.
     */
    @Column(nullable = false)
    private boolean ativo = true;
}