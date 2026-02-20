package br.com.oraped.domain.seguranca;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import br.com.oraped.domain.enums.PerfilUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuario")
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Usuario implements UserDetails {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String nome;

  @Column(nullable = false, unique = true)
  private String login;

  @Size(min = 8)
  @Column(nullable = false)
  private String senha;

  @Column(nullable = true)
  private String email;

  @Column(length = 20)
  private String whatsapp;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PerfilUsuario perfil;

  private LocalDateTime dataCriacao;
  private LocalDateTime dataUltimoAcesso;

  @Column(nullable = false)
  private Boolean ativado = true;

  @Column(nullable = false, unique = true, length = 34)
  private String tokenId;

  // opcional (se você quiser manter igual ao Oraclin)
  private String authToken;
  private LocalDateTime dataExpiracaoAuthToken;

  // auxiliares
  @JsonProperty("isAdministrador")
  private boolean isAdministrador;

  @JsonIgnore
  public boolean isIntegracaoN8n() {
    return this.perfil == PerfilUsuario.INTEGRACAO_N8N;
  }

  @Override
  @JsonIgnore
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
  }

  @Override
  public String getPassword() {
    return senha;
  }

  @Override
  public String getUsername() {
    return login;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return ativado != null && ativado;
  }
}
