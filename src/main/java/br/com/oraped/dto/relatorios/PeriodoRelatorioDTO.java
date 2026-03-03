package br.com.oraped.dto.relatorios;

import java.time.LocalDateTime;

public class PeriodoRelatorioDTO {

    private final LocalDateTime inicio;
    private final LocalDateTime fim;

    public PeriodoRelatorioDTO(LocalDateTime inicio, LocalDateTime fim) {
        this.inicio = inicio;
        this.fim = fim;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }
}