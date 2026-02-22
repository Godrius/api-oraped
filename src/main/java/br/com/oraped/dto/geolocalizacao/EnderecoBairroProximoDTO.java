package br.com.oraped.dto.geolocalizacao;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EnderecoBairroProximoDTO {

    private String bairro;

    private Integer distanciaMetros;

    private Double latitude;
    private Double longitude;
}