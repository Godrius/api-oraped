// src/main/java/br/com/oraped/repository/atendimento/SessaoAtendimentoWhatsappRepository.java
package br.com.oraped.repository.whatsapp;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;

public interface SessaoAtendimentoWhatsappRepository extends JpaRepository<SessaoAtendimentoWhatsapp, Long> {

  Optional<SessaoAtendimentoWhatsapp> findByWhatsappClienteAndWhatsappReceptor(String whatsappCliente, String whatsappReceptor);
}