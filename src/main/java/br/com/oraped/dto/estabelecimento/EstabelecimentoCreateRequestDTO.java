package br.com.oraped.dto.estabelecimento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EstabelecimentoCreateRequestDTO {

    @NotBlank(message = "nome é obrigatório")
    @Size(max = 120, message = "nome deve ter no máximo 120 caracteres")
    private String nome;

    @NotBlank(message = "whatsapp é obrigatório")
    @Size(max = 30, message = "whatsapp deve ter no máximo 30 caracteres")
    private String whatsapp;

    @Size(max = 40, message = "timezone deve ter no máximo 40 caracteres")
    private String timezone;

    private String endereco;

    private String configuracoesJson;

    @NotNull(message = "idCategoriaMarketplace é obrigatório")
    private Long idCategoriaMarketplace;

    // defaults opcionais
    private Boolean ativo;
    private Boolean aberto;
}