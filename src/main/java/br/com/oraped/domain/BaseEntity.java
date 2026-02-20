// src/main/java/br/com/oraped/domain/BaseEntity.java
package br.com.oraped.domain;

import java.time.LocalDateTime;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDateTime criadoEm;

  private LocalDateTime atualizadoEm;

  @PrePersist
  protected void onCreate() {
    this.criadoEm = LocalDateTime.now();
    this.atualizadoEm = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    this.atualizadoEm = LocalDateTime.now();
  }
}
