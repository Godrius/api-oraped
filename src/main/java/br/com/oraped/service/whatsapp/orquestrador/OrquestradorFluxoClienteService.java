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
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.ClienteService;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.PedidoService;
import br.com.oraped.service.geolocalizacao.GeolocalizacaoProvider;
import br.com.oraped.service.geolocalizacao.TaxaEntregaClienteService;
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
    private final GeolocalizacaoProvider geolocalizacaoProvider;
    private final TaxaEntregaClienteService taxaEntregaClienteService;
    
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
                    helper.btn("COMANDO|FAZER_PEDIDO", "🛎️ Fazer um pedido")
                )
            );

            return new RoteamentoResultado("bloqueio_carrinho_vazio", saida);
        }

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        if (StringUtils.hasText(sessao.getEnderecoEntrega())) {

            if (sessao.getFormaPagamento() == null) {
                sessaoService.marcarAguardandoFormaPagamento(idSessao);
                return new RoteamentoResultado(
                    "forma_pagamento_menu",
                    menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao)
                );
            }

            if (sessao.getFormaPagamento() == FormaPagamentoPedido.DINHEIRO
                && Boolean.TRUE.equals(sessao.getPrecisaTroco())
                && sessao.getTrocoPara() == null
            ) {
                sessaoService.marcarAguardandoTrocoValor(idSessao);
                return new RoteamentoResultado(
                    "troco_valor",
                    menusClienteService.montarSolicitacaoTrocoValor(whatsappCliente)
                );
            }

            return new RoteamentoResultado(
                "confirmacao_final",
                menusClienteService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, sessao)
            );
        }

        String enderecoAnterior = clienteService
            .buscarUltimoEnderecoEntrega(estabelecimento, whatsappCliente)
            .orElse(null);

        if (StringUtils.hasText(enderecoAnterior)) {
        	
        	BigDecimal taxa = sessao.getTaxaEntregaCalculada();
        	if (taxa == null) {
        	    taxa = estabelecimento.getTaxaEntregaPadrao();
        	}

        	return new RoteamentoResultado(
        	    "sugestao_endereco_anterior",
        	    menusClienteService.montarSugestaoEnderecoAnterior(whatsappCliente, enderecoAnterior, taxa)
        	);
        }

        sessaoService.marcarAguardandoCepEntrega(idSessao);
        return new RoteamentoResultado(
            "solicitar_cep_entrega",
            menusClienteService.montarSolicitacaoCepEntrega(whatsappCliente)
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

        MensagemWhatsappSaidaDTO m = menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao);
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
                menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao)
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
        return menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao);
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

	    // =========================================================
	    // NOVO: valida coerência do troco
	    // - trocoPara deve ser >= total do pedido (itens + taxa entrega)
	    // - ex.: total 100 e trocoPara 50 => inválido
	    // =========================================================
	    BigDecimal totalPedido = calcularTotalAtualDoPedido(estabelecimento, idSessao);

	    // totalPedido pode ser zero se carrinho vazio (mas carrinho vazio já é bloqueado antes no fluxo)
	    if (totalPedido != null && totalPedido.compareTo(BigDecimal.ZERO) > 0 && valor.compareTo(totalPedido) < 0) {

	        sessaoService.marcarAguardandoTrocoValor(idSessao);

	        String corpo =
	            "Esse valor não é suficiente para troco 😕\n\n" +
	                "*Total do pedido:* " + msg.formatarMoeda(totalPedido) + "\n" +
	                "*Troco para:* " + msg.formatarMoeda(valor) + "\n\n" +
	                "Por favor, informe um valor *igual ou maior* que o total.";

	        return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
	    }

	    sessaoService.salvarTrocoValor(idSessao, valor);

	    SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);
	    return menusClienteService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);
	}

	private BigDecimal calcularTotalAtualDoPedido(Estabelecimento estabelecimento, Long idSessao) {

	    if (estabelecimento == null || idSessao == null) {
	        return BigDecimal.ZERO;
	    }

	    Map<Long, Integer> carrinho = carrinhoService.montarCarrinhoAtual(idSessao);

	    BigDecimal subtotalItens = BigDecimal.ZERO;

	    if (carrinho != null && !carrinho.isEmpty()) {

	        for (var e : carrinho.entrySet()) {

	            Long idProduto = e.getKey();
	            int qtd = e.getValue() == null ? 0 : e.getValue();

	            if (qtd <= 0) {
	                continue;
	            }

	            Produto p = extracaoService.extrairProduto(estabelecimento, idProduto);

	            // Usa o método existente no seu projeto (OrquestradorMenusClienteService)
	            BigDecimal subtotalItem = (p == null)
	                ? BigDecimal.ZERO
	                : menusClienteService.calcularPrecoPorQuantidade(p, qtd);

	            subtotalItens = subtotalItens.add(subtotalItem);
	        }
	    }

	    // Taxa de entrega: prioriza a calculada na sessão; fallback na taxa padrão da loja; fallback 0
	    SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

	    BigDecimal taxaEntrega = s.getTaxaEntregaCalculada();
	    if (taxaEntrega == null) {
	        taxaEntrega = estabelecimento.getTaxaEntregaPadrao();
	    }
	    if (taxaEntrega == null) {
	        taxaEntrega = BigDecimal.ZERO;
	    }

	    // No WhatsApp você está setando taxaServico = 0 no envio do pedido.
	    // Se no futuro existir taxa de serviço, some aqui também.
	    BigDecimal taxaServico = BigDecimal.ZERO;

	    return subtotalItens.add(taxaEntrega).add(taxaServico);
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

        BigDecimal taxaEntrega = s.getTaxaEntregaCalculada();
        if (taxaEntrega == null) {
            taxaEntrega = estabelecimento.getTaxaEntregaPadrao();
        }
        if (taxaEntrega == null) {
            taxaEntrega = BigDecimal.ZERO;
        }

        pedidoReq.setTaxaEntrega(taxaEntrega);
        pedidoReq.setCepEntrega(s.getCepEntrega());
        pedidoReq.setBairroEntrega(s.getBairroEntrega());
        pedidoReq.setCidadeEntrega(s.getCidadeEntrega());
        pedidoReq.setUfEntrega(s.getUfEntrega());
        pedidoReq.setLatitudeEntrega(s.getLatitudeEntrega());
        pedidoReq.setLongitudeEntrega(s.getLongitudeEntrega());
        
        
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
        sessaoService.encerrarSessao(idSessao);
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

    
    //===================================
    //ENDEREÇO DE ENTREGA DO CLIENTE
    //===================================
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

    
    public MensagemWhatsappSaidaDTO tratarCepEntregaInformado(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    String textoCliente
	) {

	    String cep = msg.safe(textoCliente);
	    String cepLimpo = cep == null ? null : cep.replaceAll("\\D", "");

	    if (!StringUtils.hasText(cepLimpo) || cepLimpo.length() != 8) {
	        sessaoService.marcarAguardandoCepEntrega(idSessao);
	        return msg.texto(
	            whatsappCliente,
	            "CEP inválido 😕\n\n" +
	                "Me envie o CEP com 8 dígitos (apenas números).\n" +
	                "Exemplo: 24350000"
	        );
	    }

	    EnderecoResolvidoDTO end;

	    try {
	        end = geolocalizacaoProvider.resolverCep(cepLimpo);
	    } catch (Exception ex) {
	        sessaoService.marcarAguardandoEnderecoCompletoFallback(idSessao);
	        return menusClienteService.montarSolicitacaoEnderecoCompletoFallback(whatsappCliente);
	    }

	    if (end == null
	        || !StringUtils.hasText(end.getBairro())
	        || !StringUtils.hasText(end.getCidade())
	        || !StringUtils.hasText(end.getUf())
	    ) {
	        sessaoService.marcarAguardandoEnderecoCompletoFallback(idSessao);
	        return menusClienteService.montarSolicitacaoEnderecoCompletoFallback(whatsappCliente);
	    }

	    String enderecoBase = montarEnderecoBase(end);

	    BigDecimal taxa = taxaEntregaClienteService.calcularTaxaEntrega(estabelecimento, end);

	    sessaoService.salvarEnderecoResolvidoPorCep(
	        idSessao,
	        cepLimpo,
	        enderecoBase,
	        end.getBairro(),
	        end.getCidade(),
	        end.getUf(),
	        end.getLatitude(),
	        end.getLongitude(),
	        taxa
	    );

	    sessaoService.marcarAguardandoComplementoEndereco(idSessao);

	    return menusClienteService.montarEnderecoEncontradoSolicitarComplemento(
	        whatsappCliente,
	        enderecoBase
	    );
	}

	public MensagemWhatsappSaidaDTO tratarComplementoEnderecoInformado(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    String textoCliente
	) {

	    String raw = msg.safe(textoCliente);

	    if (!StringUtils.hasText(raw) || "(vazio)".equals(raw)) {
	        sessaoService.marcarAguardandoComplementoEndereco(idSessao);
	        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

	        String base = msg.safe(s.getEnderecoBaseResolvido());
	        if (!StringUtils.hasText(base)) {
	            base = "(endereço)";
	        }

	        return msg.texto(
	            whatsappCliente,
	            "Não consegui identificar o complemento 😕\n\n" +
	                "Envie o complemento (número, apto/bloco, referência).\n\n" +
	                "Endereço base:\n" +
	                "*" + msg.trunc(base, 500) + "*"
	        );
	    }

	    ParsedEndereco parsed = parseComplementoEObservacoes(raw);

	    if (!StringUtils.hasText(parsed.endereco)) {
	        sessaoService.marcarAguardandoComplementoEndereco(idSessao);
	        return msg.texto(
	            whatsappCliente,
	            "Não consegui identificar o complemento 😕\n\n" +
	                "Me envie o complemento (número, apto/bloco, referência)."
	        );
	    }

	    SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

	    String base = msg.safe(s.getEnderecoBaseResolvido());
	    if (!StringUtils.hasText(base)) {
	        base = "";
	    }

	    String enderecoFinal = base;
	    if (StringUtils.hasText(enderecoFinal)) {
	        enderecoFinal = enderecoFinal + ", " + parsed.endereco.trim();
	    } else {
	        enderecoFinal = parsed.endereco.trim();
	    }

	    sessaoService.salvarComplementoFinalizarEndereco(idSessao, enderecoFinal, parsed.observacoes);

	    sessaoService.marcarAguardandoFormaPagamento(idSessao);
	    return menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao);
	}

	public MensagemWhatsappSaidaDTO tratarEnderecoCompletoFallbackInformado(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    String textoCliente
	) {

	    String raw = msg.safe(textoCliente);

	    if (!StringUtils.hasText(raw) || "(vazio)".equals(raw)) {
	        sessaoService.marcarAguardandoEnderecoCompletoFallback(idSessao);
	        return menusClienteService.montarSolicitacaoEnderecoCompletoFallback(whatsappCliente);
	    }

	    ParsedEndereco parsed = parseEnderecoEObservacoes(raw);

	    if (!StringUtils.hasText(parsed.endereco)) {
	        sessaoService.marcarAguardandoEnderecoCompletoFallback(idSessao);
	        return menusClienteService.montarSolicitacaoEnderecoCompletoFallback(whatsappCliente);
	    }

	    // taxa: se não conseguimos resolver bairro -> taxa padrão
	    BigDecimal taxa = estabelecimento.getTaxaEntregaPadrao();
	    if (taxa == null) {
	        taxa = BigDecimal.ZERO;
	    }

	    // salva endereço final (texto) + taxa calculada (padrão) e limpa estruturado
	    SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);
	    s.setCepEntrega(null);
	    s.setBairroEntrega(null);
	    s.setCidadeEntrega(null);
	    s.setUfEntrega(null);
	    s.setLatitudeEntrega(null);
	    s.setLongitudeEntrega(null);
	    s.setEnderecoBaseResolvido(null);
	    s.setTaxaEntregaCalculada(taxa);

	    sessaoService.salvarEnderecoEntrega(idSessao, parsed.endereco, parsed.observacoes);

	    sessaoService.marcarAguardandoFormaPagamento(idSessao);
	    return menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao);
	}

	private String montarEnderecoBase(EnderecoResolvidoDTO end) {

	    String logradouro = msg.safe(end.getLogradouro());
	    String bairro = msg.safe(end.getBairro());
	    String cidade = msg.safe(end.getCidade());
	    String uf = msg.safe(end.getUf());

	    StringBuilder sb = new StringBuilder();

	    if (StringUtils.hasText(logradouro)) {
	        sb.append(logradouro);
	    }

	    if (StringUtils.hasText(bairro)) {
	        if (sb.length() > 0) sb.append(" - ");
	        sb.append(bairro);
	    }

	    if (StringUtils.hasText(cidade) || StringUtils.hasText(uf)) {
	        if (sb.length() > 0) sb.append(", ");
	        sb.append(StringUtils.hasText(cidade) ? cidade : "");
	        if (StringUtils.hasText(uf)) {
	            if (StringUtils.hasText(cidade)) sb.append("/");
	            sb.append(uf);
	        }
	    }

	    return sb.toString().trim();
	}

	
	private ParsedEndereco parseComplementoEObservacoes(String raw) {
	    return parseEnderecoEObservacoes(raw);
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