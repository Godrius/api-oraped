package br.com.oraped.dto.produto.tamanho;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO de entrada para cadastro/edição de uma opção de tamanho da grade.
 *
 * Aplicação:
 * - usado para cadastrar tamanhos como P, M, G, Família
 * - não contém preço, pois o preço pertence ao produto/tamanho
 */
@Getter
@Setter
public class OpcaoTamanhoRequestDTO {

    private Long idGrade;
    private String nome;
    private String descricao;
    private Integer ordem;
    private Boolean ativo;

    public OpcaoTamanhoRequestDTO() {
    }

    public OpcaoTamanhoRequestDTO(
        Long idGrade,
        String nome,
        Integer ordem,
        Boolean ativo
    ) {
        this.idGrade = idGrade;
        this.nome = nome;
        this.ordem = ordem;
        this.ativo = ativo;
    }
}