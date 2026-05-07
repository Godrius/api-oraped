package br.com.oraped.dto.produto.tamanho;

import java.math.BigDecimal;

import br.com.oraped.domain.produto.tamanho.OpcaoTamanhoProduto;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de saída para o preço de uma opção de tamanho em um produto.
 *
 * Aplicação:
 * - mostra quanto um tamanho custa em um produto específico
 * - usado no fluxo administrativo do cardápio via WhatsApp
 *
 * Regra:
 * - o preço é final, não adicional
 */
@Getter
@Setter
public class OpcaoTamanhoProdutoResponseDTO {

    private Long idOpcaoTamanhoProduto;
    private Long idProduto;
    private String nomeProduto;
    private Long idOpcaoTamanho;
    private String nomeOpcaoTamanho;
    private BigDecimal preco;
    private Boolean ativo;

    public OpcaoTamanhoProdutoResponseDTO() {
    }

    public OpcaoTamanhoProdutoResponseDTO(OpcaoTamanhoProduto relacao) {

        if (relacao == null) {
            return;
        }

        this.idOpcaoTamanhoProduto = relacao.getId();

        if (relacao.getProduto() != null) {
            this.idProduto = relacao.getProduto().getId();
            this.nomeProduto = relacao.getProduto().getNome();
        }

        if (relacao.getOpcaoTamanho() != null) {
            this.idOpcaoTamanho = relacao.getOpcaoTamanho().getId();
            this.nomeOpcaoTamanho = relacao.getOpcaoTamanho().getNome();
        }

        this.preco = relacao.getPreco();
        this.ativo = relacao.isAtivo();
    }
}