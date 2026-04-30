package br.com.oraped.dto.whatsapp.entrada;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MensagemWhatsappEntradaDTO {

    private String phoneNumberId;

    // Número do cliente (remetente).
    @NotBlank
    @Size(max = 30)
    private String whatsappCliente;

    // Número acionado pelo cliente.
    @NotBlank
    @Size(max = 30)
    private String whatsappReceptor;

    // Nome exibido no perfil do WhatsApp do cliente.
    @Size(max = 120)
    private String nomeClienteWhatsapp;

    // Texto livre digitado pelo usuário.
    @Size(max = 5000)
    private String texto;

    // Id do item clicado em lista/botão, normalmente no formato COMANDO|...
    @Size(max = 5000)
    private String comando;

    @Size(max = 120)
    private String idMensagem;

    @Size(max = 120)
    private String idCorrelacao;

    // =========================
    // MÍDIA - IMAGEM
    // =========================

    @Size(max = 40)
    private String tipoMidia;

    @Size(max = 200)
    private String idMidia;

    @Size(max = 120)
    private String mimeTypeMidia;

    @Size(max = 255)
    private String sha256Midia;

    @Size(max = 2000)
    private String urlMidia;

    // =========================
    // LOCALIZAÇÃO COMPARTILHADA
    // =========================

    // A Meta envia latitude/longitude quando o usuário compartilha localização.
    private Double latitudeLocalizacao;

    private Double longitudeLocalizacao;

    @Size(max = 255)
    private String nomeLocalizacao;

    @Size(max = 500)
    private String enderecoLocalizacao;

    private Object payloadOriginal;

    @JsonIgnore
    public String getTextoOuComando() {

        if (StringUtils.hasText(this.comando)) {
            return this.comando.trim();
        }

        if (StringUtils.hasText(this.texto)) {
            return this.texto.trim();
        }

        return null;
    }

    @JsonIgnore
    public String getTextoSeguro(int max) {
        String v = texto == null ? "" : texto;
        return v.length() <= max ? v : v.substring(0, max);
    }

    @JsonIgnore
    public boolean isMensagemImagem() {
        return "image".equalsIgnoreCase(tipoMidia) && StringUtils.hasText(idMidia);
    }

    @JsonIgnore
    public boolean isMensagemLocalizacao() {
        return latitudeLocalizacao != null && longitudeLocalizacao != null;
    }
}