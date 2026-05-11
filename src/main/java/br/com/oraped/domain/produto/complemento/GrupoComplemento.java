package br.com.oraped.domain.produto.complemento;

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
 * Representa um grupo de complementos vinculado a uma categoria ou produto.
 *
 * Aplicação:
 * - usado para organizar opções como "Tipo de massa", "Borda", "Recheio da borda" e "Molho extra"
 * - quando vinculado à categoria, é aplicado aos produtos da categoria
 * - quando vinculado ao produto, é aplicado apenas ao produto específico
 *
 * Regra:
 * - um grupo deve estar associado a uma categoria OU a um produto, nunca aos dois
 * - os complementos pertencem ao grupo e não são compartilhados entre outros grupos
 */
@Getter
@Setter
@Entity
@Table(name = "grupo_complemento_produto")
public class GrupoComplemento extends BaseEntity {

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

    
    /**
     * Ordem de apresentação do grupo no fluxo de compra.
     *
     * Regra:
     * - para grupos de categoria, ordena os grupos herdados pelos produtos da categoria
     * - para grupos de produto, ordena os grupos específicos daquele produto
     */
    @Column(nullable = false)
    private Integer ordem = 1;
    
    /**
     * Define a regra padrão mínima do grupo.
     * Exemplo: "Tipo de massa" pode exigir ao menos 1 escolha.
     */
    @Column(name = "minimo_selecoes", nullable = false)
    private Integer minimoSelecoes = 0;

    /**
     * Define a regra padrão máxima do grupo.
     * Exemplo: "Tipo de massa" normalmente permite no máximo 1 escolha.
     */
    @Column(name = "maximo_selecoes", nullable = false)
    private Integer maximoSelecoes = 1;

    /**
     * Grupo inativo não deve ser oferecido para novas associações ou seleção no pedido.
     */
    @Column(nullable = false)
    private boolean ativo = true;
    
    @Column(nullable = false)
    private boolean excluido = false;
    
    
}