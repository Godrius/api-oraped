package br.com.oraped.dto.produto.tamanho;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO de entrada para cadastro/edição da grade de tamanhos.
 *
 * Aplicação:
 * - cria grade com escopo por categoria ou por produto
 *
 * Regra:
 * - deve informar idCategoria ou idProduto
 * - nunca ambos
 */
@Getter
@Setter
public class GradeTamanhoRequestDTO {

    private Long idEstabelecimento;
    private Long idCategoria;
    private Long idProduto;
    private String nome;
    private String descricao;
    private Boolean ativo;
}