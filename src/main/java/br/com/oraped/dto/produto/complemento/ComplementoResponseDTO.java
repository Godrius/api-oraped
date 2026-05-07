package br.com.oraped.dto.produto.complemento;

import java.math.BigDecimal;

import br.com.oraped.domain.produto.complemento.Complemento;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComplementoResponseDTO {

    private Long id;
    private Long idGrupo;
    private String nome;
    private String descricao;
    private BigDecimal precoAdicional;
    private boolean ativo;

    public ComplementoResponseDTO(Complemento complemento) {
        this.id = complemento.getId();
        this.idGrupo = complemento.getGrupo() == null ? null : complemento.getGrupo().getId();
        this.nome = complemento.getNome();
        this.descricao = complemento.getDescricao();
        this.precoAdicional = complemento.getPrecoAdicional();
        this.ativo = complemento.isAtivo();
    }
}