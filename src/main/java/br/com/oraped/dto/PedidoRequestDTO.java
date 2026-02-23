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

    // =========================================================
    // NOVO: Endereço estruturado
    // =========================================================

    @Size(max = 8)
    private String cepEntrega;

    @Size(max = 120)
    private String bairroEntrega;

    @Size(max = 120)
    private String cidadeEntrega;

    @Size(max = 2)
    private String ufEntrega;

    private Double latitudeEntrega;

    private Double longitudeEntrega;

    private BigDecimal taxaServico;

    private BigDecimal taxaEntrega;

    // =========================================================
    // Pagamento
    // =========================================================

    @NotNull
    private FormaPagamentoPedido formaPagamento;

    private Boolean precisaTroco;

    private BigDecimal trocoPara;

    @Valid
    @NotNull
    @Size(min = 1)
    private List<ItemPedidoRequestDTO> itens = new ArrayList<>();
}