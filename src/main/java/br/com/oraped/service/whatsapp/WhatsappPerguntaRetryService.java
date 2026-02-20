// src/main/java/br/com/oraped/service/whatsapp/WhatsappPerguntaRetryService.java
package br.com.oraped.service.whatsapp;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.dto.whatsapp.saida.RespostaWhatsappDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsappPerguntaRetryService {

    public enum EtapaBloqueante {
        ENDERECO_ENTREGA,
        FORMA_PAGAMENTO,
        TROCO_CONFIRMACAO,
        TROCO_VALOR,
        CONFIRMACAO_FINAL
    }

    private static final int MAX_TENTATIVAS = 3;

    // Exemplo de delays: 20s, 45s, 90s (ajuste como quiser)
    private static final Duration[] DELAYS = new Duration[] {
        Duration.ofSeconds(20),
        Duration.ofSeconds(45),
        Duration.ofSeconds(90)
    };

    @Qualifier("whatsappRetryTaskScheduler")
    private final ThreadPoolTaskScheduler scheduler;

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final OrazzaRetrySender retrySender;

    private final ConcurrentHashMap<String, RetryState> states = new ConcurrentHashMap<>();

    public void agendarRetriesSeNecessario(
        Long idSessao,
        EtapaBloqueante etapa,
        String whatsappCliente,
        String whatsappReceptor,
        String phoneNumberId,
        MensagemWhatsappSaidaDTO ultimaPergunta
    ) {

        Objects.requireNonNull(idSessao, "idSessao é obrigatório");
        Objects.requireNonNull(etapa, "etapa é obrigatória");
        Objects.requireNonNull(ultimaPergunta, "ultimaPergunta é obrigatória");

        String key = key(idSessao, etapa);

        states.compute(key, (k, old) -> {

            // Se já tem retry rodando para a mesma etapa, não cria outro
            if (old != null && old.future != null && !old.future.isDone() && !old.future.isCancelled()) {
                old.whatsappCliente = whatsappCliente;
                old.whatsappReceptor = whatsappReceptor;
                old.phoneNumberId = phoneNumberId;
                old.ultimaPergunta = ultimaPergunta;
                return old;
            }

            RetryState st = new RetryState();
            st.idSessao = idSessao;
            st.etapa = etapa;
            st.whatsappCliente = whatsappCliente;
            st.whatsappReceptor = whatsappReceptor;
            st.phoneNumberId = phoneNumberId;
            st.ultimaPergunta = ultimaPergunta;
            st.tentativas = 0;

            agendarProximaTentativa(st);

            return st;
        });
    }

    public void cancelarRetries(Long idSessao) {

        if (idSessao == null) {
            return;
        }

        states.forEach((k, st) -> {
            if (st != null && Objects.equals(idSessao, st.idSessao)) {
                cancelarPorKey(k);
            }
        });
    }

    public void cancelarRetries(Long idSessao, EtapaBloqueante etapa) {

        if (idSessao == null || etapa == null) {
            return;
        }

        cancelarPorKey(key(idSessao, etapa));
    }

    private void cancelarPorKey(String key) {

        RetryState st = states.remove(key);
        if (st == null) {
            return;
        }

        ScheduledFuture<?> f = st.future;
        if (f != null) {
            f.cancel(false);
        }
    }

    private void agendarProximaTentativa(RetryState st) {

        int idx = Math.min(st.tentativas, DELAYS.length - 1);
        Duration delay = DELAYS[idx];

        st.future = scheduler.schedule(
            () -> executarTentativa(st.idSessao, st.etapa),
            java.util.Date.from(java.time.Instant.now().plus(delay))
        );
    }

    private void executarTentativa(Long idSessao, EtapaBloqueante etapa) {

        String key = key(idSessao, etapa);
        RetryState st = states.get(key);

        if (st == null) {
            return;
        }

        // Se a sessão não está mais nessa etapa, cancela e limpa
        if (!sessaoAindaAguardandoEtapa(idSessao, etapa)) {
            cancelarPorKey(key);
            return;
        }

        st.tentativas++;

        if (st.tentativas > MAX_TENTATIVAS) {

            MensagemWhatsappSaidaDTO fim = retrySender.mensagemFimDeTentativas(st.whatsappCliente);

            RespostaWhatsappDTO resp = retrySender.montarRespostaParaReenvio(
                null,
                st.whatsappCliente,
                st.whatsappReceptor,
                st.phoneNumberId,
                fim
            );

            retrySender.enviarAssincrono(resp);

            cancelarPorKey(key);
            return;
        }

        MensagemWhatsappSaidaDTO reenviar = retrySender.prefixarMensagemDeReenvio(st.ultimaPergunta);

        RespostaWhatsappDTO resp = retrySender.montarRespostaParaReenvio(
            null,
            st.whatsappCliente,
            st.whatsappReceptor,
            st.phoneNumberId,
            reenviar
        );

        retrySender.enviarAssincrono(resp);

        // agenda próxima tentativa (mantém rodando enquanto ainda estiver aguardando)
        agendarProximaTentativa(st);
    }

    private boolean sessaoAindaAguardandoEtapa(Long idSessao, EtapaBloqueante etapa) {

        return switch (etapa) {
            case ENDERECO_ENTREGA -> sessaoService.isAguardandoEnderecoEntrega(idSessao);
            case FORMA_PAGAMENTO -> sessaoService.isAguardandoFormaPagamento(idSessao);
            case TROCO_CONFIRMACAO -> sessaoService.isAguardandoTrocoConfirmacao(idSessao);
            case TROCO_VALOR -> sessaoService.isAguardandoTrocoValor(idSessao);
            case CONFIRMACAO_FINAL -> sessaoService.isAguardandoConfirmacaoFinal(idSessao);
        };
    }

    private String key(Long idSessao, EtapaBloqueante etapa) {
        return idSessao + "|" + etapa.name();
    }

    private static class RetryState {
        private Long idSessao;
        private EtapaBloqueante etapa;

        private String whatsappCliente;
        private String whatsappReceptor;
        private String phoneNumberId;

        private MensagemWhatsappSaidaDTO ultimaPergunta;

        private int tentativas;
        private volatile ScheduledFuture<?> future;
    }
}