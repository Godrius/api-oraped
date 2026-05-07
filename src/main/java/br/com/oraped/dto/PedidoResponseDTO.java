package br.com.oraped.dto;

import java.math.BigDecimal;

import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.enums.TipoAtendimento;
import br.com.oraped.domain.pedido.Pedido;
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

    private String enderecoEntrega;
    private String observacoes;

    private String cepEntrega;
    private String bairroEntrega;
    private String cidadeEntrega;
    private String ufEntrega;
    private Double latitudeEntrega;
    private Double longitudeEntrega;

    private String statusLabel;
    private String resumoItens;

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

        this.enderecoEntrega = pedido.getEnderecoEntrega();
        this.observacoes = pedido.getObservacoes();

        this.cepEntrega = pedido.getCepEntrega();
        this.bairroEntrega = pedido.getBairroEntrega();
        this.cidadeEntrega = pedido.getCidadeEntrega();
        this.ufEntrega = pedido.getUfEntrega();
        this.latitudeEntrega = pedido.getLatitudeEntrega();
        this.longitudeEntrega = pedido.getLongitudeEntrega();
    }
}