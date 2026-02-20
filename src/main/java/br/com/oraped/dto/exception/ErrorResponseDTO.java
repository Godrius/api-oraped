// src/main/java/br/com/oraped/infra/exception/ErrorResponseDTO.java
package br.com.oraped.dto.exception;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorResponseDTO {

  private LocalDateTime timestamp;
  private int status;
  private String error;
  private String message;
  private String path;
  private List<FieldErrorDTO> fieldErrors;

  @Getter
  @Setter
  public static class FieldErrorDTO {
    private String field;
    private String message;
  }
}
