package br.com.oraped.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.enums.TipoAtendimento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pedido")
public class Pedido extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAtendimento tipoAtendimento;

    @Column(length = 30)
    private String clienteTelefone;

    @Column(length = 120)
    private String clienteNome;

    @Column(length = 20)
    private String numeroMesa;

    @Column(columnDefinition = "TEXT")
    private String enderecoEntrega;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    // =========================================================
    // NOVO: Endereço estruturado
    // =========================================================

    @Column(name = "cep_entrega", length = 8)
    private String cepEntrega;

    @Column(name = "bairro_entrega", length = 120)
    private String bairroEntrega;

    @Column(name = "cidade_entrega", length = 120)
    private String cidadeEntrega;

    @Column(name = "uf_entrega", length = 2)
    private String ufEntrega;

    @Column(name = "latitude_entrega")
    private Double latitudeEntrega;

    @Column(name = "longitude_entrega")
    private Double longitudeEntrega;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxaServico = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxaEntrega = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> itens = new ArrayList<>();
}