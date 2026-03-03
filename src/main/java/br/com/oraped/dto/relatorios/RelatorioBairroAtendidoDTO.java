package br.com.oraped.dto.relatorios;

public class RelatorioBairroAtendidoDTO {

    private final String bairro;
    private final Long totalPedidos;

    public RelatorioBairroAtendidoDTO(String bairro, Long totalPedidos) {
        this.bairro = bairro;
        this.totalPedidos = totalPedidos;
    }

    public String getBairro() {
        return bairro;
    }

    public Long getTotalPedidos() {
        return totalPedidos;
    }
}