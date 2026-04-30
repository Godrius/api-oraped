package br.com.oraped.service.whatsapp.orquestrador;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.dto.whatsapp.entrada.MensagemWhatsappEntradaDTO;

@Service
public class OrquestradorParseService {

    public String safeTextoEntrada(MensagemWhatsappEntradaDTO req) {
        String v = req.getTextoOuComando();
        if (!StringUtils.hasText(v)) return "(vazio)";
        return v.length() <= 5000 ? v : v.substring(0, 5000);
    }

    public boolean hasLocalizacaoCompartilhada(MensagemWhatsappEntradaDTO req) {
        return req != null && req.isMensagemLocalizacao();
    }

    public Double getLatitudeLocalizacao(MensagemWhatsappEntradaDTO req) {
        if (req == null || req.getLatitudeLocalizacao() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude da localização é obrigatória");
        }
        return req.getLatitudeLocalizacao();
    }

    public Double getLongitudeLocalizacao(MensagemWhatsappEntradaDTO req) {
        if (req == null || req.getLongitudeLocalizacao() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "longitude da localização é obrigatória");
        }
        return req.getLongitudeLocalizacao();
    }

    public Long parseLongObrigatorio(String v, String nomeCampo) {
        if (!StringUtils.hasText(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nomeCampo + " é obrigatório");
        }
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nomeCampo + " inválido");
        }
    }

    public Integer parseIntObrigatorio(String v, String nomeCampo) {
        if (!StringUtils.hasText(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nomeCampo + " é obrigatório");
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nomeCampo + " inválido");
        }
    }

    public Integer parseIntDefaultZero(String raw) {
        if (!StringUtils.hasText(raw)) return 0;
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(v, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public Integer parseIntDefaultZeroAllowNegative(String raw) {
        if (!StringUtils.hasText(raw)) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public StatusPedido parseStatusPedidoObrigatorio(String raw) {

        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "statusPedido é obrigatório");
        }

        String v = raw.trim().toUpperCase(Locale.ROOT);

        try {
            return StatusPedido.valueOf(v);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "statusPedido inválido");
        }
    }

    public FormaPagamentoPedido parseFormaPagamento(String tipo) {

        if (!StringUtils.hasText(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formaPagamento é obrigatória");
        }

        String t = tipo.trim().toUpperCase(Locale.ROOT);

        if ("DINHEIRO".equals(t)) return FormaPagamentoPedido.DINHEIRO;
        if ("CREDITO".equals(t) || "CRÉDITO".equals(t)) return FormaPagamentoPedido.CREDITO;
        if ("DEBITO_PIX".equals(t) || "DEBITO".equals(t) || "DÉBITO".equals(t) || "PIX".equals(t)) return FormaPagamentoPedido.DEBITO_PIX;

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formaPagamento inválida");
    }

    public BigDecimal parseValorMonetario(String raw) {

        if (!StringUtils.hasText(raw)) return null;

        String s = raw.trim()
            .replace("R$", "")
            .replace("r$", "")
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".");

        s = s.replaceAll("[^0-9.]", "");

        if (!StringUtils.hasText(s)) return null;

        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    public int indexOfAny(String haystackLower, String... needlesLower) {
        int best = -1;
        for (String n : needlesLower) {
            int i = haystackLower.indexOf(n);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }
}