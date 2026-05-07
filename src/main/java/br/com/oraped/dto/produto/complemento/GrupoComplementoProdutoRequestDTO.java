package br.com.oraped.dto.produto.complemento;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrupoComplementoProdutoRequestDTO {

    private Long idProduto;
    private Long idGrupo;
    private Integer ordem;
    private Boolean ativo;
}