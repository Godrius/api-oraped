package br.com.oraped.dto.produto.complemento;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrupoComplementoRequestDTO {

    private Long idEstabelecimento;
    private Long idCategoria;
    private Long idProduto;
    private String nome;
    private String descricao;
    private Integer minimoSelecoes;
    private Integer maximoSelecoes;
    private Integer ordem;
    private Boolean ativo;
}