package br.com.oraped.dto.produto.tamanho;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO de entrada para definir o preço de uma opção de tamanho em um produto.
 *
 * Aplicação:
 * - usado quando o lojista define quanto cada tamanho custa para um produto específico
 *
 * Regra:
 * - o preço é final, não adicional
 */
@Getter
@Setter
public class OpcaoTamanhoProdutoRequestDTO {

    private Long idProduto;
    private Long idOpcaoTamanho;
    private BigDecimal preco;
    private Boolean ativo;

    public OpcaoTamanhoProdutoRequestDTO() {
    }

    public OpcaoTamanhoProdutoRequestDTO(
        Long idProduto,
        Long idOpcaoTamanho,
        BigDecimal preco,
        Boolean ativo
    ) {
        this.idProduto = idProduto;
        this.idOpcaoTamanho = idOpcaoTamanho;
        this.preco = preco;
        this.ativo = ativo;
    }
}