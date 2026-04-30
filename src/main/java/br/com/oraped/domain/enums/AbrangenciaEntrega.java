package br.com.oraped.domain.enums;

public enum AbrangenciaEntrega {

    // A loja só atua em bairros previamente atendidos e pode ter taxa configurada por bairro.
    BAIRRO,

    // A loja atua em toda a cidade do seu bairro base.
    // A taxa não é previamente definida por bairro.
    CIDADE,

    // A loja atua em todo o estado (UF) do seu bairro base.
    // A taxa não é previamente definida por bairro.
    ESTADO,

    // A loja atua em todo o território nacional.
    // A taxa não é previamente definida por bairro.
    NACIONAL
}