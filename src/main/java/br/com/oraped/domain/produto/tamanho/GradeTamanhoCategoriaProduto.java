package br.com.oraped.domain.produto.tamanho;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.produto.CategoriaProduto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Associação entre categoria de produto e grade de tamanhos.
 *
 * Aplicação:
 * - define a grade de tamanhos herdada pelos produtos da categoria
 * - evita configurar tamanhos produto por produto
 * - permite ativar/desativar a associação sem perder histórico/configuração
 *
 * Regra:
 * - cada categoria pode estar vinculada a no máximo uma grade de tamanhos
 */
@Getter
@Setter
@Entity
@Table(
    name = "categoria_produto_grade_tamanho",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_categoria_produto_grade_tamanho", columnNames = "categoria_produto_id")
    }
)
public class GradeTamanhoCategoriaProduto extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_produto_id", nullable = false)
    private CategoriaProduto categoria;

    @ManyToOne(optional = false)
    @JoinColumn(name = "grade_tamanho_id", nullable = false)
    private GradeTamanho grade;

    /**
     * Associação inativa deixa de aplicar a grade aos produtos da categoria.
     */
    @Column(nullable = false)
    private boolean ativo = true;
}