package br.com.oraped.dto;

import br.com.oraped.domain.AdministradorEstabelecimento;
import lombok.Data;

@Data
public class AdministradorResumoDTO {

  private String nome;
  private String whatsapp;

  public AdministradorResumoDTO(AdministradorEstabelecimento a) {
    this.nome = a.getNome();
    this.whatsapp = a.getWhatsapp();
  }
}
