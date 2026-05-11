package br.com.oraped.service.whatsapp.cliente;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorMensagemHelperService;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável por montar as mensagens do fluxo de endereço de entrega do cliente no WhatsApp.
 * Centraliza textos de CEP, endereço anterior, complemento e fallback de endereço completo.
 */
@Service
@RequiredArgsConstructor
public class EnderecoClienteService {

    private final WhatsappMensagemFactory msg;
    private final OrquestradorMensagemHelperService helperService;

    public MensagemWhatsappSaidaDTO montarSugestaoEnderecoAnterior(
        String whatsappCliente,
        String enderecoAnterior,
        BigDecimal taxaEntrega
    ) {

        BigDecimal taxa = taxaEntrega == null ? BigDecimal.ZERO : taxaEntrega;

        String corpo =
            "Encontrei um endereço usado no seu último pedido:\n\n" +
                msg.trunc(enderecoAnterior, 900) + "\n\n" +
                "🚚 Taxa de entrega para este pedido: " + msg.formatarMoeda(taxa) + "\n\n" +
                "Deseja usar esse mesmo?";

        return msg.botoes(
            whatsappCliente,
            msg.trunc(corpo, 1024),
            List.of(
                helperService.btn("COMANDO|FAZER_PEDIDO_COM_ENDERECO_ANTERIOR", "✅ Usar esse mesmo"),
                helperService.btn("COMANDO|INFORMAR_OUTRO_ENDERECO", "✏️ Alterar endereço")
            )
        );
    }

    public MensagemWhatsappSaidaDTO montarSolicitacaoEnderecoEntrega(String whatsappCliente) {

        String corpo =
            "Perfeito! ✅ Agora me informe o *endereço de entrega*.\n\n" +
                "Inclua também *observações úteis pro entregador*, como:\n" +
                "- ponto de referência\n" +
                "- bloco/apto\n" +
                "- interfone\n" +
                "- portaria / instruções de acesso\n\n" +
                "Exemplo:\n" +
                "Rua X, 123 - Apto 45, Bairro Y. Obs: interfone 45, portaria 24h.";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }

    public MensagemWhatsappSaidaDTO montarSolicitacaoCepEntrega(
	    String whatsappCliente,
	    boolean trocaCep
	) {

	    String introducao = trocaCep
	        ? "Perfeito! ✅\n\nMe informe o novo *CEP de entrega*.\n\n"
	        : "Perfeito! ✅\n\nAgora me informe o *CEP de entrega*.\n\n";

	    String corpo =
	        introducao +
	            "Exemplos:\n" +
	            "24350-000\n" +
	            "ou\n" +
	            "24350000";

	    return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
	}

    public MensagemWhatsappSaidaDTO montarEnderecoEncontradoSolicitarComplemento(
	    String whatsappCliente,
	    String enderecoBase
	) {

	    String corpo =
	        "Encontrei este endereço pelo CEP:\n\n" +
	            "*" + msg.trunc(enderecoBase, 500) + "*\n\n" +
	            "Agora me informe o *complemento* (número, apto/bloco, ponto de referência, etc.).\n\n" +
	            "Você pode incluir observações assim:\n" +
	            "- Obs: interfone 45\n" +
	            "- Obs: portaria 24h";

	    return msg.botoes(
	        whatsappCliente,
	        msg.trunc(corpo, 1024),
	        List.of(
	            helperService.btn(
	                "COMANDO|INFORMAR_OUTRO_ENDERECO",
	                "📮 Usar outro CEP"
	            )
	        )
	    );
	}

    public MensagemWhatsappSaidaDTO montarSolicitacaoEnderecoCompletoFallback(String whatsappCliente) {

        String corpo =
            "Não consegui localizar o endereço pelo CEP 😕\n\n" +
                "Por favor, me envie o *endereço completo* (rua, número, bairro) e, se quiser, observações pro entregador.\n\n" +
                "Exemplo:\n" +
                "Rua X, 123 - Apto 45, Bairro Y. Obs: interfone 45, portaria 24h.";

        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
    }
}