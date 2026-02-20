package br.com.oraped.service.seguranca;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;

@Service
public class AuthTokenService {

  @Value("${api.security.token.secret}")
  private String secret;

  public String gerarTokenAutenticacao(String subject, String perfil) {
    return JWT.create()
        .withSubject(subject)
        .withClaim("perfil", perfil)
        .withIssuedAt(new Date())
        .withExpiresAt(Date.from(Instant.now().plus(12, ChronoUnit.HOURS)))
        .sign(Algorithm.HMAC256(secret));
  }

  public String validarTokenAutenticacao(String token) {
    try {
      return JWT.require(Algorithm.HMAC256(secret))
          .build()
          .verify(token)
          .getSubject();
    } catch (TokenExpiredException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expirado", e);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido", e);
    }
  }

  public boolean isValid(String token) {
    try {
      JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public String getSubject(String token) {
    return JWT.require(Algorithm.HMAC256(secret))
        .build()
        .verify(token)
        .getSubject();
  }
}
