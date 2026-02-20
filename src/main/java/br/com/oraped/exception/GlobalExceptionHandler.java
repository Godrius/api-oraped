// src/main/java/br/com/oraped/infra/exception/GlobalExceptionHandler.java
package br.com.oraped.exception;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.dto.exception.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponseDTO> handleApiException(ApiException ex, HttpServletRequest req) {
    ErrorResponseDTO body = new ErrorResponseDTO();
    body.setTimestamp(LocalDateTime.now());
    body.setStatus(ex.getStatus().value());
    body.setError(ex.getStatus().getReasonPhrase());
    body.setMessage(ex.getMessage());
    body.setPath(req.getRequestURI());
    return ResponseEntity.status(ex.getStatus()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
    ErrorResponseDTO body = new ErrorResponseDTO();
    body.setTimestamp(LocalDateTime.now());
    body.setStatus(HttpStatus.BAD_REQUEST.value());
    body.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
    body.setMessage("Dados inválidos.");
    body.setPath(req.getRequestURI());

    List<ErrorResponseDTO.FieldErrorDTO> fields = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      ErrorResponseDTO.FieldErrorDTO dto = new ErrorResponseDTO.FieldErrorDTO();
      dto.setField(fe.getField());
      dto.setMessage(fe.getDefaultMessage());
      fields.add(dto);
    }
    body.setFieldErrors(fields);

    return ResponseEntity.badRequest().body(body);
  }

  // ✅ IMPORTANTE: não mascarar 401/403 etc. lançados via ResponseStatusException
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponseDTO> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {

    int status = ex.getStatusCode().value();
    String reason = ex.getStatusCode().toString(); // ex.: "401 UNAUTHORIZED"
    String message = (ex.getReason() != null && !ex.getReason().isBlank())
      ? ex.getReason()
      : HttpStatus.valueOf(status).getReasonPhrase();

    // loga 5xx como erro; 4xx como warn (melhor sinal/ruído)
    if (status >= 500) {
      log.error("Erro {} em {} {}: {}", status, req.getMethod(), req.getRequestURI(), message, ex);
    } else {
      log.warn("Resposta {} em {} {}: {}", status, req.getMethod(), req.getRequestURI(), message);
    }

    ErrorResponseDTO body = new ErrorResponseDTO();
    body.setTimestamp(LocalDateTime.now());
    body.setStatus(status);
    body.setError(HttpStatus.valueOf(status).getReasonPhrase());
    body.setMessage(message);
    body.setPath(req.getRequestURI());
    body.setFieldErrors(null);

    return ResponseEntity.status(status).body(body);
  }

  // ✅ Opcional, mas recomendado: exceções de auth do Spring Security como 401
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponseDTO> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
    log.warn("Falha de autenticação em {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());

    ErrorResponseDTO body = new ErrorResponseDTO();
    body.setTimestamp(LocalDateTime.now());
    body.setStatus(HttpStatus.UNAUTHORIZED.value());
    body.setError(HttpStatus.UNAUTHORIZED.getReasonPhrase());
    body.setMessage("Não autorizado");
    body.setPath(req.getRequestURI());
    body.setFieldErrors(null);

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponseDTO> handleGeneric(Exception ex, HttpServletRequest req) {
    // IMPORTANTE: loga stacktrace pra diagnóstico
    log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);

    ErrorResponseDTO body = new ErrorResponseDTO();
    body.setTimestamp(LocalDateTime.now());
    body.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    body.setError(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    body.setMessage("Erro interno inesperado.");
    body.setPath(req.getRequestURI());
    body.setFieldErrors(null);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
