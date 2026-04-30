package br.com.oraped.service.geolocalizacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.geolocalizacao.GeolocalizacaoCacheCoordenada;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.repository.geolocalizacao.GeolocalizacaoCacheCoordenadaRepository;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Gerenciar o cache persistente de resolução geográfica por coordenadas.
 *
 * Aplicação:
 * Utilizado antes das consultas ao provedor externo para evitar chamadas repetidas
 * ao OpenStreetMap/Nominatim em localizações iguais ou muito próximas.
 *
 * Utilização:
 * Deve ser a porta de entrada do reverse geocoding por latitude/longitude,
 * garantindo reaproveitamento entre usuários, sessões e reinicializações da aplicação.
 *
 * Observação:
 * Como este service grava cache mesmo quando é acionado a partir de fluxos de leitura,
 * ele precisa abrir sua própria transação de escrita para não herdar conexões read-only.
 */
@Service
@RequiredArgsConstructor
public class GeolocalizacaoCacheCoordenadaService {

    // 4 casas decimais reduzem chamadas repetidas e preservam precisão suficiente
    // para discovery por bairro em contexto urbano.
    private static final int SCALE_COORDENADA_CACHE = 4;

    // Bairro/cidade/UF mudam pouco. TTL longo reduz custo operacional sem impacto prático.
    private static final long TTL_DIAS = 180L;

    private final GeolocalizacaoCacheCoordenadaRepository repo;
    private final GeolocalizacaoProvider geolocalizacaoProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EnderecoResolvidoDTO resolverCoordenadasComCache(Double latitude, Double longitude) {

        validarCoordenadas(latitude, longitude);

        double latitudeArredondada = arredondar(latitude);
        double longitudeArredondada = arredondar(longitude);
        String chaveConsulta = montarChave(latitudeArredondada, longitudeArredondada);

        Optional<GeolocalizacaoCacheCoordenada> cacheExistente = repo.findByChaveConsultaAndExpiraEmAfter(
            chaveConsulta,
            OffsetDateTime.now()
        );

        if (cacheExistente.isPresent()) {
            GeolocalizacaoCacheCoordenada cache = cacheExistente.get();

            // Atualiza métricas de uso do cache para permitir monitoramento futuro.
            cache.setQuantidadeHits(cache.getQuantidadeHits() == null ? 1L : cache.getQuantidadeHits() + 1L);
            cache.setUltimaConsultaEm(OffsetDateTime.now());
            repo.save(cache);

            return converterCacheParaDto(cache);
        }

        EnderecoResolvidoDTO resolvido = geolocalizacaoProvider.resolverCoordenadas(latitude, longitude);

        GeolocalizacaoCacheCoordenada novoCache = new GeolocalizacaoCacheCoordenada();
        novoCache.setChaveConsulta(chaveConsulta);
        novoCache.setLatitudeArredondada(latitudeArredondada);
        novoCache.setLongitudeArredondada(longitudeArredondada);
        novoCache.setLatitudeOriginal(latitude);
        novoCache.setLongitudeOriginal(longitude);
        novoCache.setBairro(normalizarCampo(resolvido == null ? null : resolvido.getBairro()));
        novoCache.setCidade(normalizarCampo(resolvido == null ? null : resolvido.getCidade()));
        novoCache.setUf(normalizarUf(resolvido == null ? null : resolvido.getUf()));
        novoCache.setFonte("OPENSTREETMAP");
        novoCache.setQuantidadeHits(1L);
        novoCache.setPrimeiraConsultaEm(OffsetDateTime.now());
        novoCache.setUltimaConsultaEm(OffsetDateTime.now());
        novoCache.setExpiraEm(OffsetDateTime.now().plusDays(TTL_DIAS));

        repo.save(novoCache);

        return converterCacheParaDto(novoCache);
    }

    private EnderecoResolvidoDTO converterCacheParaDto(GeolocalizacaoCacheCoordenada cache) {

        EnderecoResolvidoDTO dto = new EnderecoResolvidoDTO();
        dto.setLatitude(cache.getLatitudeOriginal());
        dto.setLongitude(cache.getLongitudeOriginal());
        dto.setBairro(cache.getBairro());
        dto.setCidade(cache.getCidade());
        dto.setUf(cache.getUf());
        return dto;
    }

    private void validarCoordenadas(Double latitude, Double longitude) {

        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Latitude e longitude são obrigatórias"
            );
        }
    }

    private double arredondar(Double valor) {
        return BigDecimal.valueOf(valor)
            .setScale(SCALE_COORDENADA_CACHE, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private String montarChave(double latitude, double longitude) {
        return latitude + "|" + longitude;
    }

    private String normalizarCampo(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private String normalizarUf(String uf) {
        return StringUtils.hasText(uf) ? uf.trim().toUpperCase() : null;
    }
}