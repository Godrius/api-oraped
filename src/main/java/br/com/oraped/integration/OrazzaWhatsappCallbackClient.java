// src/main/java/br/com/oraped/integrations/OrazzaWhatsappCallbackClient.java
package br.com.oraped.integration;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.oraped.dto.whatsapp.saida.RespostaWhatsappDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrazzaWhatsappCallbackClient implements DisposableBean {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String baseUrl;
    private final ExecutorService executor;

    public OrazzaWhatsappCallbackClient(
        ObjectMapper objectMapper,
        @Value("${orazza.whatsapp.base-url:}") String baseUrl,
        @Value("${orazza.whatsapp.connect-timeout-ms:5000}") int connectTimeoutMs,
        @Value("${orazza.whatsapp.read-timeout-ms:10000}") int readTimeoutMs,
        @Value("${orazza.whatsapp.async.pool-size:4}") int poolSize
    ) {

        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(connectTimeoutMs).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(readTimeoutMs).toMillis());

        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .build();

        this.executor = Executors.newFixedThreadPool(
            Math.max(1, poolSize),
            new NamedThreadFactory("oraped-orazza-callback")
        );

        if (!StringUtils.hasText(this.baseUrl)) {
            log.warn("OrazzaWhatsappCallbackClient iniciado SEM base-url (orazza.whatsapp.base-url). Callbacks serão ignorados.");
        } else {
            log.info("OrazzaWhatsappCallbackClient iniciado com baseUrl='{}'", this.baseUrl);
        }
    }

    public void enviarRespostaAssincrono(RespostaWhatsappDTO resposta) {

        if (!StringUtils.hasText(baseUrl)) return;
        if (resposta == null) return;

        executor.execute(() -> enviarRespostaComRetry(resposta));
    }

    private void enviarRespostaComRetry(RespostaWhatsappDTO resposta) {

        String correlationId = StringUtils.hasText(resposta.getIdCorrelacao())
            ? resposta.getIdCorrelacao().trim()
            : UUID.randomUUID().toString();

        // ✅ idempotency estável por resposta (não aleatório)
        String idempotencyKey = correlationId;

        String url = baseUrl + "/whatsapp/respostas";

        int[] delaysMs = new int[] { 0, 1000, 3000 };
        Exception ultima = null;

        for (int i = 0; i < delaysMs.length; i++) {

            if (delaysMs[i] > 0) {
                try {
                    Thread.sleep(delaysMs[i]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try {

                String json = null;
                try {
                    json = objectMapper.writeValueAsString(resposta);
                } catch (Exception ignored) {
                }

                log.info(">>> CALLBACK PARA ORAZZA tentativa={} url=/whatsapp/respostas correlationId={} idempotencyKey={} payload={}",
                    (i + 1),
                    correlationId,
                    idempotencyKey,
                    (json == null ? "(erro_serializacao)" : json)
                );

                Integer status = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-Id", correlationId)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .body(resposta)
                    .exchange((req, res) -> res.getStatusCode().value());

                if (status != null && status >= 200 && status < 300) {
                    log.info("Callback Orazza OK status={} correlationId={} tentativa={}",
                        status, correlationId, (i + 1));
                    return;
                }

                log.warn("Callback Orazza NAO-2xx status={} correlationId={} tentativa={}",
                    status, correlationId, (i + 1));

            } catch (ResourceAccessException e) {
                ultima = e;
                log.warn("Callback Orazza timeout/rede correlationId={} tentativa={} err={}",
                    correlationId, (i + 1), safeMsg(e));
            } catch (Exception e) {
                ultima = e;
                log.warn("Callback Orazza falha correlationId={} tentativa={} err={}",
                    correlationId, (i + 1), safeMsg(e), e);
            }
        }

        log.error("Callback Orazza FALHOU (após retries) correlationId={} err={}",
            correlationId, safeMsg(ultima), ultima);
    }

    private String safeMsg(Throwable t) {
        String m = t == null ? null : t.getMessage();
        return m == null ? "" : m.replaceAll("[\\r\\n]+", " ").trim();
    }

    @Override
    public void destroy() {
        try {
            executor.shutdown();
        } catch (Exception ignored) {
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private int seq = 1;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + (seq++));
            t.setDaemon(true);
            return t;
        }
    }
}