// src/main/java/br/com/oraped/domain/whatsapp/SessaoAtendimentoWhatsapp.java
package br.com.oraped.domain.whatsapp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import br.com.oraped.domain.enums.FormaPagamentoPedido;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
  name = "sessao_atendimento_whatsapp",
  uniqueConstraints = {
    @UniqueConstraint(
      name = "uk_sessao_whatsapp_cliente_receptor",
      columnNames = {"whatsapp_cliente", "whatsapp_receptor"}
    )
  },
  indexes = {
    @Index(name = "idx_sessao_whatsapp_cliente", columnList = "whatsapp_cliente"),
    @Index(name = "idx_sessao_whatsapp_receptor", columnList = "whatsapp_receptor")
  }
)
public class SessaoAtendimentoWhatsapp {

	// =========================================================
    // IDENTIFICAÇÃO
    // =========================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "whatsapp_cliente", nullable = false, length = 30)
    private String whatsappCliente;

    @Column(name = "whatsapp_receptor", nullable = false, length = 30)
    private String whatsappReceptor;

    @Column(name = "id_estabelecimento", nullable = false)
    private Long idEstabelecimento;

    @Column(name = "ultima_interacao_em", nullable = false)
    private OffsetDateTime ultimaInteracaoEm;

    // =========================================================
    // FLUXO CLIENTE — Pedido em andamento
    // =========================================================

    @Column(name = "aguardando", length = 40)
    private String aguardando;

    @Column(name = "endereco_entrega", length = 2000)
    private String enderecoEntrega;

    @Column(name = "observacoes_entrega", length = 2000)
    private String observacoesEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamentoPedido formaPagamento;

    @Column(name = "precisa_troco")
    private Boolean precisaTroco;

    @Column(name = "troco_para", precision = 10, scale = 2)
    private BigDecimal trocoPara;

    // =========================================================
    // ADMIN — Produto (Preço por digitação)
    // =========================================================

    @Column(name = "aguardando_novo_preco", nullable = false)
    private Boolean aguardandoNovoPreco;

    @Column(name = "id_produto_novo_preco")
    private Long idProdutoNovoPreco;

    @Column(name = "offset_lista_novo_preco")
    private Integer offsetListaNovoPreco;

    // =========================================================
    // ADMIN — Produto (Nome por digitação)
    // =========================================================

    @Column(name = "aguardando_novo_nome_produto", nullable = false)
    private Boolean aguardandoNovoNomeProduto;

    @Column(name = "id_produto_novo_nome")
    private Long idProdutoNovoNome;

    @Column(name = "offset_lista_novo_nome")
    private Integer offsetListaNovoNome;

    // =========================================================
    // ADMIN — Produto (Descrição por digitação)
    // =========================================================

    @Column(name = "aguardando_nova_descricao_produto", nullable = false)
    private Boolean aguardandoNovaDescricaoProduto;

    @Column(name = "id_produto_nova_descricao")
    private Long idProdutoNovaDescricao;

    @Column(name = "offset_lista_nova_descricao")
    private Integer offsetListaNovaDescricao;

    // =========================================================
    // ADMIN — Marca (Criar por digitação)
    // =========================================================

    @Column(name = "aguardando_nova_marca", nullable = false)
    private Boolean aguardandoNovaMarca;

    @Column(name = "offset_lista_marcas_nova")
    private Integer offsetListaMarcasNova;

    // =========================================================
    // ADMIN — Marca (Editar nome por digitação)
    // =========================================================

    @Column(name = "aguardando_editar_marca_nome", nullable = false)
    private Boolean aguardandoEditarMarcaNome;

    @Column(name = "id_marca_editar_nome")
    private Long idMarcaEditarNome;

    @Column(name = "offset_lista_marcas_editar_nome")
    private Integer offsetListaMarcasEditarNome;

  
  
    @PrePersist
    public void prePersist() {

        if (ultimaInteracaoEm == null) {
            ultimaInteracaoEm = OffsetDateTime.now();
        }

        if (aguardandoNovoPreco == null) {
            aguardandoNovoPreco = false;
        }

        if (aguardandoNovoNomeProduto == null) {
            aguardandoNovoNomeProduto = false;
        }

        if (aguardandoNovaDescricaoProduto == null) {
            aguardandoNovaDescricaoProduto = false;
        }

        if (aguardandoNovaMarca == null) {
            aguardandoNovaMarca = false;
        }

        if (aguardandoEditarMarcaNome == null) {
            aguardandoEditarMarcaNome = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
    	ultimaInteracaoEm = OffsetDateTime.now();
    }
}