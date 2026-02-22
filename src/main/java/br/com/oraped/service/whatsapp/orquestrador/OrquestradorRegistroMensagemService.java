// src/main/java/br/com/oraped/service/whatsapp/orquestrador/OrquestradorRegistroMensagemService.java
package br.com.oraped.service.whatsapp.orquestrador;

import org.springframework.stereotype.Service;

import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.MensagemAtendimentoWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorRegistroMensagemService {

    private final MensagemAtendimentoWhatsappService mensagemService;

    public void registrarEntrada(Long idSessao, String texto, Object payloadOriginal) {
        mensagemService.registrarEntrada(idSessao, texto, payloadOriginal);
    }

    public void registrarSaida(Long idSessao, String chave, MensagemWhatsappSaidaDTO mensagem) {
        mensagemService.registrarSaida(idSessao, chave, mensagem);
    }
}