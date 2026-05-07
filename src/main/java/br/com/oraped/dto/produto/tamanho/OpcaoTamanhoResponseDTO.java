package br.com.oraped.dto.produto.tamanho;

import br.com.oraped.domain.produto.tamanho.OpcaoTamanho;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de saída da opção de tamanho da grade.
 *
 * Regra:
 * - não retorna preço, pois o preço pertence ao vínculo com o produto
 */
@Getter
@Setter
public class OpcaoTamanhoResponseDTO {

    private Long idOpcaoTamanho;
    private Long idGrade;
    private String nome;
    private String descricao;
    private Integer ordem;
    private boolean ativo;

    public OpcaoTamanhoResponseDTO(OpcaoTamanho opcao) {

        if (opcao == null) {
            return;
        }

        this.idOpcaoTamanho = opcao.getId();

        if (opcao.getGrade() != null) {
            this.idGrade = opcao.getGrade().getId();
        }

        this.nome = opcao.getNome();
        this.descricao = opcao.getDescricao();
        this.ordem = opcao.getOrdem();
        this.ativo = opcao.isAtivo();
    }
}