// src/main/java/br/com/oraped/api/controller/HealthController.java
package br.com.oraped.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  @GetMapping("/health")
  public String health() {
    return "ok";
  }
}
