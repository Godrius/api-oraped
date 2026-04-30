package br.com.oraped.domain.produto;

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
 * Representa um grupo reutilizável de complementos de produto.
 *
 * Aplicação:
 * - usado para organizar opções como "Tipo de massa", "Borda", "Recheio da borda" e "Molho extra"
 * - pertence ao estabelecimento para permitir reaproveitamento em vários produtos do mesmo cardápio
 * - será associado aos produtos por ProdutoGrupoComplemento, onde a ordem do fluxo será definida
 */
@Getter
@Setter
@Entity
@Table(name = "grupo_complemento_produto")
public class GrupoComplemento extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

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