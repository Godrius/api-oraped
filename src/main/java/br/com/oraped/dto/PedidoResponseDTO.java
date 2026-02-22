// src/main/java/br/com/oraped/dto/PedidoResponseDTO.java
package br.com.oraped.dto;

import java.math.BigDecimal;

import br.com.oraped.domain.Pedido;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.enums.TipoAtendimento;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PedidoResponseDTO {

    private Long id;
    private StatusPedido status;
    private TipoAtendimento tipoAtendimento;

    private BigDecimal subtotal;
    private BigDecimal taxaServico;
    private BigDecimal taxaEntrega;
    private BigDecimal total;

    // usados na revisão do pedido (WhatsApp)
    private String statusLabel;
    private String resumoItens;

    /**
     * Construtor de conveniência para mapear entidade -> DTO.
     *
     * Regras:
     * - DTO não executa lógica de negócio
     * - Apenas extrai dados da entidade
     * - Null-safe
     */
    public PedidoResponseDTO(Pedido pedido) {

        if (pedido == null) {
            return;
        }

        this.id = pedido.getId();
        this.status = pedido.getStatus();
        this.tipoAtendimento = pedido.getTipoAtendimento();

        this.subtotal = pedido.getSubtotal();
        this.taxaServico = pedido.getTaxaServico();
        this.taxaEntrega = pedido.getTaxaEntrega();
        this.total = pedido.getTotal();

        // statusLabel/resumoItens serão preenchidos no service (sem regra no DTO)
    }
}