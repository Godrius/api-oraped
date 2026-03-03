package br.com.oraped.domain.whatsapp;
public class SessaoEncerradaEvent {

    private final Long idSessao;

    public SessaoEncerradaEvent(Long idSessao) {
        this.idSessao = idSessao;
    }

    public Long getIdSessao() {
        return idSessao;
    }
}