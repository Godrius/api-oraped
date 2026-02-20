// src/main/java/br/com/oraped/domain/whatsapp/MensagemAtendimentoWhatsapp.java
package br.com.oraped.domain.whatsapp;

import java.time.OffsetDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
  name = "mensagem_atendimento_whatsapp",
  indexes = {
    @Index(name = "idx_msg_sessao", columnList = "id_sessao"),
    @Index(name = "idx_msg_criada_em", columnList = "criada_em"),
    // otimiza: findTop1ByIdSessaoAndDirecaoOrderByCriadaEmDesc(...)
    @Index(name = "idx_msg_sessao_direcao_criadaem", columnList = "id_sessao, direcao, criada_em")
  }
)
public class MensagemAtendimentoWhatsapp {

  public enum Direcao {
    ENTRADA, SAIDA
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "id_sessao", nullable = false)
  private Long idSessao;

  @Enumerated(EnumType.STRING)
  @Column(name = "direcao", nullable = false, length = 10)
  private Direcao direcao;

  // texto/comando em forma humana (debug)
  @Column(name = "conteudo_texto", length = 5000)
  private String conteudoTexto;

  // payload JSON (entrada: payloadOriginal; saída: mensagem pronta)
  @Lob
  @Column(name = "payload_json", columnDefinition = "LONGTEXT")
  private String payloadJson;

  @Column(name = "criada_em", nullable = false)
  private OffsetDateTime criadaEm;

  @PrePersist
  public void prePersist() {
    if (criadaEm == null) criadaEm = OffsetDateTime.now();
  }
}