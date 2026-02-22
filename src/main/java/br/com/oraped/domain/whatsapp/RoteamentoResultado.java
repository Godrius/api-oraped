// src/main/java/br/com/oraped/service/whatsapp/orquestrador/RoteamentoResultado.java
package br.com.oraped.domain.whatsapp;

import java.util.List;

import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import lombok.Getter;

@Getter
public class RoteamentoResultado {

    private final String chave;
    private final MensagemWhatsappSaidaDTO mensagem;
    private final List<MensagemWhatsappSaidaDTO> extras;

    public RoteamentoResultado(String chave, MensagemWhatsappSaidaDTO mensagem) {
        this(chave, mensagem, List.of());
    }

    public RoteamentoResultado(String chave, MensagemWhatsappSaidaDTO mensagem, List<MensagemWhatsappSaidaDTO> extras) {
        this.chave = chave;
        this.mensagem = mensagem;
        this.extras = extras == null ? List.of() : extras;
    }
}