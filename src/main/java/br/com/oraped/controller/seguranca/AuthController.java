package br.com.oraped.controller.seguranca;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import br.com.oraped.domain.seguranca.Usuario;
import br.com.oraped.dto.seguranca.LoginPorTokenRequestDTO;
import br.com.oraped.dto.seguranca.LoginRequestDTO;
import br.com.oraped.dto.seguranca.LoginResponseDTO;
import br.com.oraped.exception.ApiException;
import br.com.oraped.service.seguranca.AuthService;
import br.com.oraped.service.seguranca.AuthTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(
  name = "Autenticação",
  description = "Endpoints de login e emissão de token de autenticação."
)
public class AuthController {

  private final AuthService authService;
  private final AuthTokenService tokenService;

  @PermitAll
  @PostMapping("/login")
  @Operation(
    summary = "Login por usuário e senha",
    description = "Autentica um usuário com login e senha e retorna um token de autenticação para uso nas demais rotas."
  )
  @ApiResponses({
    @ApiResponse(
      responseCode = "200",
      description = "Login realizado com sucesso.",
      content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))
    ),
    @ApiResponse(
      responseCode = "401",
      description = "Login ou senha inválidos / Usuário sem perfil configurado.",
      content = @Content(schema = @Schema(implementation = ApiException.class))
    ),
    @ApiResponse(
      responseCode = "400",
      description = "Requisição inválida (validações do DTO).",
      content = @Content
    )
  })
  public ResponseEntity<LoginResponseDTO> login(
    @RequestBody(
      required = true,
      description = "Credenciais de acesso do usuário.",
      content = @Content(schema = @Schema(implementation = LoginRequestDTO.class))
    )
    @org.springframework.web.bind.annotation.RequestBody @Valid LoginRequestDTO dto
  ) {

    Usuario usuario = authService.autenticar(dto.getLogin(), dto.getSenha());

    if (usuario == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Login ou senha inválidos.");
    }

    if (usuario.getPerfil() == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Usuário sem perfil configurado.");
    }

    String authToken = tokenService.gerarTokenAutenticacao(
      usuario.getLogin(),
      usuario.getPerfil().name()
    );

    return ResponseEntity.ok(new LoginResponseDTO(authToken));
  }

  @PermitAll
  @PostMapping("/login-por-token")
  @Operation(
    summary = "Login por TokenId e senha",
    description = "Autentica um usuário informando TokenId e senha e retorna um token de autenticação para uso nas demais rotas."
  )
  @ApiResponses({
    @ApiResponse(
      responseCode = "200",
      description = "Login realizado com sucesso.",
      content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))
    ),
    @ApiResponse(
      responseCode = "401",
      description = "TokenId ou senha inválidos / Usuário sem perfil configurado.",
      content = @Content(schema = @Schema(implementation = ApiException.class))
    ),
    @ApiResponse(
      responseCode = "400",
      description = "Requisição inválida (validações do DTO).",
      content = @Content
    )
  })
  public ResponseEntity<LoginResponseDTO> loginPorToken(
    @RequestBody(
      required = true,
      description = "Credenciais baseadas em TokenId.",
      content = @Content(schema = @Schema(implementation = LoginPorTokenRequestDTO.class))
    )
    @org.springframework.web.bind.annotation.RequestBody @Valid LoginPorTokenRequestDTO dto
  ) {

    Usuario usuario = authService.autenticarPorTokenId(dto.getTokenId(), dto.getSenha());

    if (usuario == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "TokenId ou senha inválidos.");
    }

    if (usuario.getPerfil() == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Usuário sem perfil configurado.");
    }

    String authToken = tokenService.gerarTokenAutenticacao(
      usuario.getLogin(),
      usuario.getPerfil().name()
    );

    return ResponseEntity.ok(new LoginResponseDTO(authToken));
  }
}