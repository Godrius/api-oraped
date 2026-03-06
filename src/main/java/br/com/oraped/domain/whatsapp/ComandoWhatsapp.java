package br.com.oraped.domain.whatsapp;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

@Getter
public class ComandoWhatsapp {

  private final boolean ehComando;
  private final List<String> partes;

  private ComandoWhatsapp(boolean ehComando, List<String> partes) {
    this.ehComando = ehComando;
    this.partes = partes;
  }

  public static ComandoWhatsapp parse(String textoOuComando) {

    if (textoOuComando == null) {
      return new ComandoWhatsapp(false, List.of());
    }

    String v = textoOuComando.trim();
    if (!v.startsWith("COMANDO|")) {
      return new ComandoWhatsapp(false, List.of(v));
    }

    String[] raw = v.split("\\|");
    List<String> parts = new ArrayList<>();
    for (String p : raw) {
      parts.add(p == null ? "" : p.trim());
    }

    return new ComandoWhatsapp(true, parts);
  }

  public String getAcao() {
    // COMANDO|ACAO|...
    if (!ehComando || partes.size() < 2) return null;
    return partes.get(1);
  }

  public String getParte(int idx) {
    return partes.size() > idx ? partes.get(idx) : null;
  }
}