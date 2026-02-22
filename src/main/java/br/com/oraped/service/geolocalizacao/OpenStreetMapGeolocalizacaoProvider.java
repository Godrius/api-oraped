// src/main/java/br/com/oraped/service/geolocalizacao/OpenStreetMapGeolocalizacaoProvider.java
package br.com.oraped.service.geolocalizacao;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.dto.geolocalizacao.EnderecoBairroProximoDTO;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import lombok.Getter;
import lombok.Setter;

@Service
public class OpenStreetMapGeolocalizacaoProvider implements GeolocalizacaoProvider {

    private static final int OVERPASS_RAIO_METROS_PADRAO = 5000;

    private final RestClient http;

    public OpenStreetMapGeolocalizacaoProvider() {

        ClientHttpRequestFactory rf = criarRequestFactory(
            Duration.ofSeconds(8),
            Duration.ofSeconds(20)
        );

        this.http = RestClient.builder()
            .requestFactory(rf)
            .defaultHeader(HttpHeaders.USER_AGENT, "OraPed/1.0 (geolocalizacao)")
            .build();
    }

    private ClientHttpRequestFactory criarRequestFactory(Duration connectTimeout, Duration readTimeout) {

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) connectTimeout.toMillis());
        rf.setReadTimeout((int) readTimeout.toMillis());
        return rf;
    }

    @Override
    public EnderecoResolvidoDTO resolverCep(String cep) {

        String cepLimpo = somenteDigitos(cep);

        if (!StringUtils.hasText(cepLimpo) || cepLimpo.length() != 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CEP inválido");
        }

        ViaCepResponse via = buscarViaCep(cepLimpo);

        if (via == null || Boolean.TRUE.equals(via.getErro())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não encontrei o CEP informado");
        }

        EnderecoResolvidoDTO out = new EnderecoResolvidoDTO();
        out.setCep(cepLimpo);
        out.setLogradouro(safe(via.getLogradouro()));
        out.setBairro(safe(via.getBairro()));
        out.setCidade(safe(via.getLocalidade()));
        out.setUf(safe(via.getUf()));

        if (StringUtils.hasText(out.getCidade()) && StringUtils.hasText(out.getUf())) {

        	String query1 = montarQueryGeocode(out, cepLimpo);
        	NominatimSearchItem best = geocodificarNominatim(query1);

        	if (best == null || best.getLat() == null || best.getLon() == null) {
        	    // fallback 1: sem logradouro (às vezes o ViaCEP vem sem logradouro)
        	    String query2 = montarQueryGeocodeSemLogradouro(out, cepLimpo);
        	    best = geocodificarNominatim(query2);
        	}

        	if (best == null || best.getLat() == null || best.getLon() == null) {
        	    // fallback 2: bairro + cidade/UF + Brasil (sem CEP no final)
        	    String query3 = montarQueryGeocodeSomenteBairroCidadeUf(out);
        	    best = geocodificarNominatim(query3);
        	}

        	if (best != null && best.getLat() != null && best.getLon() != null) {
        	    out.setLatitude(parseDoubleSafe(best.getLat()));
        	    out.setLongitude(parseDoubleSafe(best.getLon()));
        	}
        }

        return out;
    }

    @Override
    public List<EnderecoBairroProximoDTO> buscarBairrosProximos(
        Double latitude,
        Double longitude,
        String cidade,
        String uf,
        int limite
    ) {

        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude/longitude são obrigatórios");
        }

        int lim = (limite <= 0) ? 40 : Math.min(limite, 50);

        OverpassResponse resp = consultarOverpassBairros(latitude, longitude, OVERPASS_RAIO_METROS_PADRAO);

        if (resp == null || resp.getElements() == null || resp.getElements().isEmpty()) {
            return List.of();
        }

        Map<String, EnderecoBairroProximoDTO> dedup = new LinkedHashMap<>();

        for (OverpassElement e : resp.getElements()) {

            String nomeBairro = null;

            if (e.getTags() != null) {
                nomeBairro = safe(e.getTags().get("name"));
                if (!StringUtils.hasText(nomeBairro)) {
                    nomeBairro = safe(e.getTags().get("addr:suburb"));
                }
            }

            if (!StringUtils.hasText(nomeBairro)) {
                continue;
            }

            Double lat = null;
            Double lon = null;

            if (e.getLat() != null && e.getLon() != null) {
                lat = e.getLat();
                lon = e.getLon();
            } else if (e.getCenter() != null && e.getCenter().getLat() != null && e.getCenter().getLon() != null) {
                lat = e.getCenter().getLat();
                lon = e.getCenter().getLon();
            }

            if (lat == null || lon == null) {
                continue;
            }

            int dist = (int) Math.round(haversineMeters(latitude, longitude, lat, lon));

            EnderecoBairroProximoDTO dto = new EnderecoBairroProximoDTO();
            dto.setBairro(nomeBairro.trim());
            dto.setLatitude(lat);
            dto.setLongitude(lon);
            dto.setDistanciaMetros(dist);

            String key = normalizar(dto.getBairro());

            EnderecoBairroProximoDTO ja = dedup.get(key);
            if (ja == null) {
                dedup.put(key, dto);
            } else {
                Integer dOld = ja.getDistanciaMetros();
                Integer dNew = dto.getDistanciaMetros();
                if (dOld == null || (dNew != null && dNew < dOld)) {
                    dedup.put(key, dto);
                }
            }
        }

        List<EnderecoBairroProximoDTO> out = new ArrayList<>(dedup.values());
        out.sort(Comparator.comparingInt(d -> d.getDistanciaMetros() == null ? Integer.MAX_VALUE : d.getDistanciaMetros()));

        if (out.size() > lim) {
            out = out.subList(0, lim);
        }

        return out;
    }

    private ViaCepResponse buscarViaCep(String cep8) {

        try {
            return http.get()
                .uri("https://viacep.com.br/ws/{cep}/json/", cep8)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ViaCepResponse.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falha ao consultar CEP (ViaCEP)");
        }
    }

    private NominatimSearchItem geocodificarNominatim(String query) {

        if (!StringUtils.hasText(query)) {
            return null;
        }

        try {
            NominatimSearchItem[] items = http.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("nominatim.openstreetmap.org")
                    .path("/search")
                    .queryParam("format", "json")
                    .queryParam("limit", 1)
                    .queryParam("addressdetails", 1)
                    .queryParam("q", query)
                    .build()
                )
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(NominatimSearchItem[].class);

            if (items == null || items.length == 0) {
                return null;
            }

            return items[0];

        } catch (Exception ex) {
            return null;
        }
    }

    private String montarQueryGeocode(EnderecoResolvidoDTO end, String cep8) {

        StringBuilder sb = new StringBuilder();

        if (StringUtils.hasText(end.getLogradouro())) {
            sb.append(end.getLogradouro()).append(", ");
        }
        if (StringUtils.hasText(end.getBairro())) {
            sb.append(end.getBairro()).append(", ");
        }

        sb.append(end.getCidade()).append(", ").append(end.getUf());
        sb.append(", Brasil");
        sb.append(", ").append(cep8.substring(0, 5)).append("-").append(cep8.substring(5));

        return sb.toString();
    }

    private OverpassResponse consultarOverpassBairros(Double lat, Double lon, int raioMetros) {

        String query =
            "[out:json][timeout:25];" +
            "(" +
            "node[\"place\"~\"^(suburb|neighbourhood)$\"](around:" + raioMetros + "," + lat + "," + lon + ");" +
            "way[\"place\"~\"^(suburb|neighbourhood)$\"](around:" + raioMetros + "," + lat + "," + lon + ");" +
            "relation[\"place\"~\"^(suburb|neighbourhood)$\"](around:" + raioMetros + "," + lat + "," + lon + ");" +
            ");" +
            "out center;";

        try {
            return http.post()
                .uri("https://overpass-api.de/api/interpreter")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body("data=" + urlEncode(query))
                .retrieve()
                .body(OverpassResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String safe(String s) {
        if (!StringUtils.hasText(s)) return null;
        return s.trim();
    }

    private String somenteDigitos(String s) {
        if (s == null) return null;
        String v = s.replaceAll("\\D", "");
        return StringUtils.hasText(v) ? v : null;
    }

    private Double parseDoubleSafe(String v) {
        if (!StringUtils.hasText(v)) return null;
        try {
            return Double.parseDouble(v);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizar(String s) {
        if (!StringUtils.hasText(s)) return "";
        String v = s.trim().toLowerCase(Locale.ROOT);
        v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        v = v.replaceAll("[^a-z0-9\\s]", " ");
        v = v.replaceAll("\\s{2,}", " ").trim();
        return v;
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    
    
    private String montarQueryGeocodeSemLogradouro(EnderecoResolvidoDTO end, String cep8) {

        StringBuilder sb = new StringBuilder();

        if (StringUtils.hasText(end.getBairro())) {
            sb.append(end.getBairro()).append(", ");
        }

        sb.append(end.getCidade()).append(", ").append(end.getUf());
        sb.append(", Brasil");
        sb.append(", ").append(cep8.substring(0, 5)).append("-").append(cep8.substring(5));

        return sb.toString();
    }

    private String montarQueryGeocodeSomenteBairroCidadeUf(EnderecoResolvidoDTO end) {

        StringBuilder sb = new StringBuilder();

        if (StringUtils.hasText(end.getBairro())) {
            sb.append(end.getBairro()).append(", ");
        }

        sb.append(end.getCidade()).append(", ").append(end.getUf());
        sb.append(", Brasil");

        return sb.toString();
    }
    
    
    
    @Getter
    @Setter
    private static class ViaCepResponse {
        private String cep;
        private String logradouro;
        private String complemento;
        private String bairro;
        private String localidade;
        private String uf;
        private Boolean erro;
    }

    @Getter
    @Setter
    private static class NominatimSearchItem {
        private String lat;
        private String lon;
    }

    @Getter
    @Setter
    private static class OverpassResponse {
        private List<OverpassElement> elements;
    }

    @Getter
    @Setter
    private static class OverpassElement {
        private String type;
        private Long id;
        private Double lat;
        private Double lon;
        private OverpassCenter center;
        private Map<String, String> tags;
    }

    @Getter
    @Setter
    private static class OverpassCenter {
        private Double lat;
        private Double lon;
    }
}