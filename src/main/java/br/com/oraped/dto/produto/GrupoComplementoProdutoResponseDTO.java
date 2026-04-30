package br.com.oraped.dto.produto;

import br.com.oraped.domain.produto.GrupoComplementoProduto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrupoComplementoProdutoResponseDTO {

    private Long id;
    private Long idProduto;
    private Long idGrupo;
    private String nomeGrupo;
    private Integer ordem;
    private boolean ativo;

    public GrupoComplementoProdutoResponseDTO(GrupoComplementoProduto associacao) {
        this.id = associacao.getId();
        this.idProduto = associacao.getProduto() == null ? null : associacao.getProduto().getId();
        this.idGrupo = associacao.getGrupo() == null ? null : associacao.getGrupo().getId();
        this.nomeGrupo = associacao.getGrupo() == null ? null : associacao.getGrupo().getNome();
        this.ordem = associacao.getOrdem();
        this.ativo = associacao.isAtivo();
    }
}