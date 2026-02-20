package br.com.oraped.dto.seguranca;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginPorTokenRequestDTO {

    /**
     * Token técnico do usuário de integração (ex: INTEGRACAO_N8N)
     * Exemplo: u_a3f91c8d9e1f4c2b...
     */
    @NotBlank(message = "tokenId é obrigatório")
    private String tokenId;

    /**
     * Senha associada ao usuário de integração
     */
    @NotBlank(message = "senha é obrigatória")
    private String senha;
}
