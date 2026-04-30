package br.com.oraped.domain.enums;

public enum TipoDiretorioFtp {

    PRODUTO_FOTO;

    public static TipoDiretorioFtp fromString(String value) {

        if (value != null) {
            for (TipoDiretorioFtp tipo : TipoDiretorioFtp.values()) {
                if (tipo.name().equalsIgnoreCase(value)) {
                    return tipo;
                }
            }
        }

        throw new IllegalArgumentException("Valor inválido para TipoDiretorioFtp: " + value);
    }
}