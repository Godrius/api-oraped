package br.com.oraped.domain.produto.tamanho;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.domain.produto.Produto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Representa uma grade de tamanhos vinculada a uma categoria ou produto.
 *
 * Aplicação:
 * - quando vinculada à categoria, serve como grade padrão para os produtos da categoria
 * - quando vinculada ao produto, serve apenas para aquele produto específico
 *
 * Regra:
 * - uma grade deve estar associada a uma categoria OU a um produto, nunca aos dois
 * - os preços específicos ficam em OpcaoTamanhoProduto
 */
@Getter
@Setter
@Entity
@Table(name = "grade_tamanho")
public class GradeTamanho extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;

    @ManyToOne
    @JoinColumn(name = "id_categoria_produto")
    private CategoriaProduto categoria;

    @ManyToOne
    @JoinColumn(name = "id_produto")
    private Produto produto;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    // Grade inativa não deve ser oferecida para novas configurações ou seleção no pedido.
    @Column(nullable = false)
    private boolean ativo = true;

    // Exclusão lógica preserva histórico/configurações antigas sem remover fisicamente o cadastro.
    @Column(nullable = false)
    private boolean excluido = false;
}