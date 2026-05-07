package br.com.oraped.dto.produto.tamanho;

import br.com.oraped.domain.produto.tamanho.GradeTamanho;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de saída da grade de tamanhos.
 */
@Getter
@Setter
public class GradeTamanhoResponseDTO {

    private Long idGrade;
    private Long idEstabelecimento;
    private String nome;
    private String descricao;
    private boolean ativo;
    private boolean excluido;

    public GradeTamanhoResponseDTO(GradeTamanho grade) {

        if (grade == null) {
            return;
        }

        this.idGrade = grade.getId();

        if (grade.getEstabelecimento() != null) {
            this.idEstabelecimento = grade.getEstabelecimento().getId();
        }

        this.nome = grade.getNome();
        this.descricao = grade.getDescricao();
        this.ativo = grade.isAtivo();
        this.excluido = grade.isExcluido();
    }
}