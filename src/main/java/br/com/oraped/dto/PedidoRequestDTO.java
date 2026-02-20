// src/main/java/br/com/oraped/dto/PedidoRequestDTO.java
package br.com.oraped.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.enums.TipoAtendimento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PedidoRequestDTO {

    @NotNull
    private Long idEstabelecimento;

    @Valid
    @NotNull
    private ClienteRequestDTO cliente;

    @NotNull
    private TipoAtendimento tipoAtendimento;

    @Size(max = 20)
    private String numeroMesa;

    @Size(max = 2000)
    private String enderecoEntrega;

    @Size(max = 2000)
    private String observacoes;

    private BigDecimal taxaServico;

    private BigDecimal taxaEntrega;

    // =========================================================
    // NOVO: Pagamento (WhatsApp e demais canais podem preencher)
    // =========================================================

    @NotNull
    private FormaPagamentoPedido formaPagamento;

    // Relevante apenas quando formaPagamento == DINHEIRO
    private Boolean precisaTroco;

    // Relevante apenas quando precisaTroco == true
    private BigDecimal trocoPara;

    @Valid
    @NotNull
    @Size(min = 1)
    private List<ItemPedidoRequestDTO> itens = new ArrayList<>();
}