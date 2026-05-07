package br.com.oraped.domain.produto.tamanho;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.Estabelecimento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Representa uma grade reutilizável de tamanhos de produto.
 *
 * Aplicação:
 * - usada para organizar tamanhos como "Pequena", "Média", "Grande" e "Família"
 * - pertence ao estabelecimento para permitir reaproveitamento em várias categorias do mesmo cardápio
 * - será associada às categorias por GradeTamanhoCategoriaProduto
 *
 * Regra:
 * - a categoria pode possuir no máximo uma grade de tamanhos ativa/configurada
 * - os preços específicos ficam nos itens da grade, ou seja, em OpcaoTamanhoProduto
 */
@Getter
@Setter
@Entity
@Table(name = "grade_tamanho")
public class GradeTamanho extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    /**
     * Grade inativa não deve ser oferecida para novas associações ou seleção no pedido.
     */
    @Column(nullable = false)
    private boolean ativo = true;

    /**
     * Exclusão lógica preserva histórico/configurações antigas sem remover fisicamente o cadastro.
     */
    @Column(nullable = false)
    private boolean excluido = false;
}