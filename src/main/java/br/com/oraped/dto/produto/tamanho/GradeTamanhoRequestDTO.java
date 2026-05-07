package br.com.oraped.dto.produto.tamanho;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO de entrada para cadastro/edição da grade de tamanhos.
 *
 * Aplicação:
 * - representa a grade reutilizável do estabelecimento
 */
@Getter
@Setter
public class GradeTamanhoRequestDTO {

    private Long idEstabelecimento;
    private String nome;
    private String descricao;
    private Boolean ativo;
}