package br.com.oraped.domain.produto;

import java.math.BigDecimal;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.Estabelecimento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    /**
     * Preço padrão do produto.
     *
     * Regra:
     * - usado somente quando não há grade de tamanhos aplicável ao produto
     * - quando há grade aplicável, o preço final vem de OpcaoTamanhoProduto.preco
     */
    @Column(precision = 12, scale = 2)
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
     * Exclusão lógica do produto.
     *
     * Regra:
     * - excluido=false => produto ativo no cadastro
     * - excluido=true  => produto removido logicamente, preservando histórico e integridade
     */
    @Column(nullable = false)
    private boolean excluido = false;

    /**
     * Data/hora em que o produto foi removido logicamente.
     */
    @Column(name = "data_exclusao")
    private java.time.OffsetDateTime dataExclusao;
}