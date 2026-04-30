package br.com.oraped.domain.whatsapp;

import br.com.oraped.domain.Estabelecimento;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrquestradorContexto {

    private final Estabelecimento estabelecimento;
    private final SessaoAtendimentoWhatsapp sessao;

    private final String whatsappCliente;
    private final String whatsappReceptor;
    private final String phoneNumberId;

    private final String nomeClienteWhatsapp;

    private final boolean temSaidaAnterior;
}