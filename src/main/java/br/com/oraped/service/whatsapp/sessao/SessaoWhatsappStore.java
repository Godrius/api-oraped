package br.com.oraped.service.whatsapp.sessao;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.repository.whatsapp.SessaoAtendimentoWhatsappRepository;
import lombok.RequiredArgsConstructor;

/**
 * Serviço de infraestrutura da sessão WhatsApp.
 * Centraliza busca e salvamento para evitar duplicação nos serviços especializados.
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappStore {

    private final SessaoAtendimentoWhatsappRepository sessaoRepo;

    public SessaoAtendimentoWhatsapp buscarPorId(Long idSessao) {
        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }

        return sessaoRepo.findById(idSessao)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
    }

    public SessaoAtendimentoWhatsapp salvar(SessaoAtendimentoWhatsapp sessao) {
        if (sessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sessão é obrigatória");
        }

        sessao.setUltimaInteracaoEm(OffsetDateTime.now());
        return sessaoRepo.save(sessao);
    }
}