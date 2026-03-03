package br.com.oraped.dto.relatorios;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RelatorioResumoDTO {

    private Long totalPedidosAtendidos;
    private BigDecimal volumeFinanceiroTotal;
    private Long totalClientesNovos;
    private BigDecimal ticketMedio;

    private List<RelatorioProdutoVendidoDTO> top3ProdutosMaisVendidos;
    private List<RelatorioBairroAtendidoDTO> bairrosAtendidos;

    public RelatorioResumoDTO() {
        this.totalPedidosAtendidos = 0L;
        this.volumeFinanceiroTotal = BigDecimal.ZERO;
        this.totalClientesNovos = 0L;
        this.ticketMedio = BigDecimal.ZERO;
        this.top3ProdutosMaisVendidos = new ArrayList<>();
        this.bairrosAtendidos = new ArrayList<>();
    }

    public Long getTotalPedidosAtendidos() {
        return totalPedidosAtendidos;
    }

    public void setTotalPedidosAtendidos(Long totalPedidosAtendidos) {
        this.totalPedidosAtendidos = totalPedidosAtendidos == null ? 0L : totalPedidosAtendidos;
    }

    public BigDecimal getVolumeFinanceiroTotal() {
        return volumeFinanceiroTotal;
    }

    public void setVolumeFinanceiroTotal(BigDecimal volumeFinanceiroTotal) {
        this.volumeFinanceiroTotal = volumeFinanceiroTotal == null ? BigDecimal.ZERO : volumeFinanceiroTotal;
    }

    public Long getTotalClientesNovos() {
        return totalClientesNovos;
    }

    public void setTotalClientesNovos(Long totalClientesNovos) {
        this.totalClientesNovos = totalClientesNovos == null ? 0L : totalClientesNovos;
    }

    public BigDecimal getTicketMedio() {
        return ticketMedio;
    }

    public void setTicketMedio(BigDecimal ticketMedio) {
        this.ticketMedio = ticketMedio == null ? BigDecimal.ZERO : ticketMedio;
    }

    public List<RelatorioProdutoVendidoDTO> getTop3ProdutosMaisVendidos() {
        return top3ProdutosMaisVendidos;
    }

    public void setTop3ProdutosMaisVendidos(List<RelatorioProdutoVendidoDTO> top3ProdutosMaisVendidos) {
        this.top3ProdutosMaisVendidos = top3ProdutosMaisVendidos == null ? new ArrayList<>() : top3ProdutosMaisVendidos;
    }

    public List<RelatorioBairroAtendidoDTO> getBairrosAtendidos() {
        return bairrosAtendidos;
    }

    public void setBairrosAtendidos(List<RelatorioBairroAtendidoDTO> bairrosAtendidos) {
        this.bairrosAtendidos = bairrosAtendidos == null ? new ArrayList<>() : bairrosAtendidos;
    }
}