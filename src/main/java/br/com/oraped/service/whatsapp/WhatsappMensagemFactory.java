// src/main/java/br/com/oraped/service/whatsapp/WhatsappMensagemFactory.java
package br.com.oraped.service.whatsapp;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.com.oraped.dto.whatsapp.saida.MensagemInterativaAcaoBotoesWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaAcaoListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaCorpoWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaSecaoListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemTextoWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;

@Component
public class WhatsappMensagemFactory {

    // =========================================================
    // BUILDERS PRINCIPAIS (WhatsApp Cloud API)
    // =========================================================

    public MensagemWhatsappSaidaDTO texto(String whatsappDestino, String corpo) {

        return MensagemWhatsappSaidaDTO.builder()
            .messagingProduct("whatsapp")
            .to(normalizarSomenteDigitos(whatsappDestino))
            .type("text")
            .text(MensagemTextoWhatsappDTO.builder()
                .body(trunc(corpo, 4096))
                .previewUrl(false)
                .build())
            .build();
    }

    public MensagemWhatsappSaidaDTO lista(
        String whatsappDestino,
        String textoCabecalho,
        String tituloBotao,
        String tituloSecao,
        List<MensagemInterativaItemListaWhatsappDTO> itens
    ) {

        List<MensagemInterativaItemListaWhatsappDTO> rows = itens == null ? List.of() : itens;

        return MensagemWhatsappSaidaDTO.builder()
            .messagingProduct("whatsapp")
            .to(normalizarSomenteDigitos(whatsappDestino))
            .type("interactive")
            .interactive(
                MensagemInterativaWhatsappDTO.builder()
                    .type("list")
                    .body(MensagemInterativaCorpoWhatsappDTO.builder()
                        .text(trunc(textoCabecalho, 1024))
                        .build())
                    .action(
                        MensagemInterativaAcaoListaWhatsappDTO.builder()
                            .tituloBotao(truncWord(tituloBotao, 20))
                            .secoes(List.of(
                                MensagemInterativaSecaoListaWhatsappDTO.builder()
                                    .title(truncWord(tituloSecao, 24))
                                    .rows(rows)
                                    .build()
                            ))
                            .build()
                    )
                    .build()
            )
            .build();
    }

    public MensagemWhatsappSaidaDTO botoes(
        String whatsappDestino,
        String corpo,
        List<MensagemInterativaBotaoReplyWhatsappDTO> botoesReply
    ) {

        List<MensagemInterativaBotaoReplyWhatsappDTO> base = botoesReply == null ? List.of() : botoesReply;

        List<MensagemInterativaBotaoWhatsappDTO> buttons = base.stream()
            .filter(Objects::nonNull)
            .map(b -> MensagemInterativaBotaoWhatsappDTO.builder()
                .type("reply")
                .reply(b)
                .build())
            .collect(Collectors.toList());

        return MensagemWhatsappSaidaDTO.builder()
            .messagingProduct("whatsapp")
            .to(normalizarSomenteDigitos(whatsappDestino))
            .type("interactive")
            .interactive(
                MensagemInterativaWhatsappDTO.builder()
                    .type("button")
                    .body(MensagemInterativaCorpoWhatsappDTO.builder()
                        .text(trunc(corpo, 1024))
                        .build())
                    .action(
                        MensagemInterativaAcaoBotoesWhatsappDTO.builder()
                            .buttons(buttons)
                            .build()
                    )
                    .build()
            )
            .build();
    }

    // =========================================================
    // HELPERS REUTILIZÁVEIS (utilitários comuns)
    // =========================================================

    public String formatarMoeda(BigDecimal v) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return nf.format(v == null ? BigDecimal.ZERO : v);
    }

    public String safe(String s) {
        return s == null ? "" : s.trim();
    }

    public String trunc(String s, int max) {
        if (s == null) return "";
        String v = s.trim();
        if (v.length() <= max) return v;
        return v.substring(0, max);
    }

    public String truncWord(String s, int max) {
        if (s == null) return "";
        String v = s.trim();
        if (v.length() <= max) return v;
        String cut = v.substring(0, max);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace >= 10) return cut.substring(0, lastSpace);
        return cut;
    }

    public String normalizarSomenteDigitos(String whatsapp) {
        if (!StringUtils.hasText(whatsapp)) return null;
        String d = whatsapp.replaceAll("\\D+", "").trim();
        return StringUtils.hasText(d) ? d : null;
    }
}