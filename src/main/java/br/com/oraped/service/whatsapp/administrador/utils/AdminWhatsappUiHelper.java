package br.com.oraped.service.whatsapp.administrador.utils;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Classe utilitária de apoio ao módulo administrativo do WhatsApp.
 *
 * Responsabilidades:
 * - padronizar criação de botões e itens de lista
 * - centralizar validações básicas comuns
 * - encapsular uso do WhatsappMensagemFactory
 * - evitar duplicação de código entre serviços admin
 *
 * Importante:
 * - não contém regra de negócio
 * - não acessa banco
 * - apenas utilidades e padronizações de saída
 */
@Component
@RequiredArgsConstructor
public class AdminWhatsappUiHelper {

    private final WhatsappMensagemFactory msg;

    /**
     * Exposição controlada do factory para evitar injeção duplicada
     * nos serviços administrativos.
     */
    public WhatsappMensagemFactory msg() {
        return msg;
    }

    // =========================================================
    // VALIDAÇÕES BÁSICAS
    // =========================================================

    /**
     * Validação mínima obrigatória para fluxos admin.
     * Garante consistência antes de executar qualquer ação.
     */
    public void validarBasico(Estabelecimento estabelecimento, String whatsappAdmin) {

        if (estabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }

        if (!StringUtils.hasText(whatsappAdmin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappAdmin é obrigatório");
        }
    }

    // =========================================================
    // CONSTRUÇÃO DE COMPONENTES WHATSAPP
    // =========================================================

    /**
     * Cria item de lista interativa com truncamento seguro.
     *
     * Regras:
     * - título: até 24 chars
     * - descrição: até 72 chars
     */
    public MensagemInterativaItemListaWhatsappDTO row(
        String id,
        String title,
        String description
    ) {

        return MensagemInterativaItemListaWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 24))
            .description(msg.trunc(msg.safe(description), 72))
            .build();
    }

    /**
     * Cria botão interativo padrão.
     *
     * Regra:
     * - título limitado a 20 chars (limite do WhatsApp)
     */
    public MensagemInterativaBotaoReplyWhatsappDTO btn(
        String id,
        String title
    ) {

        return MensagemInterativaBotaoReplyWhatsappDTO.builder()
            .id(id)
            .title(msg.trunc(msg.safe(title), 20))
            .build();
    }

    // =========================================================
    // CEP
    // =========================================================

    /**
     * Formata CEP para exibição no padrão XXXXX-XXX.
     *
     * Regras:
     * - aceita qualquer entrada
     * - tenta normalizar para 8 dígitos
     * - fallback: retorna valor original tratado
     */
    public String formatarCepParaExibicao(String cep) {

        if (!StringUtils.hasText(cep)) {
            return "(não informado)";
        }

        String v = msg.normalizarSomenteDigitos(cep);

        if (!StringUtils.hasText(v) || v.length() != 8) {
            return msg.safe(cep);
        }

        return v.substring(0, 5) + "-" + v.substring(5);
    }

    /**
     * Normaliza CEP digitado pelo usuário.
     *
     * Regras:
     * - remove tudo que não for número
     * - só aceita exatamente 8 dígitos
     * - retorna null se inválido
     */
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