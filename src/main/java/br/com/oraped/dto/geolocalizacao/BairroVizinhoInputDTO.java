package br.com.oraped.dto.geolocalizacao;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BairroVizinhoInputDTO {

    private Long idBairroVizinho;

    private Integer distanciaMetros;
}