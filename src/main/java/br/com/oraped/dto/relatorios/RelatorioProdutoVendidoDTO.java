package br.com.oraped.dto.relatorios;

public class RelatorioProdutoVendidoDTO {

    private final Long idProduto;
    private final String nomeProduto;
    private final Long quantidadeVendida;

    public RelatorioProdutoVendidoDTO(Long idProduto, String nomeProduto, Long quantidadeVendida) {
        this.idProduto = idProduto;
        this.nomeProduto = nomeProduto;
        this.quantidadeVendida = quantidadeVendida;
    }

    public Long getIdProduto() {
        return idProduto;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public Long getQuantidadeVendida() {
        return quantidadeVendida;
    }
}