package br.com.oraped.service.whatsapp.administrador;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdministradorWhatsappSupport {

    private final WhatsappMensagemFactory msg;

    public WhatsappMensagemFactory msg() {
        return msg;
    }

    public void validarBasico(Estabelecimento estabelecimento, String whatsappAdmin) {
        if (estabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }
        if (!StringUtils.hasText(whatsappAdmin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappAdmin é obrigatório");
        }
    }

    public MensagemInterativaItemListaWhatsappDTO row(String id, String title, String description) {
        return MensagemInterativaItemListaWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 24))
            .description(msg.trunc(msg.safe(description), 72))
            .build();
    }

    public MensagemInterativaBotaoReplyWhatsappDTO btn(String id, String title) {
        return MensagemInterativaBotaoReplyWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 20))
            .build();
    }

    public String formatarCepParaExibicao(String cep) {
        if (!StringUtils.hasText(cep)) return "(não informado)";

        String v = msg.normalizarSomenteDigitos(cep);
        if (!StringUtils.hasText(v) || v.length() != 8) {
            return msg.safe(cep);
        }

        return v.substring(0, 5) + "-" + v.substring(5);
    }

    public String normalizarCepDigitado(String texto) {
        if (!StringUtils.hasText(texto)) {
            return null;
        }

        String v = texto.replaceAll("\\D", "");
        if (v.length() != 8) {
            return null;
        }

        return v;
    }
}