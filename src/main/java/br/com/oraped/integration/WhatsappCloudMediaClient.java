package br.com.oraped.integration;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class WhatsappCloudMediaClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String graphBaseUrl;
    private final String graphApiVersion;
    private final String accessToken;

    public WhatsappCloudMediaClient(
        ObjectMapper objectMapper,
        @Value("${whatsapp.cloud.graph-base-url}") String graphBaseUrl,
        @Value("${whatsapp.cloud.graph-api-version}") String graphApiVersion,
        @Value("${whatsapp.cloud.access-token}") String accessToken
    ) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .build();

        this.objectMapper = objectMapper;

        // Remove espaços e barras excedentes para evitar URLs malformadas.
        this.graphBaseUrl = normalizarBaseUrl(graphBaseUrl);
        this.graphApiVersion = limpar(graphApiVersion);
        this.accessToken = limpar(accessToken);
    }

    /**
     * Consulta a API da Meta para obter os metadados da mídia recebida no webhook.
     * O campo mais importante aqui é a URL temporária usada no download do binário.
     */
    public MediaMetadata buscarMetadata(String idMidia) {

        if (!StringUtils.hasText(idMidia)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMidia é obrigatório");
        }

        validarConfiguracao();

        String url = graphBaseUrl + "/" + graphApiVersion + "/" + idMidia.trim();

        String responseBody = restClient.get()
            .uri(url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(String.class);

        if (!StringUtils.hasText(responseBody)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Resposta vazia ao consultar mídia na Meta"
            );
        }

        JsonNode response;

        try {
            response = objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Não foi possível interpretar a resposta da Meta ao consultar a mídia",
                ex
            );
        }

        String urlMidia = textoOuNulo(response, "url");
        String mimeType = textoOuNulo(response, "mime_type");
        String sha256 = textoOuNulo(response, "sha256");
        String fileSize = textoOuNulo(response, "file_size");
        String messagingProduct = textoOuNulo(response, "messaging_product");

        if (!StringUtils.hasText(urlMidia)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Meta não retornou URL da mídia"
            );
        }

        return new MediaMetadata(
            idMidia.trim(),
            urlMidia,
            mimeType,
            sha256,
            fileSize,
            messagingProduct
        );
    }

    /**
     * Baixa o conteúdo binário da mídia.
     * A URL retornada pela Meta é temporária, então o download deve acontecer logo após a consulta.
     */
    public byte[] baixarMidia(String urlMidia) {

        if (!StringUtils.hasText(urlMidia)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "urlMidia é obrigatória");
        }

        validarConfiguracao();

        byte[] bytes = restClient.get()
            .uri(urlMidia.trim())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(byte[].class);

        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Não foi possível baixar a mídia da Meta"
            );
        }

        return bytes;
    }

    /**
     * Garante que as properties essenciais do client foram informadas.
     * Falhas aqui são de configuração da aplicação, não do usuário final.
     */
    private void validarConfiguracao() {

        if (!StringUtils.hasText(graphBaseUrl)) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "whatsapp.cloud.graph-base-url não configurado"
            );
        }

        if (!StringUtils.hasText(graphApiVersion)) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "whatsapp.cloud.graph-api-version não configurado"
            );
        }

        if (!StringUtils.hasText(accessToken)) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "whatsapp.cloud.access-token não configurado"
            );
        }
    }

    /**
     * Normaliza a base URL removendo barras no final.
     * Ex.: https://graph.facebook.com/// -> https://graph.facebook.com
     */
    private String normalizarBaseUrl(String value) {

        String v = limpar(value);

        if (!StringUtils.hasText(v)) {
            return v;
        }

        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }

        return v;
    }

    private String limpar(String value) {
        return value == null ? null : value.trim();
    }

    private String textoOuNulo(JsonNode node, String campo) {

        if (node == null) {
            return null;
        }

        JsonNode v = node.path(campo);
        return v.isMissingNode() || v.isNull() ? null : v.asText(null);
    }

    /**
     * Estrutura simples para transportar os metadados relevantes da mídia
     * entre as camadas do fluxo de processamento.
     */
    public record MediaMetadata(
        String idMidia,
        String url,
        String mimeType,
        String sha256,
        String fileSize,
        String messagingProduct
    ) {
    }
}