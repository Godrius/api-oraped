package br.com.oraped.dto.produto.complemento;

import br.com.oraped.domain.produto.complemento.GrupoComplemento;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrupoComplementoResponseDTO {

    private Long id;
    private Long idEstabelecimento;
    private String nome;
    private String descricao;
    private Integer minimoSelecoes;
    private Integer maximoSelecoes;
    private boolean ativo;

    public GrupoComplementoResponseDTO(GrupoComplemento grupo) {
        this.id = grupo.getId();
        this.idEstabelecimento = grupo.getEstabelecimento() == null ? null : grupo.getEstabelecimento().getId();
        this.nome = grupo.getNome();
        this.descricao = grupo.getDescricao();
        this.minimoSelecoes = grupo.getMinimoSelecoes();
        this.maximoSelecoes = grupo.getMaximoSelecoes();
        this.ativo = grupo.isAtivo();
    }
}