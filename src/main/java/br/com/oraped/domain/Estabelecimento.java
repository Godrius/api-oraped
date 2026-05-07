package br.com.oraped.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import br.com.oraped.domain.enums.AbrangenciaEntrega;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.geolocalizacao.EstabelecimentoBairroAtendido;
import br.com.oraped.domain.marketplace.CategoriaMarketplace;
import br.com.oraped.domain.marketplace.EstabelecimentoSubcategoriaMarketplace;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Finalidade:
 * Representar o estabelecimento participante do ecossistema OraPed, incluindo
 * dados operacionais, cobertura de entrega e classificação no marketplace.
 *
 * Aplicação:
 * Utilizado nos fluxos de atendimento, discovery do marketplace, catálogo,
 * configuração administrativa e cálculo de entrega.
 *
 * Utilização:
 * Deve concentrar as informações estruturais do estabelecimento, permitindo
 * que os services consultem disponibilidade, categoria, abrangência e regras
 * comerciais sem duplicar dados em outras entidades.
 */
@Getter
@Setter
@Entity
@Table(name = "estabelecimento")
public class Estabelecimento extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private boolean aberto = true;

    @Column(length = 40)
    private String timezone;

    @Column(length = 30)
    private String whatsapp;

    @Column(columnDefinition = "TEXT")
    private String endereco;

    @Column(length = 9)
    private String cep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bairro_id")
    private Bairro bairro;

    @Column(columnDefinition = "TEXT")
    private String configuracoesJson;

    // Define o nível geográfico da entrega do estabelecimento.
    // O valor padrão BAIRRO preserva a compatibilidade com a lógica atual já baseada em bairros.
    @Enumerated(EnumType.STRING)
    @Column(name = "abrangencia_entrega", nullable = false, length = 20)
    private AbrangenciaEntrega abrangenciaEntrega = AbrangenciaEntrega.BAIRRO;

    // =========================
    // Taxa padrão de entrega
    // =========================
    @Column(name = "taxa_entrega_padrao", precision = 10, scale = 2)
    private BigDecimal taxaEntregaPadrao;

    // Valor mínimo aceito para pedidos do estabelecimento.
    // Esse campo será exibido no discovery do marketplace para orientar o cliente.
    @Column(name = "valor_pedido_minimo", precision = 10, scale = 2)
    private BigDecimal valorPedidoMinimo;
        
    // Classificação principal do estabelecimento no marketplace.
    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_marketplace_id", nullable = false)
    private CategoriaMarketplace categoriaMarketplace;

    // Um estabelecimento pode participar de várias subcategorias da sua categoria principal.
    @JsonIgnore
    @OneToMany(mappedBy = "estabelecimento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EstabelecimentoSubcategoriaMarketplace> subcategoriasMarketplace = new ArrayList<>();

    // Para abrangência BAIRRO, esta lista representa os bairros realmente cobertos pelo negócio.
    // Ela é diferente da vizinhança geográfica sugerida automaticamente pelo sistema.
    @JsonIgnore
    @OneToMany(mappedBy = "estabelecimento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EstabelecimentoBairroAtendido> bairrosAtendidos = new ArrayList<>();
}