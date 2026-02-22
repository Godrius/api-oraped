package br.com.oraped.dto.geolocalizacao;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EnderecoResolvidoDTO {

    private String cep;
    private String logradouro;
    private String bairro;
    private String cidade;
    private String uf;

    private Double latitude;
    private Double longitude;
}