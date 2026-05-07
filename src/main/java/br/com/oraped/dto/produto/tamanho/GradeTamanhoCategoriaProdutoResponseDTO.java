package br.com.oraped.dto.produto.tamanho;

import br.com.oraped.domain.produto.tamanho.GradeTamanhoCategoriaProduto;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de saída da associação entre categoria e grade de tamanhos.
 *
 * Regra:
 * - a categoria define quais tamanhos estão disponíveis
 * - os preços são definidos posteriormente por produto
 */
@Getter
@Setter
public class GradeTamanhoCategoriaProdutoResponseDTO {

    private Long idAssociacao;
    private Long idCategoria;
    private String nomeCategoria;
    private Long idGrade;
    private String nomeGrade;
    private boolean ativo;
    private Integer quantidadeOpcoes;

    public GradeTamanhoCategoriaProdutoResponseDTO(
        GradeTamanhoCategoriaProduto associacao,
        Integer quantidadeOpcoes
    ) {

        if (associacao == null) {
            return;
        }

        this.idAssociacao = associacao.getId();

        if (associacao.getCategoria() != null) {
            this.idCategoria = associacao.getCategoria().getId();
            this.nomeCategoria = associacao.getCategoria().getNome();
        }

        if (associacao.getGrade() != null) {
            this.idGrade = associacao.getGrade().getId();
            this.nomeGrade = associacao.getGrade().getNome();
        }

        this.ativo = associacao.isAtivo();
        this.quantidadeOpcoes = quantidadeOpcoes == null ? 0 : quantidadeOpcoes;
    }
}