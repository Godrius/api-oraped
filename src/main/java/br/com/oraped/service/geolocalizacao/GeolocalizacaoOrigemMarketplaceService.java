package br.com.oraped.service.geolocalizacao;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Resolver a localização atual do cliente no fluxo de marketplace
 * a partir das coordenadas compartilhadas no WhatsApp ou do CEP digitado.
 *
 * Aplicação:
 * Utilizado no discovery para descobrir bairro, cidade e UF do cliente,
 * servindo como base para a filtragem dos estabelecimentos disponíveis.
 *
 * Utilização:
 * Deve ser usado pelos services de marketplace no momento da seleção
 * de categorias e estabelecimentos, sempre passando antes pelos caches
 * persistentes de coordenadas e CEP.
 */
@Service
@RequiredArgsConstructor
public class GeolocalizacaoOrigemMarketplaceService {

    private final GeolocalizacaoCacheCoordenadaService cacheService;
    private final GeolocalizacaoCacheCepService cacheCepService;

    public EnderecoResolvidoDTO resolverOrigemCliente(Double latitude, Double longitude) {

        EnderecoResolvidoDTO dto = cacheService.resolverCoordenadasComCache(latitude, longitude);

        validarEnderecoResolvido(dto, "localização informada");

        return dto;
    }

    public EnderecoResolvidoDTO resolverOrigemClientePorCep(String cep) {

        String cepNormalizado = normalizarCep(cep);

        if (!StringUtils.hasText(cepNormalizado) || cepNormalizado.length() != 8) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Não consegui identificar um CEP válido"
            );
        }

        EnderecoResolvidoDTO dto = cacheCepService.resolverCepComCache(cepNormalizado);

        validarEnderecoResolvido(dto, "CEP informado");

        if (dto.getLatitude() == null || dto.getLongitude() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Não consegui obter coordenadas a partir do CEP informado"
            );
        }

        return dto;
    }

    private void validarEnderecoResolvido(EnderecoResolvidoDTO dto, String origem) {

        if (dto == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Não consegui resolver a " + origem
            );
        }

        if (!StringUtils.hasText(dto.getCidade()) || !StringUtils.hasText(dto.getUf())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Não consegui identificar cidade e UF da " + origem
            );
        }
    }

    private String normalizarCep(String cep) {

        if (!StringUtils.hasText(cep)) {
            return null;
        }

        return cep.replaceAll("\\D", "");
    }
}