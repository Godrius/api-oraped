package br.com.oraped.dto.produto.complemento;

import br.com.oraped.domain.produto.complemento.GrupoComplementoCategoriaProduto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrupoComplementoCategoriaProdutoResponseDTO {

    private Long id;
    private Long idCategoria;
    private String nomeCategoria;
    private Long idGrupo;
    private String nomeGrupo;
    private Integer ordem;
    private boolean ativo;
    private Integer quantidadeComplementos;

    
    public GrupoComplementoCategoriaProdutoResponseDTO(
        GrupoComplementoCategoriaProduto associacao,
        Integer quantidadeComplementos
    ) {
        this.id = associacao.getId();

        this.idCategoria = associacao.getCategoria() == null ? null : associacao.getCategoria().getId();
        this.nomeCategoria = associacao.getCategoria() == null ? null : associacao.getCategoria().getNome();

        this.idGrupo = associacao.getGrupo() == null ? null : associacao.getGrupo().getId();
        this.nomeGrupo = associacao.getGrupo() == null ? null : associacao.getGrupo().getNome();

        this.ordem = associacao.getOrdem();
        this.ativo = associacao.isAtivo();
        this.quantidadeComplementos = quantidadeComplementos == null ? 0 : quantidadeComplementos;
    }
}