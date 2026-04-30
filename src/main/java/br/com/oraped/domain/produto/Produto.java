package br.com.oraped.domain.produto;

import java.math.BigDecimal;
import java.util.List;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.Estabelecimento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "produto")
public class Produto extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private CategoriaProduto categoria;

    @ManyToOne
    @JoinColumn(name = "marca_id")
    private MarcaProduto marca;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    /**
     * URL pública da foto principal do produto.
     * Usada para exibição visual no cardápio e no carrossel do WhatsApp.
     *
     * Regra:
     * - cada produto possui no máximo 1 foto
     * - null = produto sem foto cadastrada
     */
    @Column(name = "foto_url", length = 500)
    private String urlFoto;

    /**
     * Regra operacional (WhatsApp):
     * - disponivelParaVenda=true  => aparece no cardápio / pode ser pedido
     * - disponivelParaVenda=false => fica oculto / não pode ser pedido
     */
    @Column(nullable = false)
    private boolean disponivelParaVenda = true;
    
    
    /**
     * Grupos de complementos associados ao produto.
     *
     * Regra:
     * - produto sem grupos segue o fluxo atual de pedido
     * - produto com grupos entrará no fluxo guiado de seleção de complementos
     * - a ordem real de exibição fica em ProdutoGrupoComplemento.ordem
     */
    @OneToMany(mappedBy = "produto")
    private List<GrupoComplementoProduto> gruposComplemento;
}