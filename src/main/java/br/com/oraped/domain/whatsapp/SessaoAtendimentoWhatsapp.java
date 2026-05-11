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
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "sessao_atendimento_whatsapp",
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

    @Column(name = "id_estabelecimento")
    private Long idEstabelecimento;

    @Column(name = "id_marketplace")
    private Long idMarketplace;
    
    @Column(name = "id_categoria_marketplace")
    private Long idCategoriaMarketplace;

    @Column(name = "id_subcategoria_marketplace")
    private Long idSubcategoriaMarketplace;

    @Column(name = "latitude_origem_cliente")
    private Double latitudeOrigemCliente;

    @Column(name = "longitude_origem_cliente")
    private Double longitudeOrigemCliente;
    
    @Column(name = "ultima_interacao_em", nullable = false)
    private OffsetDateTime ultimaInteracaoEm;

    @Column(name = "encerrada_em")
    private OffsetDateTime encerradaEm;
    
    // =========================================================
    // FLUXO CLIENTE — Pedido em andamento
    // =========================================================

    @Column(name = "aguardando", length = 40)
    private String aguardando;

    @Column(name = "endereco_entrega", length = 2000)
    private String enderecoEntrega;

    @Column(name = "observacoes_entrega", length = 2000)
    private String observacoesEntrega;

    // =========================================================
    // CLIENTE — Quantidade manual (digitação)
    // =========================================================

    @Column(name = "aguardando_quantidade_manual", nullable = false)
    private Boolean aguardandoQuantidadeManual;

    @Column(name = "id_produto_quantidade_manual")
    private Long idProdutoQuantidadeManual;
    
    // =========================================================
    // Endereço estruturado (cliente)
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

    @Column(name = "taxa_entrega_calculada", precision = 10, scale = 2)
    private BigDecimal taxaEntregaCalculada;

    @Column(name = "endereco_base_resolvido", length = 600)
    private String enderecoBaseResolvido;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamentoPedido formaPagamento;

    @Column(name = "precisa_troco")
    private Boolean precisaTroco;

    @Column(name = "troco_para", precision = 10, scale = 2)
    private BigDecimal trocoPara;

    // =========================================================
    // ADMIN — Produto (cadastro guiado)
    // =========================================================
    @Column(name = "cadastro_guiado_produto")
    private Boolean cadastroGuiadoProduto;
    
    // =========================================================
    // ADMIN — Produto (Preço por digitação)
    // =========================================================

    @Column(name = "aguardando_novo_preco", nullable = false)
    private Boolean aguardandoNovoPreco;

    @Column(name = "id_produto_novo_preco")
    private Long idProdutoNovoPreco;

    @Column(name = "offset_lista_novo_preco")
    private Integer offsetListaNovoPreco;

    @Column(name = "id_categoria_novo_preco")
    private Long idCategoriaNovoPreco;
    
    // =========================================================
    // ADMIN — Produto (Nome por digitação)
    // =========================================================

    @Column(name = "aguardando_novo_nome_produto", nullable = false)
    private Boolean aguardandoNovoNomeProduto;

    @Column(name = "id_produto_novo_nome")
    private Long idProdutoNovoNome;

    @Column(name = "offset_lista_novo_nome")
    private Integer offsetListaNovoNome;

    @Column(name = "id_categoria_novo_nome")
    private Long idCategoriaNovoNome;
    
    // =========================================================
    // ADMIN — Produto (Descrição por digitação)
    // =========================================================

    @Column(name = "aguardando_nova_descricao_produto", nullable = false)
    private Boolean aguardandoNovaDescricaoProduto;

    @Column(name = "id_produto_nova_descricao")
    private Long idProdutoNovaDescricao;

    @Column(name = "offset_lista_nova_descricao")
    private Integer offsetListaNovaDescricao;

    @Column(name = "id_categoria_nova_descricao")
    private Long idCategoriaNovaDescricao;
    
    // =========================================================
    // ADMIN — Produto (Foto por envio de imagem)
    // =========================================================

    @Column(name = "aguardando_nova_foto_produto", nullable = false)
    private Boolean aguardandoNovaFotoProduto;

    @Column(name = "id_produto_nova_foto")
    private Long idProdutoNovaFoto;

    @Column(name = "offset_lista_nova_foto")
    private Integer offsetListaNovaFoto;
    
    @Column(name = "id_categoria_nova_foto")
    private Long idCategoriaNovaFoto;
    
    
    // =========================================================
    // ADMIN — Categoria de produto (Criar por digitação)
    // =========================================================

    @Column(name = "aguardando_nova_categoria_produto", nullable = false)
    private Boolean aguardandoNovaCategoria = false;

    @Column(name = "offset_lista_nova_categoria")
    private Integer offsetListaNovaCategoria = 0;

    // =========================================================
    // ADMIN — Produto (Criar por digitação)
    // =========================================================

    @Column(name = "aguardando_novo_produto", nullable = false)
    private Boolean aguardandoNovoProduto = false;

    @Column(name = "id_categoria_novo_produto")
    private Long idCategoriaNovoProduto;

    @Column(name = "offset_lista_novo_produto")
    private Integer offsetListaNovoProduto = 0;
    
    
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

    // =========================================================
    // ADMIN — cep (Editar por digitação)
    // =========================================================
    @Column(nullable = false)
    private boolean aguardandoCepEstabelecimento = false;

    // =========================================================
    // ADMIN — Taxa por bairro (digitação)
    // =========================================================

    @Column(name = "aguardando_taxa_entrega_bairro", nullable = false)
    private Boolean aguardandoTaxaEntregaBairro;

    @Column(name = "id_bairro_taxa_entrega")
    private Long idBairroTaxaEntrega;

    @Column(name = "offset_lista_taxa_entrega_bairro")
    private Integer offsetListaTaxaEntregaBairro;

    // =========================================================
    // ADMIN — Taxa padrão (digitação)
    // =========================================================
    @Column(name = "aguardando_taxa_entrega_padrao", nullable = false)
    private Boolean aguardandoTaxaEntregaPadrao;

    @Column(name = "offset_lista_taxa_padrao_voltar")
    private Integer offsetListaTaxaPadraoVoltar;

    
	// =========================================================
	// ADMIN — Bairros atendidos por digitação
	// =========================================================

    @Column(name = "aguardando_bairros_atendidos", nullable = false)
    private Boolean aguardandoBairrosAtendidos;
    
	// =========================================================
	// ADMIN — Grupo de complementos (Criar por digitação)
	// =========================================================
	
	@Column(name = "aguardando_novo_grupo_complemento", nullable = false)
	private boolean aguardandoNovoGrupoComplemento = false;
	
	@Column(name = "offset_novo_grupo_complemento")
	private Integer offsetNovoGrupoComplemento = 0;
	
	// =========================================================
	// ADMIN — Grupo de complementos (Editar nome por digitação)
	// =========================================================
	
	@Column(name = "aguardando_editar_nome_grupo_complemento", nullable = false)
	private boolean aguardandoEditarNomeGrupoComplemento = false;
	
	@Column(name = "id_grupo_complemento_editar_nome")
	private Long idGrupoComplementoEditarNome;
	
	@Column(name = "offset_editar_nome_grupo_complemento")
	private Integer offsetEditarNomeGrupoComplemento = 0;
	
	// =========================================================
	// ADMIN — Grupo de complementos (Editar descrição por digitação)
	// =========================================================
	
	@Column(name = "aguardando_editar_descricao_grupo_complemento", nullable = false)
	private boolean aguardandoEditarDescricaoGrupoComplemento = false;
	
	@Column(name = "id_grupo_complemento_editar_descricao")
	private Long idGrupoComplementoEditarDescricao;
	
	@Column(name = "offset_editar_descricao_grupo_complemento")
	private Integer offsetEditarDescricaoGrupoComplemento = 0;
	 
	// =========================================================
	// ADMIN — Complemento de grupo (Criar por digitação)
	// =========================================================

	@Column(name = "aguardando_novo_complemento_grupo", nullable = false)
	private boolean aguardandoNovoComplementoGrupo = false;

	@Column(name = "id_grupo_novo_complemento")
	private Long idGrupoNovoComplemento;

	@Column(name = "offset_novo_complemento_grupo")
	private Integer offsetNovoComplementoGrupo = 0;
	
	// =========================================================
	// ADMIN — Complemento de grupo (Editar nome por digitação)
	// =========================================================

	@Column(name = "aguardando_editar_nome_complemento_global", nullable = false)
	private boolean aguardandoEditarNomeComplementoGlobal = false;

	@Column(name = "id_grupo_editar_nome_complemento_global")
	private Long idGrupoEditarNomeComplementoGlobal;

	@Column(name = "id_complemento_editar_nome_global")
	private Long idComplementoEditarNomeGlobal;

	@Column(name = "offset_editar_nome_complemento_global")
	private Integer offsetEditarNomeComplementoGlobal = 0;
	
    // =========================================================
    // ADMIN — Preços dos complementos
    // =========================================================
    		
    /**
    * Controle temporário para alteração de preço de complemento pelo admin.
    *
    * Aplicação:
    * - usado quando o admin escolhe "Outro valor" no menu do complemento
    * - preserva o contexto necessário para retornar ao grupo/produto após a digitação
    */
    
    @Column(name = "aguardando_novo_preco_complemento", nullable = false)
    private boolean aguardandoNovoPrecoComplemento = false;

    @Column(name = "id_produto_novo_preco_complemento")
    private Long idProdutoNovoPrecoComplemento;

    @Column(name = "id_categoria_novo_preco_complemento")
    private Long idCategoriaNovoPrecoComplemento;

    @Column(name = "id_grupo_novo_preco_complemento")
    private Long idGrupoNovoPrecoComplemento;

    @Column(name = "id_complemento_novo_preco")
    private Long idComplementoNovoPreco;

    @Column(name = "offset_lista_produto_novo_preco_complemento")
    private Integer offsetListaProdutoNovoPrecoComplemento = 0;
    
    // =========================================================
	// ADMIN — Complemento de produto (cadastro guiado)
	// =========================================================
	
	@Column(name = "aguardando_novo_complemento_produto", nullable = false)
	private boolean aguardandoNovoComplementoProduto = false;
	
	@Column(name = "etapa_novo_complemento_produto", length = 30)
	private String etapaNovoComplementoProduto;
	
	@Column(name = "id_produto_novo_complemento_produto")
	private Long idProdutoNovoComplementoProduto;
	
	@Column(name = "id_categoria_novo_complemento_produto")
	private Long idCategoriaNovoComplementoProduto;
	
	@Column(name = "id_grupo_novo_complemento_produto")
	private Long idGrupoNovoComplementoProduto;
	
	@Column(name = "offset_lista_produto_novo_complemento")
	private Integer offsetListaProdutoNovoComplemento = 0;
	
	@Column(name = "nome_novo_complemento_produto", length = 120)
	private String nomeNovoComplementoProduto;
	
	@Column(name = "descricao_novo_complemento_produto", length = 600)
	private String descricaoNovoComplementoProduto;
	
	@Column(name = "preco_novo_complemento_produto", precision = 10, scale = 2)
	private BigDecimal precoNovoComplementoProduto;
	
	@Column(name = "minimo_novo_complemento_produto")
	private Integer minimoNovoComplementoProduto;
	
	@Column(name = "maximo_novo_complemento_produto")
	private Integer maximoNovoComplementoProduto;
	 
	// =========================================================
	// ADMIN — Complemento de categoria (cadastro guiado)
	// =========================================================

	@Column(name = "aguardando_novo_complemento_categoria", nullable = false)
	private boolean aguardandoNovoComplementoCategoria = false;

	@Column(name = "etapa_novo_complemento_categoria", length = 30)
	private String etapaNovoComplementoCategoria;

	@Column(name = "id_categoria_novo_complemento_categoria")
	private Long idCategoriaNovoComplementoCategoria;

	@Column(name = "id_grupo_novo_complemento_categoria")
	private Long idGrupoNovoComplementoCategoria;

	@Column(name = "offset_lista_categoria_novo_complemento")
	private Integer offsetListaCategoriaNovoComplemento = 0;

	@Column(name = "nome_novo_complemento_categoria", length = 120)
	private String nomeNovoComplementoCategoria;

	@Column(name = "descricao_novo_complemento_categoria", length = 600)
	private String descricaoNovoComplementoCategoria;

	@Column(name = "preco_novo_complemento_categoria", precision = 10, scale = 2)
	private BigDecimal precoNovoComplementoCategoria;

	@Column(name = "minimo_novo_complemento_categoria")
	private Integer minimoNovoComplementoCategoria;

	@Column(name = "maximo_novo_complemento_categoria")
	private Integer maximoNovoComplementoCategoria;
	
	// =========================================================
	// ADMIN — Opção de tamanho (Criar por digitação)
	// =========================================================
	
	@Column(name = "aguardando_nova_opcao_tamanho", nullable = false)
	private Boolean aguardandoNovaOpcaoTamanho = false;
	
	@Column(name = "id_categoria_nova_opcao_tamanho")
	private Long idCategoriaNovaOpcaoTamanho;
	
	@Column(name = "offset_produtos_nova_opcao_tamanho")
	private Integer offsetProdutosNovaOpcaoTamanho = 0;
	 
	// =========================================================
	// ADMIN — Opção de tamanho do produto (Criar por digitação)
	// =========================================================

	@Column(name = "aguardando_novo_tamanho_produto", nullable = false)
	private Boolean aguardandoNovoTamanhoProduto = false;

	@Column(name = "id_produto_novo_tamanho_produto")
	private Long idProdutoNovoTamanhoProduto;

	@Column(name = "id_categoria_novo_tamanho_produto")
	private Long idCategoriaNovoTamanhoProduto;

	@Column(name = "offset_lista_novo_tamanho_produto")
	private Integer offsetListaNovoTamanhoProduto = 0;
	
	// =========================================================
	// ADMIN — Tamanho do produto (Nome por digitação)
	// =========================================================

	@Column(name = "aguardando_novo_nome_tamanho_produto", nullable = false)
	private Boolean aguardandoNovoNomeTamanhoProduto = false;

	@Column(name = "id_produto_novo_nome_tamanho_produto")
	private Long idProdutoNovoNomeTamanhoProduto;

	@Column(name = "id_categoria_novo_nome_tamanho_produto")
	private Long idCategoriaNovoNomeTamanhoProduto;

	@Column(name = "id_opcao_tamanho_novo_nome_produto")
	private Long idOpcaoTamanhoNovoNomeProduto;

	@Column(name = "offset_lista_novo_nome_tamanho_produto")
	private Integer offsetListaNovoNomeTamanhoProduto = 0;
	
	// =========================================================
	// ADMIN — Opção de tamanho (Descrição por digitação)
	// =========================================================

	@Column(name = "aguardando_descricao_opcao_tamanho", nullable = false)
	private Boolean aguardandoDescricaoOpcaoTamanho = false;

	@Column(name = "id_opcao_tamanho_nova_descricao")
	private Long idOpcaoTamanhoNovaDescricao;

	@Column(name = "id_categoria_opcao_tamanho_nova_descricao")
	private Long idCategoriaOpcaoTamanhoNovaDescricao;

	@Column(name = "offset_produtos_opcao_tamanho_nova_descricao")
	private Integer offsetProdutosOpcaoTamanhoNovaDescricao = 0;
	
	// =========================================================
	// ADMIN — Produto x Opção de tamanho (Preço por digitação)
	// =========================================================

	@Column(name = "aguardando_novo_preco_produto_tamanho", nullable = false)
	private Boolean aguardandoNovoPrecoProdutoTamanho = false;

	@Column(name = "id_produto_novo_preco_tamanho")
	private Long idProdutoNovoPrecoTamanho;

	@Column(name = "id_categoria_novo_preco_tamanho")
	private Long idCategoriaNovoPrecoTamanho;

	@Column(name = "id_opcao_tamanho_produto_novo_preco")
	private Long idOpcaoTamanhoProdutoNovoPreco;

	@Column(name = "offset_lista_novo_preco_tamanho")
	private Integer offsetListaNovoPrecoTamanho = 0;
	
	
	// =========================================================
	// CLIENTE — Carrinho em montagem
	// =========================================================

	@Column(name = "id_produto_item_em_montagem")
	private Long idProdutoItemEmMontagem;

	@Column(name = "id_categoria_item_em_montagem")
	private Long idCategoriaItemEmMontagem;

	@Column(name = "quantidade_multipla_item_em_montagem")
	private Integer quantidadeMultiplaItemEmMontagem;

	@Column(name = "ordem_grupo_complemento_item_em_montagem")
	private Integer ordemGrupoComplementoItemEmMontagem;

	@Column(name = "id_opcao_tamanho_produto_item_em_montagem")
	private Long idOpcaoTamanhoProdutoItemEmMontagem;

	@Column(name = "id_opcao_tamanho_item_em_montagem")
	private Long idOpcaoTamanhoItemEmMontagem;

	@Column(name = "nome_tamanho_item_em_montagem", length = 120)
	private String nomeTamanhoItemEmMontagem;

	@Column(name = "preco_tamanho_item_em_montagem", precision = 12, scale = 2)
	private BigDecimal precoTamanhoItemEmMontagem;
    
    
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

        if (aguardandoNovaFotoProduto == null) {
            aguardandoNovaFotoProduto = false;
        }
        
        if (aguardandoNovaMarca == null) {
            aguardandoNovaMarca = false;
        }

        if (aguardandoEditarMarcaNome == null) {
            aguardandoEditarMarcaNome = false;
        }

        if (aguardandoTaxaEntregaBairro == null) {
            aguardandoTaxaEntregaBairro = false;
        }

        if (aguardandoTaxaEntregaPadrao == null) {
            aguardandoTaxaEntregaPadrao = false;
        }
        
        if (aguardandoQuantidadeManual == null) {
            aguardandoQuantidadeManual = false;
        }
        
        if (aguardandoBairrosAtendidos == null) {
            aguardandoBairrosAtendidos = false;
        }
        
        if (aguardandoNovaOpcaoTamanho == null) {
            aguardandoNovaOpcaoTamanho = false;
        }
        
        if (aguardandoNovoTamanhoProduto == null) {
            aguardandoNovoTamanhoProduto = false;
        }

        if (offsetListaNovoTamanhoProduto == null) {
            offsetListaNovoTamanhoProduto = 0;
        }
        
        if (aguardandoNovoPrecoProdutoTamanho == null) {
            aguardandoNovoPrecoProdutoTamanho = false;
        }
        if (aguardandoNovoNomeTamanhoProduto == null) {
            aguardandoNovoNomeTamanhoProduto = false;
        }

        if (offsetListaNovoNomeTamanhoProduto == null) {
            offsetListaNovoNomeTamanhoProduto = 0;
        }
        
        if (aguardandoDescricaoOpcaoTamanho == null) {
            aguardandoDescricaoOpcaoTamanho = false;
        }
        
        if (offsetListaProdutoNovoComplemento == null) {
            offsetListaProdutoNovoComplemento = 0;
        }
        
        if (offsetListaCategoriaNovoComplemento == null) {
            offsetListaCategoriaNovoComplemento = 0;
        }
        
        
    }

    @PreUpdate
    public void preUpdate() {
        ultimaInteracaoEm = OffsetDateTime.now();
    }
}