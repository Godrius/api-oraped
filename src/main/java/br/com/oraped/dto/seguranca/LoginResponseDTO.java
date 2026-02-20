package br.com.oraped.dto.seguranca;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDTO {
  private String authToken;
}
