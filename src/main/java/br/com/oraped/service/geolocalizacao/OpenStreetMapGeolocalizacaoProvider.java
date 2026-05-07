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

/**
 * Finalidade:
 * Integrar a aplicação com serviços públicos baseados em OpenStreetMap
 * para resolução de CEP, coordenadas e bairros próximos.
 *
 * Aplicação:
 * Utilizado nos fluxos de geolocalização do marketplace e de entrega,
 * especialmente quando é necessário descobrir bairro, cidade e UF
 * a partir de latitude/longitude.
 *
 * Utilização:
 * Deve ser consumido preferencialmente por uma camada de cache, evitando
 * chamadas repetidas e respeitando limites operacionais do provedor externo.
 */
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
                String query2 = montarQueryGeocodeSemLogradouro(out, cepLimpo);
                best = geocodificarNominatim(query2);
            }

            if (best == null || best.getLat() == null || best.getLon() == null) {
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
    public EnderecoResolvidoDTO resolverCoordenadas(Double latitude, Double longitude) {

        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude/longitude são obrigatórios");
        }

        ReverseResponse reverse = reverseGeocode(latitude, longitude);

        if (reverse == null || reverse.getAddress() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não consegui resolver a localização informada");
        }

        ReverseAddress address = reverse.getAddress();

        EnderecoResolvidoDTO dto = new EnderecoResolvidoDTO();
        dto.setLatitude(latitude);
        dto.setLongitude(longitude);
        dto.setBairro(extrairBairro(address));
        dto.setCidade(extrairCidade(address));
        dto.setUf(extrairUf(address));

        // Se o reverse não trouxer bairro, tentamos enriquecer usando os bairros próximos.
        if (!StringUtils.hasText(dto.getBairro())
            && StringUtils.hasText(dto.getCidade())
            && StringUtils.hasText(dto.getUf())
        ) {
            List<EnderecoBairroProximoDTO> bairros = buscarBairrosProximos(
                latitude,
                longitude,
                dto.getCidade(),
                dto.getUf(),
                1
            );

            if (!bairros.isEmpty() && StringUtils.hasText(bairros.get(0).getBairro())) {
                dto.setBairro(bairros.get(0).getBairro().trim());
            }
        }

        return dto;
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

        int lim = (limite <= 0) ? 60 : Math.min(limite, 100);

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

    private ReverseResponse reverseGeocode(Double latitude, Double longitude) {

        try {
            return http.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("nominatim.openstreetmap.org")
                    .path("/reverse")
                    .queryParam("format", "jsonv2")
                    .queryParam("lat", latitude)
                    .queryParam("lon", longitude)
                    .queryParam("addressdetails", 1)
                    .build()
                )
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ReverseResponse.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Falha ao resolver localização por coordenadas"
            );
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

    private String extrairBairro(ReverseAddress address) {

        String bairro = primeiroTextoComValor(
            address.getSuburb(),
            address.getNeighbourhood(),
            address.getQuarter(),
            address.getCityDistrict()
        );

        return safe(bairro);
    }

    private String extrairCidade(ReverseAddress address) {

        String cidade = primeiroTextoComValor(
            address.getCity(),
            address.getTown(),
            address.getVillage(),
            address.getMunicipality(),
            address.getCounty()
        );

        return safe(cidade);
    }

    private String extrairUf(ReverseAddress address) {

        String estado = safe(address.getState());

        if (!StringUtils.hasText(estado)) {
            return null;
        }

        return converterEstadoParaUf(estado);
    }

    private String converterEstadoParaUf(String estado) {

        String normalizado = normalizar(estado);

        return switch (normalizado) {
            case "acre" -> "AC";
            case "alagoas" -> "AL";
            case "amapa" -> "AP";
            case "amazonas" -> "AM";
            case "bahia" -> "BA";
            case "ceara" -> "CE";
            case "distrito federal" -> "DF";
            case "espirito santo" -> "ES";
            case "goias" -> "GO";
            case "maranhao" -> "MA";
            case "mato grosso" -> "MT";
            case "mato grosso do sul" -> "MS";
            case "minas gerais" -> "MG";
            case "para" -> "PA";
            case "paraiba" -> "PB";
            case "parana" -> "PR";
            case "pernambuco" -> "PE";
            case "piaui" -> "PI";
            case "rio de janeiro" -> "RJ";
            case "rio grande do norte" -> "RN";
            case "rio grande do sul" -> "RS";
            case "rondonia" -> "RO";
            case "roraima" -> "RR";
            case "santa catarina" -> "SC";
            case "sao paulo" -> "SP";
            case "sergipe" -> "SE";
            case "tocantins" -> "TO";
            default -> null;
        };
    }

    private String primeiroTextoComValor(String... valores) {

        if (valores == null) {
            return null;
        }

        for (String valor : valores) {
            if (StringUtils.hasText(valor)) {
                return valor.trim();
            }
        }

        return null;
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
    private static class ReverseResponse {
        private ReverseAddress address;
    }

    @Getter
    @Setter
    private static class ReverseAddress {
        private String suburb;
        private String neighbourhood;
        private String quarter;
        private String cityDistrict;
        private String city;
        private String town;
        private String village;
        private String municipality;
        private String county;
        private String state;
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