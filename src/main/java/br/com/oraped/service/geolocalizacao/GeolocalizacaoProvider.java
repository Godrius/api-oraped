// src/main/java/br/com/oraped/service/geolocalizacao/GeolocalizacaoProvider.java
package br.com.oraped.service.geolocalizacao;

import java.util.List;

import br.com.oraped.dto.geolocalizacao.EnderecoBairroProximoDTO;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;

public interface GeolocalizacaoProvider {

    EnderecoResolvidoDTO resolverCep(String cep);

    List<EnderecoBairroProximoDTO> buscarBairrosProximos(
        Double latitude,
        Double longitude,
        String cidade,
        String uf,
        int limite
    );
}