package br.com.oraped.service.whatsapp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.domain.whatsapp.SessaoEncerradaEvent;
import br.com.oraped.repository.whatsapp.SessaoAtendimentoWhatsappRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessaoAtendimentoWhatsappService {

    private final SessaoAtendimentoWhatsappRepository sessaoRepo;
    private final ApplicationEventPublisher eventPublisher; //para encerrar a sessão (apagar as mensagens) de forma assíncrona
    
    // =========================================================
    // CONTEXTO GENÉRICO (fluxo do CLIENTE) -> campo "aguardando"
    // =========================================================
    private static final String AGUARDANDO_QUANTIDADE = "QUANTIDADE_MANUAL";
    private static final String AGUARDANDO_ENDERECO_ENTREGA = "ENDERECO_ENTREGA";
    private static final String AGUARDANDO_FORMA_PAGAMENTO = "FORMA_PAGAMENTO";
    private static final String AGUARDANDO_TROCO_CONFIRMACAO = "TROCO_CONFIRMACAO";
    private static final String AGUARDANDO_TROCO_VALOR = "TROCO_VALOR";
    private static final String AGUARDANDO_CONFIRMACAO_FINAL = "CONFIRMACAO_FINAL";
    private static final String AGUARDANDO_CEP_ENTREGA = "CEP_ENTREGA";
    private static final String AGUARDANDO_COMPLEMENTO_ENDERECO = "COMPLEMENTO_ENDERECO";
    private static final String AGUARDANDO_ENDERECO_COMPLETO_FALLBACK = "ENDERECO_COMPLETO_FALLBACK";

    // =========================================================
    // BÁSICO (sessão)
    // =========================================================

    @Transactional
    public SessaoAtendimentoWhatsapp obterOuCriar(
        String whatsappCliente,
        String whatsappReceptor,
        Long idEstabelecimento
    ) {

        validarObterOuCriar(whatsappCliente, whatsappReceptor, idEstabelecimento);

        String wCliente = whatsappCliente.trim();
        String wReceptor = whatsappReceptor.trim();

        return sessaoRepo.buscarSessaoAtiva(wCliente, wReceptor)
            .map(s -> {
                s.setIdEstabelecimento(idEstabelecimento);
                garantirDefaults(s);
                s.setUltimaInteracaoEm(OffsetDateTime.now());
                return sessaoRepo.save(s);
            })
            .orElseGet(() -> criarNovaSessao(wCliente, wReceptor, idEstabelecimento));
    }

    @Transactional
    public SessaoAtendimentoWhatsapp criarNovaSessao(
        String whatsappCliente,
        String whatsappReceptor,
        Long idEstabelecimento
    ) {

        validarObterOuCriar(whatsappCliente, whatsappReceptor, idEstabelecimento);

        String wCliente = whatsappCliente.trim();
        String wReceptor = whatsappReceptor.trim();

        SessaoAtendimentoWhatsapp s = new SessaoAtendimentoWhatsapp();
        s.setWhatsappCliente(wCliente);
        s.setWhatsappReceptor(wReceptor);
        s.setIdEstabelecimento(idEstabelecimento);

        // garante que é uma sessão NOVA e ATIVA
        s.setEncerradaEm(null);

        garantirDefaults(s);
        s.setUltimaInteracaoEm(OffsetDateTime.now());
        return sessaoRepo.save(s);
    }

    @Transactional
    public void encerrarSessao(Long idSessao) {

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        limparPedidoEmAndamento(idSessao);
        limparAguardando(idSessao);

        s.setEncerradaEm(OffsetDateTime.now());
        sessaoRepo.save(s);
        
        //encerra a sessão e apaga as mensaegens associadas a ela para limpar o BD
        eventPublisher.publishEvent(new SessaoEncerradaEvent(idSessao));
        
    }

    @Transactional
    public void atualizarInteracao(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setUltimaInteracaoEm(OffsetDateTime.now());
        sessaoRepo.save(s);
    }

    public SessaoAtendimentoWhatsapp buscarPorId(Long idSessao) {
        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }

        return sessaoRepo.findById(idSessao)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
    }

    private void validarObterOuCriar(String whatsappCliente, String whatsappReceptor, Long idEstabelecimento) {
        if (!StringUtils.hasText(whatsappCliente)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappCliente é obrigatório");
        }
        if (!StringUtils.hasText(whatsappReceptor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappReceptor é obrigatório");
        }
        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }
    }

    // =========================================================
    // FLUXO CLIENTE: QUANTIDADE MANUAL
    // =========================================================

    @Transactional
    public void marcarAguardandoQuantidadeManual(Long idSessao, Long idProduto) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardando(AGUARDANDO_QUANTIDADE);
        s.setAguardandoQuantidadeManual(true);
        s.setIdProdutoQuantidadeManual(idProduto);

        salvar(s);
    }

    public boolean isAguardandoQuantidadeManual(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_QUANTIDADE, s.getAguardando())
            && Boolean.TRUE.equals(s.getAguardandoQuantidadeManual())
            && s.getIdProdutoQuantidadeManual() != null;
    }

    public Long getIdProdutoQuantidadeManual(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return s.getIdProdutoQuantidadeManual();
    }

    @Transactional
    public void limparAguardandoQuantidadeManual(Long idSessao) {

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        if (Objects.equals(AGUARDANDO_QUANTIDADE, s.getAguardando())
            || Boolean.TRUE.equals(s.getAguardandoQuantidadeManual())
        ) {
            s.setAguardando(null);
            s.setAguardandoQuantidadeManual(false);
            s.setIdProdutoQuantidadeManual(null);
            salvar(s);
        }
    }

    // =========================================================
    // FLUXO CLIENTE: ENDEREÇO
    // =========================================================

    @Transactional
    public void marcarAguardandoEnderecoEntrega(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardando(AGUARDANDO_ENDERECO_ENTREGA);
        salvar(s);
    }

    public boolean isAguardandoEnderecoEntrega(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_ENDERECO_ENTREGA, s.getAguardando());
    }

    @Transactional
    public void salvarEnderecoEntrega(Long idSessao, String endereco, String observacoes) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setEnderecoEntrega(StringUtils.hasText(endereco) ? endereco.trim() : null);
        s.setObservacoesEntrega(StringUtils.hasText(observacoes) ? observacoes.trim() : null);

        // ao salvar endereço, encerra "aguardando" do cliente
        s.setAguardando(null);

        salvar(s);
    }

    @Transactional
    public void desmarcarAguardandoEnderecoEntrega(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        if (Objects.equals(AGUARDANDO_ENDERECO_ENTREGA, s.getAguardando())) {
            s.setAguardando(null);
            salvar(s);
        }
    }

    // =========================================================
    // FLUXO CLIENTE: PAGAMENTO / TROCO
    // =========================================================

    @Transactional
    public void marcarAguardandoFormaPagamento(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardando(AGUARDANDO_FORMA_PAGAMENTO);
        salvar(s);
    }

    public boolean isAguardandoFormaPagamento(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_FORMA_PAGAMENTO, s.getAguardando());
    }

    @Transactional
    public void marcarAguardandoTrocoConfirmacao(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardando(AGUARDANDO_TROCO_CONFIRMACAO);
        salvar(s);
    }

    public boolean isAguardandoTrocoConfirmacao(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_TROCO_CONFIRMACAO, s.getAguardando());
    }

    @Transactional
    public void marcarAguardandoTrocoValor(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardando(AGUARDANDO_TROCO_VALOR);
        salvar(s);
    }

    public boolean isAguardandoTrocoValor(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_TROCO_VALOR, s.getAguardando());
    }

    @Transactional
    public void salvarFormaPagamento(Long idSessao, FormaPagamentoPedido formaPagamento) {
        if (formaPagamento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formaPagamento é obrigatória");
        }

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setFormaPagamento(formaPagamento);

        // reset de troco quando muda para não-dinheiro
        if (formaPagamento != FormaPagamentoPedido.DINHEIRO) {
            s.setPrecisaTroco(null);
            s.setTrocoPara(null);
        }

        s.setAguardando(null);
        salvar(s);
    }

    @Transactional
    public void salvarTrocoNecessidade(Long idSessao, boolean precisaTroco) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setPrecisaTroco(precisaTroco);
        if (!precisaTroco) {
            s.setTrocoPara(null);
        }

        s.setAguardando(null);
        salvar(s);
    }

    @Transactional
    public void salvarTrocoValor(Long idSessao, BigDecimal trocoPara) {
        if (trocoPara == null || trocoPara.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trocoPara inválido");
        }

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setPrecisaTroco(true);
        s.setTrocoPara(trocoPara);

        s.setAguardando(null);
        salvar(s);
    }

    // =========================================================
    // ADMIN: PREÇO POR DIGITAÇÃO
    // =========================================================

    public boolean isAguardandoNovoPreco(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Boolean.TRUE.equals(s.getAguardandoNovoPreco())
            && s.getIdProdutoNovoPreco() != null;
    }

    public Long getIdProdutoNovoPreco(Long idSessao) {
        return buscarPorId(idSessao).getIdProdutoNovoPreco();
    }

    public Integer getOffsetListaNovoPreco(Long idSessao) {
        Integer v = buscarPorId(idSessao).getOffsetListaNovoPreco();
        return v == null ? 0 : Math.max(v, 0);
    }

    @Transactional
    public void marcarAguardandoNovoPreco(Long idSessao, Long idProduto, Integer offsetLista) {
        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoNovoPreco(true);
        s.setIdProdutoNovoPreco(idProduto);
        s.setOffsetListaNovoPreco(offsetLista == null ? 0 : Math.max(offsetLista, 0));

        // evita conflito com fluxo cliente
        s.setAguardando(null);

        salvar(s);
    }

    @Transactional
    public void limparAguardandoNovoPreco(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoNovoPreco(false);
        s.setIdProdutoNovoPreco(null);
        s.setOffsetListaNovoPreco(null);

        salvar(s);
    }

    // =========================================================
    // ADMIN: PRODUTO - NOME POR DIGITAÇÃO
    // =========================================================

    public boolean isAguardandoNovoNomeProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Boolean.TRUE.equals(s.getAguardandoNovoNomeProduto())
            && s.getIdProdutoNovoNome() != null;
    }

    public Long getIdProdutoNovoNome(Long idSessao) {
        return buscarPorId(idSessao).getIdProdutoNovoNome();
    }

    public Integer getOffsetListaNovoNome(Long idSessao) {
        Integer v = buscarPorId(idSessao).getOffsetListaNovoNome();
        return v == null ? 0 : Math.max(v, 0);
    }

    @Transactional
    public void marcarAguardandoNovoNomeProduto(Long idSessao, Long idProduto, Integer offsetLista) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoNovoNomeProduto(true);
        s.setIdProdutoNovoNome(idProduto);
        s.setOffsetListaNovoNome(offsetLista == null ? 0 : Math.max(0, offsetLista));

        // evita conflito com fluxo do cliente
        s.setAguardando(null);

        salvar(s);
    }

    @Transactional
    public void limparAguardandoNovoNomeProduto(Long idSessao) {

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoNovoNomeProduto(false);
        s.setIdProdutoNovoNome(null);
        s.setOffsetListaNovoNome(null);

        salvar(s);
    }

    // =========================================================
    // ADMIN: PRODUTO - DESCRIÇÃO POR DIGITAÇÃO
    // =========================================================

    public boolean isAguardandoNovaDescricaoProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Boolean.TRUE.equals(s.getAguardandoNovaDescricaoProduto())
            && s.getIdProdutoNovaDescricao() != null;
    }

    public Long getIdProdutoNovaDescricao(Long idSessao) {
        return buscarPorId(idSessao).getIdProdutoNovaDescricao();
    }

    public Integer getOffsetListaNovaDescricao(Long idSessao) {
        Integer v = buscarPorId(idSessao).getOffsetListaNovaDescricao();
        return v == null ? 0 : Math.max(v, 0);
    }

    @Transactional
    public void marcarAguardandoNovaDescricaoProduto(Long idSessao, Long idProduto, Integer offsetLista) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoNovaDescricaoProduto(true);
        s.setIdProdutoNovaDescricao(idProduto);
        s.setOffsetListaNovaDescricao(offsetLista == null ? 0 : Math.max(0, offsetLista));

        s.setAguardando(null);

        salvar(s);
    }

    @Transactional
    public void limparAguardandoNovaDescricaoProduto(Long idSessao) {

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoNovaDescricaoProduto(false);
        s.setIdProdutoNovaDescricao(null);
        s.setOffsetListaNovaDescricao(null);

        salvar(s);
    }

    // =========================================================
    // ADMIN: MARCA - CRIAR / EDITAR NOME POR DIGITAÇÃO
    // =========================================================

    public boolean isAguardandoNovaMarca(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Boolean.TRUE.equals(s.getAguardandoNovaMarca());
    }

    @Transactional
    public void marcarAguardandoNovaMarca(Long idSessao, Integer offsetLista) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardandoNovaMarca(true);
        s.setOffsetListaMarcasNova(offsetLista == null ? 0 : Math.max(0, offsetLista));
        s.setAguardando(null);
        salvar(s);
    }

    public Integer getOffsetNovaMarca(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        Integer v = s.getOffsetListaMarcasNova();
        return v == null ? 0 : Math.max(0, v);
    }

    @Transactional
    public void limparAguardandoNovaMarca(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardandoNovaMarca(false);
        s.setOffsetListaMarcasNova(null);
        salvar(s);
    }

    public boolean isAguardandoEditarMarcaNome(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Boolean.TRUE.equals(s.getAguardandoEditarMarcaNome())
            && s.getIdMarcaEditarNome() != null;
    }

    @Transactional
    public void marcarAguardandoEditarMarcaNome(Long idSessao, Long idMarca, Integer offsetLista) {
        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardandoEditarMarcaNome(true);
        s.setIdMarcaEditarNome(idMarca);
        s.setOffsetListaMarcasEditarNome(offsetLista == null ? 0 : Math.max(0, offsetLista));
        s.setAguardando(null);
        salvar(s);
    }

    public Long getIdMarcaEditarNome(Long idSessao) {
        return buscarPorId(idSessao).getIdMarcaEditarNome();
    }

    public Integer getOffsetEditarMarcaNome(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        Integer v = s.getOffsetListaMarcasEditarNome();
        return v == null ? 0 : Math.max(0, v);
    }

    @Transactional
    public void limparAguardandoEditarMarcaNome(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardandoEditarMarcaNome(false);
        s.setIdMarcaEditarNome(null);
        s.setOffsetListaMarcasEditarNome(null);
        salvar(s);
    }

    // =========================================================
    // CEP - EDITAR POR DIGITAÇÃO
    // =========================================================

    @Transactional
    public void marcarAguardandoCepEstabelecimento(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardandoCepEstabelecimento(true);
        s.setAguardando(null);
        salvar(s);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoCepEstabelecimento(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return s.isAguardandoCepEstabelecimento();
    }

    @Transactional
    public void limparAguardandoCepEstabelecimento(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardandoCepEstabelecimento(false);
        salvar(s);
    }

    // =========================================================
    // ADMIN: TAXA ENTREGA POR BAIRRO (DIGITAÇÃO)
    // =========================================================

    public boolean isAguardandoTaxaEntregaBairro(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Boolean.TRUE.equals(s.getAguardandoTaxaEntregaBairro())
            && s.getIdBairroTaxaEntrega() != null;
    }

    public Long getIdBairroTaxaEntrega(Long idSessao) {
        return buscarPorId(idSessao).getIdBairroTaxaEntrega();
    }

    public Integer getOffsetListaTaxaEntregaBairro(Long idSessao) {
        Integer v = buscarPorId(idSessao).getOffsetListaTaxaEntregaBairro();
        return v == null ? 0 : Math.max(0, v);
    }

    @Transactional
    public void marcarAguardandoTaxaEntregaBairro(Long idSessao, Long idBairro, Integer offsetLista) {

        if (idBairro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
        }

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoTaxaEntregaBairro(true);
        s.setIdBairroTaxaEntrega(idBairro);
        s.setOffsetListaTaxaEntregaBairro(offsetLista == null ? 0 : Math.max(0, offsetLista));

        s.setAguardando(null); // evita conflito com fluxo cliente

        salvar(s);
    }

    @Transactional
    public void limparAguardandoTaxaEntregaBairro(Long idSessao) {

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoTaxaEntregaBairro(false);
        s.setIdBairroTaxaEntrega(null);
        s.setOffsetListaTaxaEntregaBairro(null);

        salvar(s);
    }

    // =========================================================
    // ADMIN: TAXA PADRÃO (DIGITAÇÃO)
    // =========================================================

    public boolean isAguardandoTaxaEntregaPadrao(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Boolean.TRUE.equals(s.getAguardandoTaxaEntregaPadrao());
    }

    public Integer getOffsetListaTaxaPadraoVoltar(Long idSessao) {
        Integer v = buscarPorId(idSessao).getOffsetListaTaxaPadraoVoltar();
        return v == null ? 0 : Math.max(0, v);
    }

    @Transactional
    public void marcarAguardandoTaxaEntregaPadrao(Long idSessao, Integer offsetVoltar) {

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoTaxaEntregaPadrao(true);
        s.setOffsetListaTaxaPadraoVoltar(offsetVoltar == null ? 0 : Math.max(0, offsetVoltar));

        s.setAguardando(null);

        salvar(s);
    }

    @Transactional
    public void limparAguardandoTaxaEntregaPadrao(Long idSessao) {

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardandoTaxaEntregaPadrao(false);
        s.setOffsetListaTaxaPadraoVoltar(null);

        salvar(s);
    }

    // =========================================================
    // CLIENTE: TAXA DE ENTREGA DE ACORDO COM ENDEREÇO
    // =========================================================

    @Transactional
    public void marcarAguardandoCepEntrega(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardando(AGUARDANDO_CEP_ENTREGA);
        salvar(s);
    }

    public boolean isAguardandoCepEntrega(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_CEP_ENTREGA, s.getAguardando());
    }

    @Transactional
    public void marcarAguardandoComplementoEndereco(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardando(AGUARDANDO_COMPLEMENTO_ENDERECO);
        salvar(s);
    }

    public boolean isAguardandoComplementoEndereco(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_COMPLEMENTO_ENDERECO, s.getAguardando());
    }

    @Transactional
    public void marcarAguardandoEnderecoCompletoFallback(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardando(AGUARDANDO_ENDERECO_COMPLETO_FALLBACK);
        salvar(s);
    }

    public boolean isAguardandoEnderecoCompletoFallback(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_ENDERECO_COMPLETO_FALLBACK, s.getAguardando());
    }

    @Transactional
    public void salvarEnderecoResolvidoPorCep(
        Long idSessao,
        String cep8,
        String enderecoBaseResolvido,
        String bairro,
        String cidade,
        String uf,
        Double latitude,
        Double longitude,
        BigDecimal taxaEntregaCalculada
    ) {

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setCepEntrega(StringUtils.hasText(cep8) ? cep8.trim() : null);
        s.setEnderecoBaseResolvido(StringUtils.hasText(enderecoBaseResolvido) ? enderecoBaseResolvido.trim() : null);

        s.setBairroEntrega(StringUtils.hasText(bairro) ? bairro.trim() : null);
        s.setCidadeEntrega(StringUtils.hasText(cidade) ? cidade.trim() : null);
        s.setUfEntrega(StringUtils.hasText(uf) ? uf.trim() : null);

        s.setLatitudeEntrega(latitude);
        s.setLongitudeEntrega(longitude);

        s.setTaxaEntregaCalculada(taxaEntregaCalculada);

        // ainda não finaliza enderecoEntrega aqui; vai finalizar com complemento
        s.setEnderecoEntrega(null);
        s.setObservacoesEntrega(null);

        salvar(s);
    }

    @Transactional
    public void salvarComplementoFinalizarEndereco(
        Long idSessao,
        String enderecoFinal,
        String observacoes
    ) {

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setEnderecoEntrega(StringUtils.hasText(enderecoFinal) ? enderecoFinal.trim() : null);
        s.setObservacoesEntrega(StringUtils.hasText(observacoes) ? observacoes.trim() : null);

        // encerra aguardando do cliente
        s.setAguardando(null);

        salvar(s);
    }

    // =========================================================
    // CONFIRMAÇÃO FINAL
    // =========================================================

    @Transactional
    public void marcarAguardandoConfirmacaoFinal(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        s.setAguardando(AGUARDANDO_CONFIRMACAO_FINAL);
        salvar(s);
    }

    public boolean isAguardandoConfirmacaoFinal(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_CONFIRMACAO_FINAL, s.getAguardando());
    }

    @Transactional
    public void desmarcarAguardandoConfirmacaoFinal(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        if (Objects.equals(AGUARDANDO_CONFIRMACAO_FINAL, s.getAguardando())) {
            s.setAguardando(null);
            salvar(s);
        }
    }

    // =========================================================
    // LIMPEZA
    // =========================================================

    @Transactional
    public void limparAguardando(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        limparAguardandoNoEntity(s);
        salvar(s);
    }

    @Transactional
    public void limparPedidoEmAndamento(Long idSessao) {
        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);
        limparPedidoEmAndamentoNoEntity(s);
        salvar(s);
    }

    private void limparAguardandoNoEntity(SessaoAtendimentoWhatsapp s) {

        // CLIENTE: estado genérico
        s.setAguardando(null);

        // CLIENTE: quantidade manual
        s.setAguardandoQuantidadeManual(false);
        s.setIdProdutoQuantidadeManual(null);

        // ADMIN: flags de digitação
        s.setAguardandoNovoPreco(false);
        s.setIdProdutoNovoPreco(null);
        s.setOffsetListaNovoPreco(null);

        s.setAguardandoNovoNomeProduto(false);
        s.setIdProdutoNovoNome(null);
        s.setOffsetListaNovoNome(null);

        s.setAguardandoNovaDescricaoProduto(false);
        s.setIdProdutoNovaDescricao(null);
        s.setOffsetListaNovaDescricao(null);

        s.setAguardandoNovaMarca(false);
        s.setOffsetListaMarcasNova(null);

        s.setAguardandoEditarMarcaNome(false);
        s.setIdMarcaEditarNome(null);
        s.setOffsetListaMarcasEditarNome(null);

        s.setAguardandoCepEstabelecimento(false);

        s.setAguardandoTaxaEntregaBairro(false);
        s.setIdBairroTaxaEntrega(null);
        s.setOffsetListaTaxaEntregaBairro(null);

        s.setAguardandoTaxaEntregaPadrao(false);
        s.setOffsetListaTaxaPadraoVoltar(null);
    }

    private void limparPedidoEmAndamentoNoEntity(SessaoAtendimentoWhatsapp s) {

        s.setAguardando(null);
        s.setEnderecoEntrega(null);
        s.setObservacoesEntrega(null);

        s.setFormaPagamento(null);
        s.setPrecisaTroco(null);
        s.setTrocoPara(null);

        // ADMIN: flags
        s.setAguardandoNovoPreco(false);
        s.setIdProdutoNovoPreco(null);
        s.setOffsetListaNovoPreco(null);

        s.setAguardandoNovoNomeProduto(false);
        s.setIdProdutoNovoNome(null);
        s.setOffsetListaNovoNome(null);

        s.setAguardandoNovaDescricaoProduto(false);
        s.setIdProdutoNovaDescricao(null);
        s.setOffsetListaNovaDescricao(null);

        s.setAguardandoNovaMarca(false);
        s.setOffsetListaMarcasNova(null);

        s.setAguardandoEditarMarcaNome(false);
        s.setIdMarcaEditarNome(null);
        s.setOffsetListaMarcasEditarNome(null);

        s.setAguardandoCepEstabelecimento(false);

        // taxas
        s.setAguardandoTaxaEntregaBairro(false);
        s.setIdBairroTaxaEntrega(null);
        s.setOffsetListaTaxaEntregaBairro(null);

        s.setAguardandoTaxaEntregaPadrao(false);
        s.setOffsetListaTaxaPadraoVoltar(null);

        // quantidade manual
        s.setAguardandoQuantidadeManual(false);
        s.setIdProdutoQuantidadeManual(null);

        // endereço estruturado
        s.setCepEntrega(null);
        s.setBairroEntrega(null);
        s.setCidadeEntrega(null);
        s.setUfEntrega(null);
        s.setLatitudeEntrega(null);
        s.setLongitudeEntrega(null);
        s.setTaxaEntregaCalculada(null);
        s.setEnderecoBaseResolvido(null);
    }

    // =========================================================
    // INTERNOS
    // =========================================================

    private void garantirDefaults(SessaoAtendimentoWhatsapp s) {
        if (s.getAguardandoNovoPreco() == null) s.setAguardandoNovoPreco(false);
        if (s.getAguardandoNovaMarca() == null) s.setAguardandoNovaMarca(false);
        if (s.getAguardandoEditarMarcaNome() == null) s.setAguardandoEditarMarcaNome(false);
        if (s.getAguardandoNovoNomeProduto() == null) s.setAguardandoNovoNomeProduto(false);
        if (s.getAguardandoNovaDescricaoProduto() == null) s.setAguardandoNovaDescricaoProduto(false);

        if (s.getAguardandoTaxaEntregaBairro() == null) s.setAguardandoTaxaEntregaBairro(false);
        if (s.getAguardandoTaxaEntregaPadrao() == null) s.setAguardandoTaxaEntregaPadrao(false);

        if (s.getAguardandoQuantidadeManual() == null) s.setAguardandoQuantidadeManual(false);
    }

    @Transactional
    private SessaoAtendimentoWhatsapp salvar(SessaoAtendimentoWhatsapp s) {
        s.setUltimaInteracaoEm(OffsetDateTime.now());
        return sessaoRepo.save(s);
    }
}