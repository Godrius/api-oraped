// src/main/java/br/com/oraped/service/whatsapp/MensagemAtendimentoWhatsappService.java
package br.com.oraped.service.whatsapp;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.oraped.domain.whatsapp.MensagemAtendimentoWhatsapp;
import br.com.oraped.domain.whatsapp.MensagemAtendimentoWhatsapp.Direcao;
import br.com.oraped.repository.whatsapp.MensagemAtendimentoWhatsappRepository;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MensagemAtendimentoWhatsappService {

  private final MensagemAtendimentoWhatsappRepository msgRepo;
  private final ObjectMapper objectMapper;

  @Transactional
  public void registrarEntrada(Long idSessao, String conteudoTexto, Object payloadOriginal) {
    MensagemAtendimentoWhatsapp m = new MensagemAtendimentoWhatsapp();
    m.setIdSessao(idSessao);
    m.setDirecao(Direcao.ENTRADA);
    m.setConteudoTexto(conteudoTexto);
    m.setPayloadJson(toJson(payloadOriginal));
    msgRepo.save(m);
  }

  @Transactional
  public void registrarSaida(Long idSessao, String conteudoTexto, Object payloadSaida) {
    MensagemAtendimentoWhatsapp m = new MensagemAtendimentoWhatsapp();
    m.setIdSessao(idSessao);
    m.setDirecao(Direcao.SAIDA);
    m.setConteudoTexto(conteudoTexto);
    m.setPayloadJson(toJson(payloadSaida));
    msgRepo.save(m);
  }

  @Transactional(readOnly = true)
  public Optional<MensagemAtendimentoWhatsapp> buscarUltimaSaida(Long idSessao) {
    return msgRepo.findTop1ByIdSessaoAndDirecaoOrderByCriadaEmDesc(idSessao, Direcao.SAIDA);
  }

  @Transactional(readOnly = true)
  public List<MensagemAtendimentoWhatsapp> listarEntradas(Long idSessao) {
    return msgRepo.findByIdSessaoAndDirecaoOrderByCriadaEmAsc(idSessao, Direcao.ENTRADA);
  }

  private String toJson(Object o) {
    if (o == null) return null;
    try {
      return objectMapper.writeValueAsString(o);
    } catch (Exception e) {
      return "{\"erro\":\"Falha ao serializar payload\"}";
    }
  }
}