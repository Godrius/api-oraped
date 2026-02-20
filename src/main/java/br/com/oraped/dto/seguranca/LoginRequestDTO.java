package br.com.oraped.dto.seguranca;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDTO {

  @NotBlank
  private String login;

  @NotBlank
  private String senha;
}
