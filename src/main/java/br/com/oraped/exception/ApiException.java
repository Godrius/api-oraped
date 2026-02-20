// src/main/java/br/com/oraped/infra/exception/ApiException.java
package br.com.oraped.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private final HttpStatus status;

	public ApiException(HttpStatus status, String message) {
		super(message);
	    this.status = status;
	}
}
