package br.com.oraped.service.geolocalizacao;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.geolocalizacao.GeolocalizacaoCacheCep;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.repository.geolocalizacao.GeolocalizacaoCacheCepRepository;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Gerenciar o cache persistente de resolução geográfica por CEP.
 *
 * Aplicação:
 * Utilizado antes das consultas ao provedor externo para evitar chamadas repetidas
 * ao resolver CEPs já consultados anteriormente no fluxo de marketplace.
 *
 * Utilização:
 * Deve ser a porta de entrada da resolução por CEP, garantindo reaproveitamento
 * entre usuários, sessões e reinicializações da aplicação.
 *
 * Observação:
 * Como este service grava cache mesmo quando é acionado a partir de fluxos de leitura,
 * ele precisa abrir sua própria transação de escrita para não herdar conexões read-only.
 */
@Service
@RequiredArgsConstructor
public class GeolocalizacaoCacheCepService {

    // Endereços por CEP tendem a ser extremamente estáveis. TTL longo reduz custo sem prejuízo prático.
    private static final long TTL_DIAS = 180L;

    private final GeolocalizacaoCacheCepRepository repo;
    private final GeolocalizacaoProvider geolocalizacaoProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EnderecoResolvidoDTO resolverCepComCache(String cep) {

        String cepNormalizado = normalizarCep(cep);
        validarCep(cepNormalizado);

        Optional<GeolocalizacaoCacheCep> cacheExistente = repo.findByCepAndExpiraEmAfter(
            cepNormalizado,
            OffsetDateTime.now()
        );

        if (cacheExistente.isPresent()) {
            GeolocalizacaoCacheCep cache = cacheExistente.get();

            // Atualiza métricas de uso para permitir monitoramento e futura limpeza inteligente.
            cache.setQuantidadeHits(cache.getQuantidadeHits() == null ? 1L : cache.getQuantidadeHits() + 1L);
            cache.setUltimaConsultaEm(OffsetDateTime.now());
            repo.save(cache);

            return converterCacheParaDto(cache);
        }

        EnderecoResolvidoDTO resolvido = geolocalizacaoProvider.resolverCep(cepNormalizado);

        GeolocalizacaoCacheCep novoCache = new GeolocalizacaoCacheCep();
        novoCache.setCep(cepNormalizado);
        novoCache.setLogradouro(normalizarCampo(resolvido == null ? null : resolvido.getLogradouro()));
        novoCache.setBairro(normalizarCampo(resolvido == null ? null : resolvido.getBairro()));
        novoCache.setCidade(normalizarCampo(resolvido == null ? null : resolvido.getCidade()));
        novoCache.setUf(normalizarUf(resolvido == null ? null : resolvido.getUf()));
        novoCache.setLatitude(resolvido == null ? null : resolvido.getLatitude());
        novoCache.setLongitude(resolvido == null ? null : resolvido.getLongitude());
        novoCache.setFonte("CEP_PROVIDER");
        novoCache.setQuantidadeHits(1L);
        novoCache.setPrimeiraConsultaEm(OffsetDateTime.now());
        novoCache.setUltimaConsultaEm(OffsetDateTime.now());
        novoCache.setExpiraEm(OffsetDateTime.now().plusDays(TTL_DIAS));

        repo.save(novoCache);

        return converterCacheParaDto(novoCache);
    }

    private EnderecoResolvidoDTO converterCacheParaDto(GeolocalizacaoCacheCep cache) {

        EnderecoResolvidoDTO dto = new EnderecoResolvidoDTO();
        dto.setCep(cache.getCep());
        dto.setLogradouro(cache.getLogradouro());
        dto.setBairro(cache.getBairro());
        dto.setCidade(cache.getCidade());
        dto.setUf(cache.getUf());
        dto.setLatitude(cache.getLatitude());
        dto.setLongitude(cache.getLongitude());
        return dto;
    }

    private void validarCep(String cep) {

        if (!StringUtils.hasText(cep) || cep.length() != 8) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "CEP válido é obrigatório"
            );
        }
    }

    private String normalizarCep(String cep) {

        if (!StringUtils.hasText(cep)) {
            return null;
        }

        return cep.replaceAll("\\D", "");
    }

    private String normalizarCampo(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private String normalizarUf(String uf) {
        return StringUtils.hasText(uf) ? uf.trim().toUpperCase() : null;
    }
}