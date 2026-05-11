package br.com.oraped.dto.produto.complemento;

import br.com.oraped.domain.produto.complemento.GrupoComplemento;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrupoComplementoResponseDTO {

    private Long id;
    private Long idEstabelecimento;
    private Long idCategoria;
    private String nomeCategoria;
    private Long idProduto;
    private String nomeProduto;
    private String nome;
    private String descricao;
    private Integer minimoSelecoes;
    private Integer maximoSelecoes;
    private Integer ordem;
    private boolean ativo;
    private boolean excluido;

    public GrupoComplementoResponseDTO(GrupoComplemento grupo) {

        if (grupo == null) {
            return;
        }

        this.id = grupo.getId();

        if (grupo.getEstabelecimento() != null) {
            this.idEstabelecimento = grupo.getEstabelecimento().getId();
        }

        if (grupo.getCategoria() != null) {
            this.idCategoria = grupo.getCategoria().getId();
            this.nomeCategoria = grupo.getCategoria().getNome();
        }

        if (grupo.getProduto() != null) {
            this.idProduto = grupo.getProduto().getId();
            this.nomeProduto = grupo.getProduto().getNome();
        }

        this.nome = grupo.getNome();
        this.descricao = grupo.getDescricao();
        this.minimoSelecoes = grupo.getMinimoSelecoes();
        this.maximoSelecoes = grupo.getMaximoSelecoes();
        this.ordem = grupo.getOrdem();
        this.ativo = grupo.isAtivo();
        this.excluido = grupo.isExcluido();
    }
}