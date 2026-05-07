package br.com.oraped.service.whatsapp.cliente.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Rotear comandos de notificação solicitados pelo cliente no WhatsApp.
 *
 * Aplicação:
 * Usado quando o cliente deseja ser avisado sobre eventos do estabelecimento,
 * como a reabertura da loja.
 *
 * Utilização:
 * Mantém regras de notificação fora do roteador principal do cliente,
 * evitando misturar pedidos, carrinho, catálogo e alertas operacionais.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoNotificacaoClienteService {

    private final EstabelecimentoService estabelecimentoService;
    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        String phoneNumberId,
        String idCorrelacao,
        String wamidEntrada,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "CADASTRAR_NOTIFICACAO_ESTABELECIMENTO_ABERTO":
                return cadastrarNotificacaoEstabelecimentoAberto(
                    estabelecimento,
                    whatsappCliente,
                    phoneNumberId,
                    idCorrelacao,
                    wamidEntrada
                );

            default:
                return new RoteamentoResultado(
                    "notificacao_cliente_comando_desconhecido",
                    msg.texto(
                        whatsappCliente,
                        "⚠️ Não consegui identificar a ação de notificação.\n\nTente novamente."
                    )
                );
        }
    }

    private RoteamentoResultado cadastrarNotificacaoEstabelecimentoAberto(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        String phoneNumberId,
        String idCorrelacao,
        String wamidEntrada
    ) {

        if (estabelecimento == null) {
            return new RoteamentoResultado(
                "notificacao_estabelecimento_invalido",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar o estabelecimento desta conversa."
                )
            );
        }

        if (estabelecimento.isAberto()) {
            MensagemWhatsappSaidaDTO mensagem = msg.texto(
                whatsappCliente,
                "✅ O estabelecimento já está *aberto*.\n\n" +
                    "Você já pode fazer seu pedido agora. 🙂"
            );

            return new RoteamentoResultado(
                "notificacao_estabelecimento_ja_aberto",
                mensagem
            );
        }

        boolean criado = estabelecimentoService.solicitarNotificacaoQuandoAbrir(
            estabelecimento.getId(),
            whatsappCliente,
            phoneNumberId,
            wamidEntrada,
            idCorrelacao
        );

        MensagemWhatsappSaidaDTO mensagem = msg.texto(
            whatsappCliente,
            criado
                ? "✅ Combinado! Vou te avisar assim que o estabelecimento abrir."
                : "✅ Você já está na lista para ser avisado quando o estabelecimento abrir."
        );

        return new RoteamentoResultado(
            "notificacao_estabelecimento_cadastrada",
            mensagem
        );
    }
}