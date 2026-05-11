package br.com.oraped.service.whatsapp.cliente;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.carrinho.Carrinho;
import br.com.oraped.domain.carrinho.ComplementoItemCarrinhoEmMontagem;
import br.com.oraped.domain.carrinho.ItemCarrinho;
import br.com.oraped.domain.enums.AbrangenciaEntrega;
import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.enums.TipoAtendimento;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.domain.produto.complemento.Complemento;
import br.com.oraped.domain.produto.complemento.GrupoComplemento;
import br.com.oraped.domain.produto.tamanho.OpcaoTamanhoProduto;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.ClienteRequestDTO;
import br.com.oraped.dto.ItemPedidoOpcionalRequestDTO;
import br.com.oraped.dto.ItemPedidoRequestDTO;
import br.com.oraped.dto.PedidoRequestDTO;
import br.com.oraped.dto.PedidoResponseDTO;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.repository.produto.complemento.ComplementoRepository;
import br.com.oraped.repository.produto.complemento.GrupoComplementoRepository;
import br.com.oraped.repository.produto.tamanho.OpcaoTamanhoProdutoRepository;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.carrinho.CarrinhoService;
import br.com.oraped.service.geolocalizacao.EstabelecimentoBairroAtendidoService;
import br.com.oraped.service.geolocalizacao.GeolocalizacaoProvider;
import br.com.oraped.service.geolocalizacao.TaxaEntregaClienteService;
import br.com.oraped.service.pedido.PedidoService;
import br.com.oraped.service.produto.complemento.GrupoComplementoService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdminPedidoService;
import br.com.oraped.service.whatsapp.administrador.ValidadorAdminService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorExtracaoEstabelecimentoService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorMensagemHelperService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.sessao.SessaoItemCarrinhoEmMontagemService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappClienteService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappMarketplaceService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PedidoClienteService {

    private final AdminPedidoService adminPedidoService;
    private final ValidadorAdminService validadorAdminService;
    
    private final EstabelecimentoService estabelecimentoService;
    private final PedidoService pedidoService;

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final SessaoWhatsappClienteService sessaoClienteService;
    private final SessaoWhatsappMarketplaceService sessaoMarketplaceService;
    
    private final EnderecoClienteService enderecoClienteService;
    private final CarrinhoClienteService carrinhoService;
    private final MenuClienteService menusClienteService;
    private final OrquestradorExtracaoEstabelecimentoService extracaoService;
    private final OrquestradorParseService parseService;
    private final RevisaoPedidoClienteService revisaoPedidoService;
    
    private final GeolocalizacaoProvider geolocalizacaoProvider;
    private final TaxaEntregaClienteService taxaEntregaClienteService;
    private final EstabelecimentoBairroAtendidoService estabelecimentoBairroAtendidoService;
    
    private final SessaoItemCarrinhoEmMontagemService itemEmMontagemService;
    private final CarrinhoService carrinhoPersistenciaService;
    private final GrupoComplementoService grupoComplementoService;
    private final GrupoComplementoRepository grupoComplementoRepository;
    private final ComplementoRepository complementoRepository;
    private final OpcaoTamanhoProdutoRepository opcaoTamanhoProdutoRepository;
    
    private final WhatsappMensagemFactory msg;
    private final OrquestradorMensagemHelperService helper;

    
    public RoteamentoResultado tratarFluxoEndereco(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao
	) {

	    if (carrinhoService.isCarrinhoVazio(idSessao)) {

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
	            sessaoClienteService.marcarAguardandoFormaPagamento(idSessao);
	            return new RoteamentoResultado(
	                "forma_pagamento_menu",
	                menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao)
	            );
	        }

	        if (sessao.getFormaPagamento() == FormaPagamentoPedido.DINHEIRO
	            && Boolean.TRUE.equals(sessao.getPrecisaTroco())
	            && sessao.getTrocoPara() == null
	        ) {
	            sessaoClienteService.marcarAguardandoTrocoValor(idSessao);
	            return new RoteamentoResultado(
	                "troco_valor",
	                menusClienteService.montarSolicitacaoTrocoValor(whatsappCliente)
	            );
	        }

	        return new RoteamentoResultado(
	            "confirmacao_final",
	            revisaoPedidoService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, sessao)
	        );
	    }

	    PedidoResponseDTO ultimoPedido = pedidoService
	        .buscarUltimoPedidoDoCliente(estabelecimento.getId(), whatsappCliente);

	    if (ultimoPedido != null && StringUtils.hasText(ultimoPedido.getEnderecoEntrega())) {

	        EnderecoResolvidoDTO end = new EnderecoResolvidoDTO();
	        end.setCep(ultimoPedido.getCepEntrega());
	        end.setBairro(ultimoPedido.getBairroEntrega());
	        end.setCidade(ultimoPedido.getCidadeEntrega());
	        end.setUf(ultimoPedido.getUfEntrega());
	        end.setLatitude(ultimoPedido.getLatitudeEntrega());
	        end.setLongitude(ultimoPedido.getLongitudeEntrega());

	        // Para abrangência por bairro, só oferecemos reaproveitar endereço se ele ainda for atendido.
	        boolean enderecoAtendido = estabelecimentoBairroAtendidoService.isEnderecoAtendido(estabelecimento, end);

	        if (enderecoAtendido) {
	            BigDecimal taxaAtual = taxaEntregaClienteService.calcularTaxaEntrega(estabelecimento, end);

	            return new RoteamentoResultado(
	                "sugestao_endereco_anterior",
	                enderecoClienteService.montarSugestaoEnderecoAnterior(
	                    whatsappCliente,
	                    ultimoPedido.getEnderecoEntrega(),
	                    taxaAtual
	                )
	            );
	        }
	    }

	    sessaoClienteService.marcarAguardandoCepEntrega(idSessao);
	    return new RoteamentoResultado(
	        "solicitar_cep_entrega",
	        enderecoClienteService.montarSolicitacaoCepEntrega(whatsappCliente, false)
	    );
	}

    public RoteamentoResultado tratarConfirmacaoEnderecoAnterior(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    String whatsappReceptor,
	    String phoneNumberId,
	    Long idSessao
	) {

	    if (carrinhoService.isCarrinhoVazio(idSessao)) {
	        return new RoteamentoResultado(
	            "bloqueio_carrinho_vazio",
	            msg.texto(whatsappCliente, "Seu carrinho está vazio 🛒\n\nInclua itens antes de enviar o pedido.")
	        );
	    }

	    PedidoResponseDTO ultimoPedido = pedidoService
	        .buscarUltimoPedidoDoCliente(estabelecimento.getId(), whatsappCliente);

	    if (ultimoPedido == null || !StringUtils.hasText(ultimoPedido.getEnderecoEntrega())) {
	        sessaoClienteService.marcarAguardandoCepEntrega(idSessao);

	        MensagemWhatsappSaidaDTO m = enderecoClienteService.montarSolicitacaoCepEntrega(whatsappCliente, false);
	        return new RoteamentoResultado("solicitar_cep_entrega", m);
	    }

	    EnderecoResolvidoDTO end = new EnderecoResolvidoDTO();
	    end.setCep(ultimoPedido.getCepEntrega());
	    end.setBairro(ultimoPedido.getBairroEntrega());
	    end.setCidade(ultimoPedido.getCidadeEntrega());
	    end.setUf(ultimoPedido.getUfEntrega());
	    end.setLatitude(ultimoPedido.getLatitudeEntrega());
	    end.setLongitude(ultimoPedido.getLongitudeEntrega());

	    // Revalidamos cobertura antes de reaproveitar o endereço antigo.
	    if (!estabelecimentoBairroAtendidoService.isEnderecoAtendido(estabelecimento, end)) {
	        sessaoClienteService.marcarAguardandoCepEntrega(idSessao);

	        MensagemWhatsappSaidaDTO m = msg.texto(
	            whatsappCliente,
	            "⚠️ No momento esse endereço anterior não está dentro da área atendida por este estabelecimento.\n\n" +
	                "Por favor, envie o *CEP de entrega* para eu verificar um novo endereço."
	        );

	        return new RoteamentoResultado("endereco_anterior_fora_da_area", m);
	    }

	    BigDecimal taxaAtual = taxaEntregaClienteService.calcularTaxaEntrega(estabelecimento, end);

	    sessaoClienteService.salvarEnderecoEntregaEstruturado(
	        idSessao,
	        ultimoPedido.getEnderecoEntrega(),
	        ultimoPedido.getObservacoes(),
	        ultimoPedido.getCepEntrega(),
	        ultimoPedido.getBairroEntrega(),
	        ultimoPedido.getCidadeEntrega(),
	        ultimoPedido.getUfEntrega(),
	        ultimoPedido.getLatitudeEntrega(),
	        ultimoPedido.getLongitudeEntrega(),
	        taxaAtual
	    );

	    sessaoClienteService.marcarAguardandoFormaPagamento(idSessao);

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

        sessaoClienteService.salvarFormaPagamento(idSessao, fp);

        if (fp == FormaPagamentoPedido.DINHEIRO) {
        	sessaoClienteService.marcarAguardandoTrocoConfirmacao(idSessao);
            MensagemWhatsappSaidaDTO m = menusClienteService.montarPerguntaTrocoSimNao(whatsappCliente);
            return new RoteamentoResultado("troco_confirmacao", m);
        }

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        sessaoClienteService.marcarAguardandoConfirmacaoFinal(idSessao);
        MensagemWhatsappSaidaDTO m = revisaoPedidoService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);

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
        	sessaoClienteService.salvarTrocoNecessidade(idSessao, false);

            SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

            sessaoClienteService.marcarAguardandoConfirmacaoFinal(idSessao);
            MensagemWhatsappSaidaDTO m = revisaoPedidoService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);

            return new RoteamentoResultado("confirmacao_final", m);
        }

        if ("SIM".equalsIgnoreCase(escolha)) {
        	sessaoClienteService.salvarTrocoNecessidade(idSessao, true);
        	sessaoClienteService.marcarAguardandoTrocoValor(idSessao);

            MensagemWhatsappSaidaDTO m = menusClienteService.montarSolicitacaoTrocoValor(whatsappCliente);
            return new RoteamentoResultado("troco_valor", m);
        }

        sessaoClienteService.marcarAguardandoTrocoConfirmacao(idSessao);

        MensagemWhatsappSaidaDTO m = menusClienteService.montarPerguntaTrocoSimNao(whatsappCliente);
        return new RoteamentoResultado("troco_confirmacao", m);
    }

    public RoteamentoResultado tratarEnvioPedidoDefinitivo(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao
	) {

	    Carrinho carrinho = carrinhoService.buscarCarrinhoAtual(idSessao);

	    if (carrinho == null || carrinho.getItens() == null || carrinho.getItens().isEmpty()) {
	        return new RoteamentoResultado(
	            "bloqueio_carrinho_vazio",
	            msg.texto(whatsappCliente, "Seu carrinho está vazio 🛒\n\nInclua itens antes de enviar o pedido.")
	        );
	    }

	    SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

	    if (!StringUtils.hasText(s.getEnderecoEntrega())) {
	        sessaoClienteService.marcarAguardandoCepEntrega(idSessao);
	        return new RoteamentoResultado(
	            "solicitar_cep_entrega",
	            enderecoClienteService.montarSolicitacaoCepEntrega(whatsappCliente, false)
	        );
	    }

	    if (s.getFormaPagamento() == null) {
	        sessaoClienteService.marcarAguardandoFormaPagamento(idSessao);
	        return new RoteamentoResultado(
	            "forma_pagamento_menu",
	            menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao)
	        );
	    }

	    if (s.getFormaPagamento() == FormaPagamentoPedido.DINHEIRO
	        && Boolean.TRUE.equals(s.getPrecisaTroco())
	        && s.getTrocoPara() == null
	    ) {
	        sessaoClienteService.marcarAguardandoTrocoValor(idSessao);
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
	    Long idSessao,
	    Long idProduto,
	    Integer quantidade
	) {

	    if (idSessao == null) {
	        return new RoteamentoResultado(
	            "sessao_nao_encontrada",
	            msg.texto(whatsappCliente, "Não consegui identificar sua sessão. Por favor, tente iniciar novamente.")
	        );
	    }

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

	    // =========================================================
	    // CLIENTE — Complementos escolhidos antes da quantidade
	    // =========================================================

	    // A montagem guarda os complementos temporariamente até o cliente escolher a quantidade.
	    List<ComplementoItemCarrinhoEmMontagem> complementos =
	        itemEmMontagemService.listarComplementos(idSessao);

	    // =========================================================
	    // CLIENTE — Persistência do item no carrinho
	    // =========================================================

	    // A criação do item e dos complementos pertence ao serviço de persistência do carrinho.
	    ItemCarrinho itemAdicionado = carrinhoPersistenciaService.adicionarItem(
	        idSessao,
	        produto,
	        qtd,
	        complementos
	    );

	    // A montagem temporária não pode permanecer após virar item efetivo do carrinho.
	    itemEmMontagemService.limparMontagem(idSessao);

	    BigDecimal total = carrinhoService.calcularSubtotalItem(itemAdicionado);

	    StringBuilder sb = new StringBuilder();
	    
		// Feedback de ação (mantido)
		sb.append("Perfeito! ✅\n");
		sb.append("Adicionado ao carrinho:\n\n");
	
		// Produto
		sb.append("*").append(msg.safe(produto.getNome())).append("*\n");

		String descricaoProduto = msg.safe(produto.getDescricao());
		if (StringUtils.hasText(descricaoProduto)) {
		    sb.append(msg.trunc(descricaoProduto, 120)).append("\n");
		}

		BigDecimal precoUnitarioItem = itemAdicionado.getPrecoUnitario() == null
		    ? BigDecimal.ZERO
		    : itemAdicionado.getPrecoUnitario();

		sb.append("\nPreço: ").append(msg.formatarMoeda(precoUnitarioItem)).append("\n");

		if (StringUtils.hasText(itemAdicionado.getNomeTamanho())) {
		    sb.append("Tamanho: ")
		        .append(msg.safe(itemAdicionado.getNomeTamanho()))
		        .append("\n");
		}

		sb.append("Quantidade: ").append(qtd).append("\n");
		
		// Complementos
		if (complementos != null && !complementos.isEmpty()) {
			sb.append("\n*Complementos:*\n");
	
			for (ComplementoItemCarrinhoEmMontagem complemento : complementos) {
				if (complemento == null || complemento.getQuantidade() == null || complemento.getQuantidade() < 1) {
					continue;
				}
	
				BigDecimal precoUnitario = complemento.getPrecoUnitario() == null
					? BigDecimal.ZERO
					: complemento.getPrecoUnitario();
	
		        BigDecimal subtotalComplemento = precoUnitario.multiply(
		        	BigDecimal.valueOf(complemento.getQuantidade())
		        );
	
		        sb.append("• ")
		             .append(complemento.getQuantidade())
		             .append("x ")
		             .append(msg.safe(complemento.getNome()))
		             .append(": ")
		             .append(msg.formatarMoeda(subtotalComplemento))
		             .append("\n");
			}
		}
	
		 // Total
		 sb.append("\nValor total: ").append(msg.formatarMoeda(total)).append("\n\n");
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
        	sessaoClienteService.marcarAguardandoEnderecoEntrega(idSessao);
            return msg.texto(
                whatsappCliente,
                "Não consegui identificar o endereço 😕\n\n" +
                    "Por favor, me envie o endereço de entrega (com número) e observações pro entregador."
            );
        }

        ParsedEndereco parsed = parseEnderecoEObservacoes(raw);

        if (!StringUtils.hasText(parsed.endereco)) {
        	sessaoClienteService.marcarAguardandoEnderecoEntrega(idSessao);
            return msg.texto(
                whatsappCliente,
                "Não consegui identificar o endereço 😕\n\n" +
                    "Me envie o endereço (rua, número, bairro) e, se quiser, observações pro entregador."
            );
        }

        sessaoClienteService.salvarEnderecoEntrega(idSessao, parsed.endereco, parsed.observacoes);
        sessaoClienteService.desmarcarAguardandoEnderecoEntrega(idSessao);

        sessaoClienteService.marcarAguardandoFormaPagamento(idSessao);
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
        	sessaoClienteService.marcarAguardandoTrocoValor(idSessao);
            return msg.texto(
                whatsappCliente,
                "Não consegui entender o valor do troco 😕\n\n" +
                    "Me informe um valor válido.\n\n" +
                    "Exemplos: 50 | 100,00 | R$ 20"
            );
        }

        // Garante que o valor informado para troco seja compatível com o total atual.
        BigDecimal totalPedido = calcularTotalAtualDoPedido(estabelecimento, idSessao);

        if (totalPedido != null && totalPedido.compareTo(BigDecimal.ZERO) > 0 && valor.compareTo(totalPedido) < 0) {

        	sessaoClienteService.marcarAguardandoTrocoValor(idSessao);

            String corpo =
                "Esse valor não é suficiente para troco 😕\n\n" +
                    "*Total do pedido:* " + msg.formatarMoeda(totalPedido) + "\n" +
                    "*Troco para:* " + msg.formatarMoeda(valor) + "\n\n" +
                    "Por favor, informe um valor *igual ou maior* que o total.";

            return msg.texto(whatsappCliente, msg.trunc(corpo, 4096));
        }

        sessaoClienteService.salvarTrocoValor(idSessao, valor);

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);
        return revisaoPedidoService.montarConfirmacaoFinalAntesDeEnviar(estabelecimento, whatsappCliente, s);
    }

    private BigDecimal calcularTotalAtualDoPedido(Estabelecimento estabelecimento, Long idSessao) {

        if (estabelecimento == null || idSessao == null) {
            return BigDecimal.ZERO;
        }

        Carrinho carrinho = carrinhoService.buscarCarrinhoAtual(idSessao);
        BigDecimal subtotalItens = carrinhoService.calcularSubtotalCarrinho(carrinho);

        SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

        BigDecimal taxaEntrega = s.getTaxaEntregaCalculada();
        if (taxaEntrega == null) {
            taxaEntrega = estabelecimento.getTaxaEntregaPadrao();
        }
        if (taxaEntrega == null) {
            taxaEntrega = BigDecimal.ZERO;
        }

        BigDecimal taxaServico = BigDecimal.ZERO;

        return subtotalItens.add(taxaEntrega).add(taxaServico);
    }

    public RoteamentoResultado enviarPedidoDefinitivo(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    Carrinho carrinho
	) {

	    if (carrinho == null || carrinho.getItens() == null || carrinho.getItens().isEmpty()) {
	        return new RoteamentoResultado(
	            "bloqueio_carrinho_vazio",
	            msg.texto(whatsappCliente, "Seu carrinho está vazio 🛒\n\nInclua itens antes de enviar o pedido.")
	        );
	    }

	    SessaoAtendimentoWhatsapp s = sessaoService.buscarPorId(idSessao);

	    if (!StringUtils.hasText(s.getEnderecoEntrega())) {
	        sessaoClienteService.marcarAguardandoCepEntrega(idSessao);
	        return new RoteamentoResultado(
	            "solicitar_cep_entrega",
	            enderecoClienteService.montarSolicitacaoCepEntrega(whatsappCliente, false)
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

	    List<ItemPedidoRequestDTO> itens = carrinho.getItens().stream()
	        .map(itemCarrinho -> {
	            ItemPedidoRequestDTO itemPedido = new ItemPedidoRequestDTO();

	            itemPedido.setIdProduto(itemCarrinho.getProduto().getId());
	            itemPedido.setQuantidade(itemCarrinho.getQuantidade());
	            itemPedido.setIdOpcaoTamanhoProduto(itemCarrinho.getIdOpcaoTamanhoProduto());
	            itemPedido.setIdOpcaoTamanho(itemCarrinho.getIdOpcaoTamanho());
	            itemPedido.setNomeTamanho(itemCarrinho.getNomeTamanho());
	            itemPedido.setPrecoUnitario(itemCarrinho.getPrecoUnitario());
	            itemPedido.setObservacoes(itemCarrinho.getObservacoes());
	            itemPedido.setOpcionais(montarOpcionaisDoItemCarrinho(itemCarrinho));

	            return itemPedido;
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

	    List<String> admins = validadorAdminService.listarWhatsappsAdministradoresAtivos(estabelecimento);

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
	        .map(admin -> adminPedidoService.montarNotificacaoPedidoParaAdmin(
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

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        // =========================================================
        // CLIENTE — Limpeza do carrinho após envio do pedido
        // =========================================================

        // Depois que o pedido foi criado, o carrinho temporário não pode permanecer na sessão.
        carrinhoPersistenciaService.limparCarrinho(idSessao);

        if (sessao.getIdMarketplace() != null) {
            sessaoMarketplaceService.limparPedidoFinalizadoPreservandoMarketplace(idSessao);
            return;
        }

        sessaoService.encerrarSessao(idSessao);
    }

    public Estabelecimento recarregarEstabelecimentoPorWhatsapp(String whatsappReceptor) {
        if (!StringUtils.hasText(whatsappReceptor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappReceptor é obrigatório");
        }
        return estabelecimentoService.buscarPorWhatsapp(whatsappReceptor);
    }

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
        if (!StringUtils.hasText(txt)) {
            return new ParsedEndereco("", null);
        }

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
	        sessaoClienteService.marcarAguardandoCepEntrega(idSessao);
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

	        // Para cobertura por bairro, CEP é obrigatório para validar área atendida.
	        if (estabelecimento.getAbrangenciaEntrega() == AbrangenciaEntrega.BAIRRO) {
	            sessaoClienteService.marcarAguardandoCepEntrega(idSessao);
	            return msg.texto(
	                whatsappCliente,
	                "⚠️ Não consegui validar esse CEP agora.\n\n" +
	                    "Como este estabelecimento atende por *bairros específicos*, preciso que você envie um *CEP válido* para verificar a área de entrega."
	            );
	        }

	        sessaoClienteService.marcarAguardandoEnderecoCompletoFallback(idSessao);
	        return enderecoClienteService.montarSolicitacaoEnderecoCompletoFallback(whatsappCliente);
	    }

	    if (end == null
	        || !StringUtils.hasText(end.getBairro())
	        || !StringUtils.hasText(end.getCidade())
	        || !StringUtils.hasText(end.getUf())
	    ) {

	        if (estabelecimento.getAbrangenciaEntrega() == AbrangenciaEntrega.BAIRRO) {
	            sessaoClienteService.marcarAguardandoCepEntrega(idSessao);
	            return msg.texto(
	                whatsappCliente,
	                "⚠️ Não consegui identificar o bairro desse CEP.\n\n" +
	                    "Como este estabelecimento atende por *bairros específicos*, preciso de um *CEP válido* para verificar a cobertura."
	            );
	        }

	        sessaoClienteService.marcarAguardandoEnderecoCompletoFallback(idSessao);
	        return enderecoClienteService.montarSolicitacaoEnderecoCompletoFallback(whatsappCliente);
	    }

	    // ================================
	    // VALIDAÇÃO DE COBERTURA
	    // ================================
	    if (!estabelecimentoBairroAtendidoService.isEnderecoAtendido(estabelecimento, end)) {

	        sessaoClienteService.marcarAguardandoCepEntrega(idSessao);

	        String bairro = msg.safe(end.getBairro());
	        String cidade = msg.safe(end.getCidade());
	        String uf = msg.safe(end.getUf());

	        String bairrosAtendidosStr = "";

	        // Quando a abrangência é por bairro, mostramos TODOS os bairros atendidos.
	        // Não aplicamos limite aqui para dar total transparência ao cliente.
	        if (estabelecimento.getAbrangenciaEntrega() == AbrangenciaEntrega.BAIRRO) {

	            List<Bairro> bairrosAtendidos =
	                estabelecimentoBairroAtendidoService.listarBairrosAtendidosDaVizinhanca(estabelecimento);

	            if (bairrosAtendidos != null && !bairrosAtendidos.isEmpty()) {

	                bairrosAtendidosStr = bairrosAtendidos.stream()
	                    .map(b -> "• " + msg.trunc(msg.safe(b.getNome()), 40)) // protege apenas tamanho do nome
	                    .collect(Collectors.joining("\n"));
	            }
	        }

	        return msg.texto(
        	    whatsappCliente,
        	    msg.trunc(
        	        "Poxa 😕\n\n" +
        	            "Neste momento ainda não atendemos entregas para *" +
        	            bairro + "*" +
        	            (StringUtils.hasText(cidade) || StringUtils.hasText(uf)
        	                ? " (" + cidade + "/" + uf + ")"
        	                : "") +
        	            ".\n\n" +

        	            (StringUtils.hasText(bairrosAtendidosStr)
        	                ? "Mas atendemos normalmente nestes bairros:\n\n" +
        	                  bairrosAtendidosStr + "\n\n" +
        	                  "Se você preferir, posso seguir com a entrega em um desses locais 🙂\n\n"
        	                : ""
        	            ) +

        	            "Se quiser, me envie outro *CEP de entrega* que eu verifico para você na hora.",
        	        4000
        	    )
        	);
	    }

	    // ================================
	    // ENDEREÇO VÁLIDO E ATENDIDO
	    // ================================
	    String enderecoBase = montarEnderecoBase(end);

	    BigDecimal taxa = taxaEntregaClienteService.calcularTaxaEntrega(estabelecimento, end);

	    sessaoClienteService.salvarEnderecoResolvidoPorCep(
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

	    sessaoClienteService.marcarAguardandoComplementoEndereco(idSessao);

	    return enderecoClienteService.montarEnderecoEncontradoSolicitarComplemento(
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
	        sessaoClienteService.marcarAguardandoComplementoEndereco(idSessao);
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
	        sessaoClienteService.marcarAguardandoComplementoEndereco(idSessao);
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
	
	    sessaoClienteService.salvarComplementoFinalizarEndereco(idSessao, enderecoFinal, parsed.observacoes);
	
	    sessaoClienteService.marcarAguardandoFormaPagamento(idSessao);
	    return menusClienteService.montarEscolhaFormaPagamento(estabelecimento, whatsappCliente, idSessao);
	}
	
	public MensagemWhatsappSaidaDTO tratarEnderecoCompletoFallbackInformado(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    String textoCliente
	) {

	    // Para cobertura por bairro, não aceitamos fallback textual,
	    // porque o sistema precisa validar o bairro atendido antes de continuar.
	    if (estabelecimento.getAbrangenciaEntrega() == AbrangenciaEntrega.BAIRRO) {
	        sessaoClienteService.marcarAguardandoCepEntrega(idSessao);
	        return msg.texto(
	            whatsappCliente,
	            "⚠️ Este estabelecimento atende por *bairros específicos*.\n\n" +
	                "Para verificar a cobertura, preciso que você envie o *CEP de entrega*."
	        );
	    }

	    String raw = msg.safe(textoCliente);

	    if (!StringUtils.hasText(raw) || "(vazio)".equals(raw)) {
	        sessaoClienteService.marcarAguardandoEnderecoCompletoFallback(idSessao);
	        return enderecoClienteService.montarSolicitacaoEnderecoCompletoFallback(whatsappCliente);
	    }

	    ParsedEndereco parsed = parseEnderecoEObservacoes(raw);

	    if (!StringUtils.hasText(parsed.endereco)) {
	        sessaoClienteService.marcarAguardandoEnderecoCompletoFallback(idSessao);
	        return enderecoClienteService.montarSolicitacaoEnderecoCompletoFallback(whatsappCliente);
	    }

	    BigDecimal taxa = estabelecimento.getTaxaEntregaPadrao();
	    if (taxa == null) {
	        taxa = BigDecimal.ZERO;
	    }

	    sessaoClienteService.salvarEnderecoEntregaEstruturado(
	        idSessao,
	        parsed.endereco,
	        parsed.observacoes,
	        null,
	        null,
	        null,
	        null,
	        null,
	        null,
	        taxa
	    );

	    sessaoClienteService.marcarAguardandoFormaPagamento(idSessao);
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
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append(bairro);
        }

        if (StringUtils.hasText(cidade) || StringUtils.hasText(uf)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(StringUtils.hasText(cidade) ? cidade : "");
            if (StringUtils.hasText(uf)) {
                if (StringUtils.hasText(cidade)) {
                    sb.append("/");
                }
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
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }
    
    private List<ItemPedidoOpcionalRequestDTO> montarOpcionaisDoItemCarrinho(ItemCarrinho itemCarrinho) {

        if (itemCarrinho == null
            || itemCarrinho.getComplementos() == null
            || itemCarrinho.getComplementos().isEmpty()
        ) {
            return List.of();
        }

        return itemCarrinho.getComplementos().stream()
            .filter(complemento -> complemento != null)
            .filter(complemento -> complemento.getQuantidade() != null && complemento.getQuantidade() > 0)
            .map(complemento -> {
                ItemPedidoOpcionalRequestDTO opcional = new ItemPedidoOpcionalRequestDTO();

                opcional.setNome(complemento.getNome());
                opcional.setQuantidade(complemento.getQuantidade());
                opcional.setPreco(
                    complemento.getPrecoUnitario() == null
                        ? BigDecimal.ZERO
                        : complemento.getPrecoUnitario()
                );

                return opcional;
            })
            .collect(Collectors.toList());
    }
    
    
    @Transactional
    public RoteamentoResultado tratarSelecionarComplemento(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        Long idComplemento
    ) {

        if (idSessao == null) {
            return new RoteamentoResultado(
                "sessao_nao_encontrada",
                msg.texto(whatsappCliente, "Não consegui identificar sua sessão. Por favor, tente iniciar novamente.")
            );
        }

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        Produto produto = extracaoService.extrairProduto(
            estabelecimento,
            sessao.getIdProdutoItemEmMontagem()
        );

        if (produto == null || produto.getId() == null) {
            return new RoteamentoResultado(
                "item_montagem_invalido",
                msg.texto(whatsappCliente, "Não consegui identificar o produto em montagem. Por favor, escolha o produto novamente.")
            );
        }

        List<GrupoComplemento> grupos = buscarGruposComplementoAplicaveis(produto);

        if (grupos.isEmpty()) {
            return montarTelaQuantidadeAposComplementos(estabelecimento, whatsappCliente, idSessao, sessao);
        }

        int posicaoAtual = sessao.getOrdemGrupoComplementoItemEmMontagem() == null
            ? 1
            : sessao.getOrdemGrupoComplementoItemEmMontagem();

        if (posicaoAtual < 1) {
            posicaoAtual = 1;
        }

        if (posicaoAtual > grupos.size()) {
            return montarTelaQuantidadeAposComplementos(estabelecimento, whatsappCliente, idSessao, sessao);
        }

        GrupoComplemento grupoAtual = grupos.get(posicaoAtual - 1);

        Complemento complemento = grupoComplementoService.buscarComplementoObrigatorio(idComplemento);

        if (complemento == null || !complemento.isAtivo()) {
            return new RoteamentoResultado(
                "complemento_indisponivel",
                msg.texto(whatsappCliente, "Esse complemento não está disponível no momento.")
            );
        }

        Set<Long> idsComplementosDoGrupoAtual = complementoRepository
            .findByGrupoIdAndAtivoTrueOrderByNomeAsc(grupoAtual.getId())
            .stream()
            .map(Complemento::getId)
            .collect(Collectors.toSet());

        if (!idsComplementosDoGrupoAtual.contains(complemento.getId())) {
            return new RoteamentoResultado(
                "complemento_fora_do_grupo_atual",
                msg.texto(whatsappCliente, "Esse complemento não pertence à etapa atual do pedido.")
            );
        }

        int maximoSelecoes = grupoAtual.getMaximoSelecoes() == null
            ? 0
            : grupoAtual.getMaximoSelecoes();

        int quantidadeSelecionadaAntes = contarComplementosSelecionadosDoGrupoAtual(
            idSessao,
            idsComplementosDoGrupoAtual
        );

        if (maximoSelecoes > 0 && quantidadeSelecionadaAntes >= maximoSelecoes) {
            return montarTelaQuantidadeAposComplementos(estabelecimento, whatsappCliente, idSessao, sessao);
        }

        // A seleção é acumulativa até atingir o limite máximo configurado no grupo atual.
        itemEmMontagemService.adicionarComplemento(idSessao, complemento);

        int quantidadeSelecionadaDepois = contarComplementosSelecionadosDoGrupoAtual(
            idSessao,
            idsComplementosDoGrupoAtual
        );

        if (maximoSelecoes > 0 && quantidadeSelecionadaDepois >= maximoSelecoes) {
            return montarTelaQuantidadeAposComplementos(estabelecimento, whatsappCliente, idSessao, sessao);
        }

        return new RoteamentoResultado(
            "complemento_adicionado",
            menusClienteService.montarListaComplementosEmMontagem(
                estabelecimento,
                whatsappCliente,
                idSessao
            )
        );
    }

    private int contarComplementosSelecionadosDoGrupoAtual(
	    Long idSessao,
	    Set<Long> idsComplementosDoGrupoAtual
	) {

	    if (idSessao == null || idsComplementosDoGrupoAtual == null || idsComplementosDoGrupoAtual.isEmpty()) {
	        return 0;
	    }

	    List<ComplementoItemCarrinhoEmMontagem> complementosSelecionados =
	        itemEmMontagemService.listarComplementos(idSessao);

	    if (complementosSelecionados == null || complementosSelecionados.isEmpty()) {
	        return 0;
	    }

	    return complementosSelecionados.stream()
	        .filter(item -> item != null)
	        .filter(item -> item.getComplemento() != null)
	        .filter(item -> idsComplementosDoGrupoAtual.contains(item.getComplemento().getId()))
	        .map(ComplementoItemCarrinhoEmMontagem::getQuantidade)
	        .filter(quantidade -> quantidade != null && quantidade > 0)
	        .mapToInt(Integer::intValue)
	        .sum();
	}

	private RoteamentoResultado montarTelaQuantidadeAposComplementos(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao,
	    SessaoAtendimentoWhatsapp sessao
	) {

	    Long idProduto = sessao.getIdProdutoItemEmMontagem();

	    if (idProduto == null) {
	        return new RoteamentoResultado(
	            "item_montagem_invalido",
	            msg.texto(whatsappCliente, "Não consegui identificar o produto em montagem. Por favor, escolha o produto novamente.")
	        );
	    }

	    return new RoteamentoResultado(
	        "listar_quantidades_apos_limite_complementos",
	        menusClienteService.montarListaQuantidades(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            sessao.getIdCategoriaItemEmMontagem(),
	            sessao.getQuantidadeMultiplaItemEmMontagem(),
	            idProduto
	        )
	    );
	}
	
    public RoteamentoResultado tratarNaoQueroComplemento(
	    Estabelecimento estabelecimento,
	    String whatsappCliente,
	    Long idSessao
	) {

	    SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

	    Long idProduto = sessao.getIdProdutoItemEmMontagem();

	    if (idProduto == null) {
	        return new RoteamentoResultado(
	            "item_montagem_invalido",
	            msg.texto(whatsappCliente, "Não consegui identificar o produto em montagem. Por favor, escolha o produto novamente.")
	        );
	    }

	    // A opção encerra a etapa de complementos e leva o cliente para a escolha de quantidade.
	    return new RoteamentoResultado(
	        "listar_quantidades_apos_complementos",
	        menusClienteService.montarListaQuantidades(
	            estabelecimento,
	            whatsappCliente,
	            idSessao,
	            sessao.getIdCategoriaItemEmMontagem(),
	            sessao.getQuantidadeMultiplaItemEmMontagem(),
	            idProduto
	        )
	    );
	}
    
    
    @Transactional
    public RoteamentoResultado tratarSelecionarTamanho(
        Estabelecimento estabelecimento,
        String whatsappCliente,
        Long idSessao,
        Long idProduto,
        Long idOpcaoTamanhoProduto
    ) {

        if (idSessao == null) {
            return new RoteamentoResultado(
                "sessao_nao_encontrada",
                msg.texto(whatsappCliente, "Não consegui identificar sua sessão. Por favor, tente iniciar novamente.")
            );
        }

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        Produto produto = extracaoService.extrairProduto(
            estabelecimento,
            idProduto
        );

        if (produto == null || produto.getId() == null) {
            return new RoteamentoResultado(
                "produto_nao_encontrado",
                msg.texto(whatsappCliente, "Produto não encontrado.")
            );
        }

        if (sessao.getIdProdutoItemEmMontagem() == null || !produto.getId().equals(sessao.getIdProdutoItemEmMontagem())) {
            return new RoteamentoResultado(
                "item_montagem_invalido",
                msg.texto(whatsappCliente, "Não consegui identificar o produto em montagem. Por favor, escolha o produto novamente.")
            );
        }

        OpcaoTamanhoProduto opcaoTamanhoProduto = opcaoTamanhoProdutoRepository
            .findById(idOpcaoTamanhoProduto)
            .orElse(null);

        if (opcaoTamanhoProduto == null
            || opcaoTamanhoProduto.getProduto() == null
            || !produto.getId().equals(opcaoTamanhoProduto.getProduto().getId())
            || !opcaoTamanhoProduto.isAtivo()
            || opcaoTamanhoProduto.getOpcaoTamanho() == null
            || !opcaoTamanhoProduto.getOpcaoTamanho().isAtivo()
            || opcaoTamanhoProduto.getPreco() == null
        ) {
            return new RoteamentoResultado(
                "tamanho_indisponivel",
                msg.texto(whatsappCliente, "Esse tamanho não está disponível para este produto.")
            );
        }

        // O tamanho define o preço final do produto na montagem atual.
        itemEmMontagemService.salvarTamanhoSelecionado(
            idSessao,
            opcaoTamanhoProduto
        );

        if (menusClienteService.produtoPossuiComplementos(produto)) {
            return new RoteamentoResultado(
                "listar_complementos_apos_tamanho",
                menusClienteService.montarListaComplementosEmMontagem(
                    estabelecimento,
                    whatsappCliente,
                    idSessao
                )
            );
        }

        return new RoteamentoResultado(
            "listar_quantidades_apos_tamanho",
            menusClienteService.montarListaQuantidades(
                estabelecimento,
                whatsappCliente,
                idSessao,
                sessao.getIdCategoriaItemEmMontagem(),
                sessao.getQuantidadeMultiplaItemEmMontagem(),
                produto.getId()
            )
        );
    }
    
    
    private List<GrupoComplemento> buscarGruposComplementoAplicaveis(Produto produto) {

        if (produto == null || produto.getId() == null) {
            return List.of();
        }

        List<GrupoComplemento> gruposProduto = grupoComplementoRepository
            .findByProdutoIdAndAtivoTrueAndExcluidoFalseOrderByOrdemAscNomeAsc(produto.getId());

        // Complementos próprios do produto têm prioridade sobre os herdados da categoria.
        if (!gruposProduto.isEmpty()) {
            return gruposProduto;
        }

        if (produto.getCategoria() == null || produto.getCategoria().getId() == null) {
            return List.of();
        }

        return grupoComplementoRepository
            .findByCategoriaIdAndAtivoTrueAndExcluidoFalseOrderByOrdemAscNomeAsc(produto.getCategoria().getId());
    }
}