package br.com.oraped.service.whatsapp.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.oraped.domain.whatsapp.SessaoEncerradaEvent;
import br.com.oraped.repository.whatsapp.MensagemAtendimentoWhatsappRepository;
import lombok.RequiredArgsConstructor;

/**
 * Listener responsável por limpar o histórico de mensagens
 * de uma sessão de atendimento WhatsApp após seu encerramento.
 *
 * Estratégia adotada:
 * - Executa somente após o commit da transação principal (AFTER_COMMIT)
 * - Executa de forma assíncrona (@Async) para não atrasar a resposta ao cliente
 * - Executa em nova transação (REQUIRES_NEW) para isolar a operação de delete
 *
 * Objetivo:
 * - Evitar crescimento infinito da tabela de mensagens
 * - Não impactar o tempo de resposta da confirmação do pedido
 */
@Component
@RequiredArgsConstructor
public class LimpezaSessaoWhatsappListener {

    private final MensagemAtendimentoWhatsappRepository mensagemRepo;

    /**
     * Executado após a confirmação (commit) da transação
     * que publicou o evento de sessão encerrada.
     *
     * Fluxo:
     * 1) Pedido é criado
     * 2) Sessão é encerrada
     * 3) Transação principal faz commit
     * 4) Este método é disparado
     * 5) Histórico da sessão é removido em background
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessaoEncerrada(SessaoEncerradaEvent event) {

        Long idSessao = event.getIdSessao();

        // Segurança defensiva: evita delete com id nulo
        if (idSessao == null) {
            return;
        }

        // Remove todas as mensagens associadas à sessão encerrada
        mensagemRepo.deleteByIdSessao(idSessao);
    }
}