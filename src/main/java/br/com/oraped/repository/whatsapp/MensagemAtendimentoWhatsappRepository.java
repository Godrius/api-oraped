// src/main/java/br/com/oraped/repository/whatsapp/MensagemAtendimentoWhatsappRepository.java
package br.com.oraped.repository.whatsapp;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.whatsapp.MensagemAtendimentoWhatsapp;
import br.com.oraped.domain.whatsapp.MensagemAtendimentoWhatsapp.Direcao;

public interface MensagemAtendimentoWhatsappRepository extends JpaRepository<MensagemAtendimentoWhatsapp, Long> {

  Optional<MensagemAtendimentoWhatsapp> findTop1ByIdSessaoAndDirecaoOrderByCriadaEmDesc(Long idSessao, Direcao direcao);

  List<MensagemAtendimentoWhatsapp> findByIdSessaoAndDirecaoOrderByCriadaEmAsc(Long idSessao, Direcao direcao);
}