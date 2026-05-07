package br.com.oraped.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.NotificacaoAberturaEstabelecimento;
import br.com.oraped.domain.carrinho.Carrinho;
import br.com.oraped.domain.enums.StatusNotificacaoAberturaEstabelecimento;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.marketplace.CategoriaMarketplace;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.estabelecimento.EstabelecimentoCreateRequestDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.dto.whatsapp.saida.RespostaWhatsappDTO;
import br.com.oraped.integration.OrazzaWhatsappCallbackClient;
import br.com.oraped.repository.EstabelecimentoRepository;
import br.com.oraped.repository.NotificacaoAberturaEstabelecimentoRepository;
import br.com.oraped.repository.marketplace.CategoriaMarketplaceRepository;
import br.com.oraped.repository.marketplace.MarketplaceRepository;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.cliente.CarrinhoClienteService;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstabelecimentoService {

    private final EstabelecimentoRepository estabelecimentoRepository;

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final CarrinhoClienteService carrinhoService;

    private final NotificacaoAberturaEstabelecimentoRepository notificacaoAberturaRepository;
    private final OrazzaWhatsappCallbackClient orazzaWhatsappCallbackClient;
    private final WhatsappMensagemFactory msg;

    private final CategoriaMarketplaceRepository categoriaMarketplaceRepository;
    private final MarketplaceRepository marketplaceRepository;

    @Transactional(readOnly = true)
    public Estabelecimento buscar(Long idEstabelecimento) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        Estabelecimento e = estabelecimentoRepository.findById(idEstabelecimento)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estabelecimento não encontrado"));

        return e;
    }

    @Transactional(readOnly = true)
    public void validarExiste(Long idEstabelecimento) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (!estabelecimentoRepository.existsById(idEstabelecimento)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estabelecimento não encontrado");
        }
    }

    @Transactional(readOnly = true)
    public Estabelecimento buscarPorWhatsapp(String whatsapp) {

        if (!StringUtils.hasText(whatsapp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsapp é obrigatório");
        }

        String w = normalizarWhatsapp(whatsapp);

        Estabelecimento e = estabelecimentoRepository.findByWhatsappAndAtivoTrue(w)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estabelecimento não encontrado"));
        
        return e;
    }

    @Transactional
    public Estabelecimento cadastrar(EstabelecimentoCreateRequestDTO req) {

        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload é obrigatório");
        }

        String nome = safeTrim(req.getNome());
        String whatsapp = safeTrim(req.getWhatsapp());

        if (nome.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nome é obrigatório");
        }

        if (whatsapp.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsapp é obrigatório");
        }

        if (req.getIdCategoriaMarketplace() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoriaMarketplace é obrigatório");
        }

        String whatsappNormalizado = normalizarWhatsapp(whatsapp);

        // Um número tem papel único no sistema: ou estabelecimento, ou marketplace.
        if (estabelecimentoRepository.existsByWhatsapp(whatsappNormalizado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe estabelecimento com este whatsapp");
        }

        if (marketplaceRepository.existsByWhatsapp(whatsappNormalizado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe marketplace com este whatsapp");
        }

        CategoriaMarketplace categoriaMarketplace = categoriaMarketplaceRepository.findById(req.getIdCategoriaMarketplace())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria do marketplace não encontrada"));

        Estabelecimento e = new Estabelecimento();
        e.setNome(nome);
        e.setWhatsapp(whatsappNormalizado);
        e.setTimezone(safeTrimOrNull(req.getTimezone()));
        e.setEndereco(safeTrimOrNull(req.getEndereco()));
        e.setConfiguracoesJson(req.getConfiguracoesJson());
        e.setCategoriaMarketplace(categoriaMarketplace);

        if (req.getAtivo() != null) {
            e.setAtivo(req.getAtivo());
        }

        if (req.getAberto() != null) {
            e.setAberto(req.getAberto());
        }

        return estabelecimentoRepository.save(e);
    }

    @Transactional
    public Estabelecimento atualizarCepEBairroBase(Long idEstabelecimento, String cep8, Bairro bairroBase) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        String cepLimpo = (cep8 == null) ? "" : cep8.replaceAll("\\D", "").trim();
        if (!StringUtils.hasText(cepLimpo) || cepLimpo.length() != 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CEP inválido");
        }

        Estabelecimento e = buscar(idEstabelecimento);

        e.setCep(cepLimpo);
        e.setBairro(bairroBase);

        return estabelecimentoRepository.save(e);
    }

    // ====================================================
    // Abrir, fechar e notificar aos clientes quando abrir
    // ====================================================
    @Transactional
    public void abrir(Long idEstabelecimento) {

        Estabelecimento estabelecimento = buscar(idEstabelecimento);

        if (!estabelecimento.isAberto()) {
            estabelecimento.setAberto(true);
            estabelecimentoRepository.save(estabelecimento);

            consumirFilaNotificacoesAbertura(estabelecimento);
        }
    }

    @Transactional
    public boolean solicitarNotificacaoQuandoAbrir(
        Long idEstabelecimento,
        String whatsappCliente,
        String phoneNumberId,
        String wamidEntrada,
        String idCorrelacao
    ) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        String w = normalizarWhatsapp(whatsappCliente);
        if (!StringUtils.hasText(w)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappCliente é obrigatório");
        }

        boolean jaExiste = notificacaoAberturaRepository
            .findByIdEstabelecimentoAndWhatsappClienteAndStatus(
                idEstabelecimento,
                w,
                StatusNotificacaoAberturaEstabelecimento.PENDENTE
            )
            .isPresent();

        if (jaExiste) {
            return false;
        }

        NotificacaoAberturaEstabelecimento n = new NotificacaoAberturaEstabelecimento();
        n.setIdEstabelecimento(idEstabelecimento);
        n.setWhatsappCliente(w);
        n.setPhoneNumberId(phoneNumberId);
        n.setWamidEntrada(wamidEntrada);
        n.setIdCorrelacao(idCorrelacao);
        n.setStatus(StatusNotificacaoAberturaEstabelecimento.PENDENTE);
        n.setCriadoEm(OffsetDateTime.now());
        n.setPendenteKey(1);

        notificacaoAberturaRepository.save(n);
        return true;
    }

    @Transactional
    public int consumirFilaNotificacoesAbertura(Estabelecimento estabelecimento) {

        if (estabelecimento == null || estabelecimento.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }

        List<NotificacaoAberturaEstabelecimento> pendentes =
            notificacaoAberturaRepository.findByIdEstabelecimentoAndStatus(
                estabelecimento.getId(),
                StatusNotificacaoAberturaEstabelecimento.PENDENTE
            );

        if (pendentes == null || pendentes.isEmpty()) {
            return 0;
        }

        int enviados = 0;

        for (NotificacaoAberturaEstabelecimento n : pendentes) {

            String waCliente = n.getWhatsappCliente();

            // Reaproveita a sessão do cliente no contexto exclusivo do estabelecimento.
            SessaoAtendimentoWhatsapp sessao = sessaoService.obterOuCriar(
                waCliente,
                msg.normalizarSomenteDigitos(estabelecimento.getWhatsapp()),
                estabelecimento.getId(),
                null
            );

            Carrinho carrinho = carrinhoService.buscarCarrinhoAtual(sessao.getId());

	        // O carrinho oficial agora é persistido por sessão, não reconstruído pelo histórico de comandos.
	        boolean temCarrinho = carrinho != null
	             && carrinho.getItens() != null
	             && !carrinho.getItens().isEmpty();

            List<MensagemInterativaBotaoReplyWhatsappDTO> botoes = new ArrayList<>();

            botoes.add(
                MensagemInterativaBotaoReplyWhatsappDTO.builder()
                    .id("COMANDO|FAZER_PEDIDO")
                    .title(msg.trunc(msg.safe("🛎️ Fazer meu pedido"), 20))
                    .build()
            );

            if (temCarrinho) {
                botoes.add(
                    MensagemInterativaBotaoReplyWhatsappDTO.builder()
                        .id("COMANDO|VISUALIZAR_CARRINHO")
                        .title(msg.trunc(msg.safe("🛒 Ver carrinho"), 20))
                        .build()
                );
            }

            String corpo =
                "✅ O estabelecimento *" + safeNome(estabelecimento.getNome()) + "* acabou de abrir!\n\n" +
                    (temCarrinho
                        ? "Vi que você tinha itens no carrinho. Quer continuar de onde parou? 🙂"
                        : "Você já pode fazer seu pedido agora. 🙂");

            MensagemWhatsappSaidaDTO mensagemSaida = msg.botoes(
                waCliente,
                msg.trunc(corpo, 1024),
                botoes
            );

            RespostaWhatsappDTO resposta = RespostaWhatsappDTO.builder()
                .idCorrelacao(n.getIdCorrelacao() != null
                    ? n.getIdCorrelacao()
                    : UUID.randomUUID().toString())
                .timestamp(OffsetDateTime.now().toString())
                .canal("WHATSAPP")
                .whatsappCliente(waCliente)
                .whatsappReceptor(msg.normalizarSomenteDigitos(estabelecimento.getWhatsapp()))
                .phoneNumberId(n.getPhoneNumberId())
                .wamidEntrada(n.getWamidEntrada())
                .mensagem(mensagemSaida)
                .mensagensExtras(List.of())
                .build();

            orazzaWhatsappCallbackClient.enviarRespostaAssincrono(resposta);

            n.setStatus(StatusNotificacaoAberturaEstabelecimento.ENVIADA);
            n.setEnviadoEm(OffsetDateTime.now());
            n.setPendenteKey(null);

            enviados++;
        }

        notificacaoAberturaRepository.saveAll(pendentes);

        System.out.println("[WA] Notificacoes de abertura disparadas: " + enviados
            + " idEstabelecimento=" + estabelecimento.getId());

        return enviados;
    }

    @Transactional
    public void fechar(Long idEstabelecimento) {

        Estabelecimento estabelecimento = buscar(idEstabelecimento);

        if (estabelecimento.isAberto()) {
            estabelecimento.setAberto(false);
            estabelecimentoRepository.save(estabelecimento);
        }
    }

    // =========================
    // Taxa padrão de entrega
    // =========================
    @Transactional
    public Estabelecimento atualizarTaxaEntregaPadrao(Long idEstabelecimento, BigDecimal taxaEntregaPadrao) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (taxaEntregaPadrao != null && taxaEntregaPadrao.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taxaEntregaPadrao inválida");
        }

        Estabelecimento e = buscar(idEstabelecimento);
        e.setTaxaEntregaPadrao(taxaEntregaPadrao);

        return estabelecimentoRepository.save(e);
    }

    private String safeNome(String s) {
        String v = s == null ? "" : s.trim();
        return v.isEmpty() ? "Estabelecimento" : v;
    }

    private String normalizarWhatsapp(String whatsapp) {
        String digits = whatsapp.replaceAll("\\D+", "");
        return digits.trim();
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private String safeTrimOrNull(String s) {
        String v = safeTrim(s);
        return v.isEmpty() ? null : v;
    }
}