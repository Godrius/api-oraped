package br.com.oraped.service.whatsapp.orquestrador;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.OrquestradorContexto;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;

@Service
public class OrquestradorContextoService {

    public OrquestradorContexto montarContexto(
        Estabelecimento estabelecimento,
        SessaoAtendimentoWhatsapp sessao,
        String whatsappCliente,
        String whatsappReceptor,
        String phoneNumberId,
        boolean temSaidaAnterior
    ) {
        return new OrquestradorContexto(
            estabelecimento,
            sessao,
            whatsappCliente,
            whatsappReceptor,
            phoneNumberId,
            temSaidaAnterior
        );
    }
}