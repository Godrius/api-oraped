package br.com.oraped.dto.produto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComplementoRequestDTO {

    private Long idGrupo;
    private String nome;
    private String descricao;
    private BigDecimal precoAdicional;
    private Boolean ativo;
}