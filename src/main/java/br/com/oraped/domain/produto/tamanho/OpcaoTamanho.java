package br.com.oraped.domain.produto.tamanho;

import br.com.oraped.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Representa uma opção de tamanho dentro de uma grade.
 *
 * Aplicação:
 * - exemplos: "Pequena", "Média", "Grande"
 *
 * Regra:
 * - NÃO possui preço
 * - apenas define a estrutura da grade
 */
@Getter
@Setter
@Entity
@Table(name = "opcao_tamanho")
public class OpcaoTamanho extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "grade_tamanho_id", nullable = false)
    private GradeTamanho grade;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private Integer ordem = 1;

    @Column(nullable = false)
    private boolean ativo = true;
}