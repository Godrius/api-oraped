package br.com.oraped.dto.produto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrupoComplementoRequestDTO {

    private Long idEstabelecimento;
    private String nome;
    private String descricao;
    private Integer minimoSelecoes;
    private Integer maximoSelecoes;
    private Boolean ativo;
}