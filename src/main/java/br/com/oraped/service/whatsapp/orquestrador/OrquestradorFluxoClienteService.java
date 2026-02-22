// src/main/java/br/com/oraped/service/whatsapp/orquestrador/OrquestradorFluxoClienteService.java
package br.com.oraped.service.whatsapp.orquestrador;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.Produto;
import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.enums.TipoAtendimento;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.ClienteRequestDTO;
import br.com.oraped.dto.ItemPedidoRequestDTO;
import br.com.oraped.dto.PedidoRequestDTO;
import br.com.oraped.dto.PedidoResponseDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.ClienteService;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.PedidoService;
import br.com.oraped.service.whatsapp.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdministradorWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorFluxoClienteService {

    private final AdministradorWhatsappService administradorWhatsappService;
    private final EstabelecimentoService estabelecimentoService;
    private final ClienteService clienteService;
    private final PedidoService pedidoService;

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final OrquestradorCarrinhoService carrinhoService;
    private final OrquestradorMenusClienteService menusClienteService;
    private final OrquestradorExtracaoEstabelecimentoService extracaoService;
    private final OrquestradorParseService parseService;

    private final WhatsappMensagemFactory msg;
    private final OrquestradorMensagemHelperService helper;

    public RoteamentoResultado tratarFluxoEndereco(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        Map<Long, Integer> carrinho = carrinhoService.montarCarrinhoAtual(idSessao);

        if (carrinho.isEmpty()) {

            MensagemWhatsappSaidaDTO saida = msg.botoes(
                whatsappCliente,
                msg.trunc("Seu carrinho está vazio 🛒\n\nInclua pelo menos 1 item para concluir o pedido.", 1024),
                List.of(
                    helper.btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Incluir outro item"),
                    helper.btn("COMANDO|FAZER_PEDIDO", "🛍️ Fazer um pedido")
                )
            );

            return new RoteamentoResultado("bloqueio_carrinho_vazio", saida);
        }

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        if (StringUtils.hasText(s.getEnderecoEntrega())) {

            if (s.getFormaPagamento() == null) {
                sessaoService.marcarAguardandoFormaPagamento(idSessao);
                return new RoteamentoResultado(
                    "forma_pagamento_menu",
                    menusClienteService.montarEscolhaFormaPagamento(whatsappCliente)
                );
            }

            if (s.getFormaPagamento() == FormaPagamentoPedido.DINHEIRO
                && Boolean.TRUE.equals(s.getPrecisaTroco())
                && s.getTrocoPara() == null
            ) {
                sessaoService.marcarAguardandoTrocoValor(idSessao);
                return new RoteamentoResultado(
                    "troco_valor",
                    menusClienteService.montarSolicitacaoTrocoValor(whatsappCliente)
                );
            }

            return new RoteamentoResultado(
                "confirmacao_final",
                menusClienteService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s)
            );
        }

        String enderecoAnterior = clienteService
            .buscarUltimoEnderecoEntrega(estabelecimento, whatsappCliente)
            .orElse(null);

        if (StringUtils.hasText(enderecoAnterior)) {
            return new RoteamentoResultado(
                "sugestao_endereco_anterior",
                menusClienteService.montarSugestaoEnderecoAnterior(whatsappCliente, enderecoAnterior)
            );
        }

        sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
        return new RoteamentoResultado(
            "solicitar_endereco_entrega",
            menusClienteService.montarSolicitacaoEnderecoEntrega(whatsappCliente)
        );
    }

    public RoteamentoResultado tratarConfirmacaoEnderecoAnterior(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        String whatsappReceptor,
        String phoneNumberId,
        Long idSessao
    ) {

        Map<Long, Integer> carrinho = carrinhoService.montarCarrinhoAtual(idSessao);

        if (carrinho.isEmpty()) {
            return new RoteamentoResultado(
                "bloqueio_carrinho_vazio",
                msg.texto(whatsappCliente, "Seu carrinho está vazio 🛒\n\nInclua itens antes de enviar o pedido.")
            );
        }

        String enderecoAnterior = clienteService
            .buscarUltimoEnderecoEntrega(estabelecimento, whatsappCliente)
            .orElse(null);

        if (!StringUtils.hasText(enderecoAnterior)) {
            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);

            MensagemWhatsappSaidaDTO m = menusClienteService.montarSolicitacaoEnderecoEntrega(whatsappCliente);
            return new RoteamentoResultado("solicitar_endereco_entrega", m);
        }

        sessaoService.salvarEnderecoEntrega(idSessao, enderecoAnterior, null);
        sessaoService.marcarAguardandoFormaPagamento(idSessao);

        MensagemWhatsappSaidaDTO m = menusClienteService.montarEscolhaFormaPagamento(whatsappCliente);
        return new RoteamentoResultado("forma_pagamento_menu", m);
    }

    public RoteamentoResultado tratarSelecaoPagamento(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        String whatsappReceptor,
        String phoneNumberId,
        Long idSessao,
        FormaPagamentoPedido fp
    ) {

        if (fp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formaPagamento é obrigatória");
        }

        sessaoService.salvarFormaPagamento(idSessao, fp);

        if (fp == FormaPagamentoPedido.DINHEIRO) {
            sessaoService.marcarAguardandoTrocoConfirmacao(idSessao);
            MensagemWhatsappSaidaDTO m = menusClienteService.montarPerguntaTrocoSimNao(whatsappCliente);
            return new RoteamentoResultado("troco_confirmacao", m);
        }

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        sessaoService.marcarAguardandoConfirmacaoFinal(idSessao);
        MensagemWhatsappSaidaDTO m = menusClienteService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);

        return new RoteamentoResultado("confirmacao_final", m);
    }

    public RoteamentoResultado tratarTroco(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        String whatsappReceptor,
        String phoneNumberId,
        Long idSessao,
        String escolha
    ) {

        if ("NAO".equalsIgnoreCase(escolha)) {
            sessaoService.salvarTrocoNecessidade(idSessao, false);

            SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

            sessaoService.marcarAguardandoConfirmacaoFinal(idSessao);
            MensagemWhatsappSaidaDTO m = menusClienteService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);

            return new RoteamentoResultado("confirmacao_final", m);
        }

        if ("SIM".equalsIgnoreCase(escolha)) {
            sessaoService.salvarTrocoNecessidade(idSessao, true);
            sessaoService.marcarAguardandoTrocoValor(idSessao);

            MensagemWhatsappSaidaDTO m = menusClienteService.montarSolicitacaoTrocoValor(whatsappCliente);
            return new RoteamentoResultado("troco_valor", m);
        }

        sessaoService.marcarAguardandoTrocoConfirmacao(idSessao);

        MensagemWhatsappSaidaDTO m = menusClienteService.montarPerguntaTrocoSimNao(whatsappCliente);
        return new RoteamentoResultado("troco_confirmacao", m);
    }

    public RoteamentoResultado tratarEnvioPedidoDefinitivo(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao
    ) {

        Map<Long, Integer> carrinho = carrinhoService.montarCarrinhoAtual(idSessao);

        if (carrinho.isEmpty()) {
            return new RoteamentoResultado(
                "bloqueio_carrinho_vazio",
                msg.texto(whatsappCliente, "Seu carrinho está vazio 🛒\n\nInclua itens antes de enviar o pedido.")
            );
        }

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        if (!StringUtils.hasText(s.getEnderecoEntrega())) {
            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
            return new RoteamentoResultado(
                "solicitar_endereco_entrega",
                menusClienteService.montarSolicitacaoEnderecoEntrega(whatsappCliente)
            );
        }

        if (s.getFormaPagamento() == null) {
            sessaoService.marcarAguardandoFormaPagamento(idSessao);
            return new RoteamentoResultado(
                "forma_pagamento_menu",
                menusClienteService.montarEscolhaFormaPagamento(whatsappCliente)
            );
        }

        if (s.getFormaPagamento() == FormaPagamentoPedido.DINHEIRO
            && Boolean.TRUE.equals(s.getPrecisaTroco())
            && s.getTrocoPara() == null
        ) {
            sessaoService.marcarAguardandoTrocoValor(idSessao);
            return new RoteamentoResultado(
                "troco_valor",
                menusClienteService.montarSolicitacaoTrocoValor(whatsappCliente)
            );
        }

        return enviarPedidoDefinitivo(estabelecimento, whatsappCliente, idSessao, carrinho);
    }

    public RoteamentoResultado tratarAdicionarProduto(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idProduto,
        Integer quantidade
    ) {

        Produto produto = extracaoService.extrairProduto(estabelecimento, idProduto);

        if (produto == null) {
            return new RoteamentoResultado(
                "produto_nao_encontrado",
                msg.texto(whatsappCliente, "Produto não encontrado.")
            );
        }

        int qtd = quantidade == null ? 0 : quantidade;
        if (qtd < 1) {
            return new RoteamentoResultado(
                "quantidade_invalida",
                msg.texto(whatsappCliente, "Quantidade inválida.")
            );
        }

        String nome = msg.safe(produto.getNome());
        String descricao = msg.safe(produto.getDescricao());
        BigDecimal total = menusClienteService.calcularPrecoPorQuantidade(produto, qtd);

        StringBuilder sb = new StringBuilder();
        sb.append("Perfeito! ✅\n");
        sb.append("Adicionado ao carrinho:\n\n");
        sb.append("*").append(nome).append("*\n");
        if (StringUtils.hasText(descricao)) sb.append(descricao).append("\n");
        sb.append("Quantidade: ").append(qtd).append("\n");
        sb.append("Valor total: ").append(msg.formatarMoeda(total)).append("\n\n");
        sb.append("Vamos continuar seu pedido 👇");

        MensagemWhatsappSaidaDTO saida = msg.botoes(
            whatsappCliente,
            msg.trunc(sb.toString(), 1024),
            List.of(
                helper.btn("COMANDO|INCLUIR_OUTRO_ITEM", "➕ Comprar mais"),
                helper.btn("COMANDO|VISUALIZAR_CARRINHO", "🛒 Ver o carrinho"),
                helper.btn("COMANDO|INFORMAR_ENDERECO", "🏍️ Ir para entrega")
            )
        );

        return new RoteamentoResultado("produto_adicionado", saida);
    }

    public MensagemWhatsappSaidaDTO tratarEnderecoEntregaInformado(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        String textoCliente
    ) {

        String raw = msg.safe(textoCliente);

        if (!StringUtils.hasText(raw) || "(vazio)".equals(raw)) {
            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
            return msg.texto(
                whatsappCliente,
                "Não consegui identificar o endereço 😕\n\n" +
                    "Por favor, me envie o endereço de entrega (com número) e observações pro entregador."
            );
        }

        ParsedEndereco parsed = parseEnderecoEObservacoes(raw);

        if (!StringUtils.hasText(parsed.endereco)) {
            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
            return msg.texto(
                whatsappCliente,
                "Não consegui identificar o endereço 😕\n\n" +
                    "Me envie o endereço (rua, número, bairro) e, se quiser, observações pro entregador."
            );
        }

        sessaoService.salvarEnderecoEntrega(idSessao, parsed.endereco, parsed.observacoes);
        sessaoService.desmarcarAguardandoEnderecoEntrega(idSessao);

        sessaoService.marcarAguardandoFormaPagamento(idSessao);
        return menusClienteService.montarEscolhaFormaPagamento(whatsappCliente);
    }

    public MensagemWhatsappSaidaDTO tratarValorTrocoInformado(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        String textoCliente
    ) {

        String raw = msg.safe(textoCliente);

        BigDecimal valor = parseService.parseValorMonetario(raw);
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            sessaoService.marcarAguardandoTrocoValor(idSessao);
            return msg.texto(
                whatsappCliente,
                "Não consegui entender o valor do troco 😕\n\n" +
                    "Me informe um valor válido.\n\n" +
                    "Exemplos: 50 | 100,00 | R$ 20"
            );
        }

        sessaoService.salvarTrocoValor(idSessao, valor);

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);
        return menusClienteService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);
    }

    public RoteamentoResultado enviarPedidoDefinitivo(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        Map<Long, Integer> carrinho
    ) {

        if (carrinho == null || carrinho.isEmpty()) {
            return new RoteamentoResultado(
                "bloqueio_carrinho_vazio",
                msg.texto(whatsappCliente, "Seu carrinho está vazio 🛒\n\nInclua itens antes de enviar o pedido.")
            );
        }

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        if (!StringUtils.hasText(s.getEnderecoEntrega())) {
            sessaoService.marcarAguardandoEnderecoEntrega(idSessao);
            return new RoteamentoResultado(
                "solicitar_endereco_entrega",
                menusClienteService.montarSolicitacaoEnderecoEntrega(whatsappCliente)
            );
        }

        PedidoRequestDTO pedidoReq = new PedidoRequestDTO();
        pedidoReq.setIdEstabelecimento(estabelecimento.getId());

        ClienteRequestDTO cli = new ClienteRequestDTO();
        cli.setTelefone(whatsappCliente);
        cli.setNome(null);
        pedidoReq.setCliente(cli);

        pedidoReq.setTipoAtendimento(TipoAtendimento.ENTREGA);
        pedidoReq.setEnderecoEntrega(s.getEnderecoEntrega());
        pedidoReq.setObservacoes(s.getObservacoesEntrega());
        pedidoReq.setTaxaEntrega(BigDecimal.ZERO);
        pedidoReq.setTaxaServico(BigDecimal.ZERO);

        if (s.getFormaPagamento() != null) {
            pedidoReq.setFormaPagamento(s.getFormaPagamento());
            pedidoReq.setPrecisaTroco(s.getPrecisaTroco());
            pedidoReq.setTrocoPara(s.getTrocoPara());
        }

        List<ItemPedidoRequestDTO> itens = carrinho.entrySet().stream()
            .map(e -> {
                ItemPedidoRequestDTO it = new ItemPedidoRequestDTO();
                it.setIdProduto(e.getKey());
                it.setQuantidade(e.getValue());
                it.setObservacoes(null);
                it.setOpcionais(List.of());
                return it;
            })
            .collect(Collectors.toList());

        pedidoReq.setItens(itens);

        PedidoResponseDTO resp = pedidoService.criar(pedidoReq);

        String resumoItens = carrinhoService.montarResumoItensDoCarrinho(estabelecimento, carrinho);

        MensagemWhatsappSaidaDTO saidaCliente = menusClienteService.montarConfirmacaoPedidoEnviado(
            whatsappCliente,
            resp.getId(),
            s,
            resumoItens,
            resp.getTotal()
        );

        List<String> admins = administradorWhatsappService.listarWhatsappsAdministradoresAtivos(estabelecimento);

        if (admins.isEmpty()) {

            MensagemWhatsappSaidaDTO saidaSemAdmin = msg.texto(
                whatsappCliente,
                msg.trunc(
                    "✅ Pedido enviado!\n\n" +
                        "Número do pedido: *#" + resp.getId() + "*\n" +
                        "No momento não encontrei atendentes ativos para confirmar.\n" +
                        "Assim que possível retornaremos. 🙂",
                    4096
                )
            );

            limparSessaoAposEnviar(idSessao);
            return new RoteamentoResultado("pedido_pendente_sem_admin", saidaSemAdmin);
        }

        List<MensagemWhatsappSaidaDTO> extras = admins.stream()
            .map(admin -> administradorWhatsappService.montarNotificacaoPedidoParaAdmin(
                admin,
                resp.getId(),
                whatsappCliente,
                s.getEnderecoEntrega(),
                s.getObservacoesEntrega(),
                resumoItens,
                resp.getTotal()
            ))
            .collect(Collectors.toList());

        limparSessaoAposEnviar(idSessao);

        return new RoteamentoResultado("pedido_pendente", saidaCliente, extras);
    }

    public void limparSessaoAposEnviar(Long idSessao) {
        sessaoService.limparPedidoEmAndamento(idSessao);
    }

    public Estabelecimento recarregarEstabelecimentoPorWhatsapp(String whatsappReceptor) {
        if (!StringUtils.hasText(whatsappReceptor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappReceptor é obrigatório");
        }
        return estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);
    }

    // ======================================================================
    // Endereço: parse + persist (manteve a mesma regra que você tinha)
    // ======================================================================

    private static class ParsedEndereco {
        final String endereco;
        final String observacoes;

        ParsedEndereco(String endereco, String observacoes) {
            this.endereco = endereco;
            this.observacoes = observacoes;
        }
    }

    private ParsedEndereco parseEnderecoEObservacoes(String raw) {

        String txt = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(txt)) return new ParsedEndereco("", null);

        String lower = txt.toLowerCase(Locale.ROOT);

        int idx = indexOfAny(lower,
            "obs:", "obs.:", "observacao:", "observação:", "observacoes:", "observações:"
        );

        if (idx < 0) {
            return new ParsedEndereco(txt, null);
        }

        String endereco = txt.substring(0, idx).trim();

        String obs = txt.substring(idx).trim();
        obs = obs.replaceFirst("(?i)^(obs\\s*:?\\s*\\.?|observa(c|ç)\\w*\\s*:?\\s*)", "").trim();

        return new ParsedEndereco(endereco, StringUtils.hasText(obs) ? obs : null);
    }

    private int indexOfAny(String haystackLower, String... needlesLower) {
        int best = -1;
        for (String n : needlesLower) {
            int i = haystackLower.indexOf(n);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }
}