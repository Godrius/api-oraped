package br.com.oraped.service.whatsapp.sessao;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelos estados da sessão usados no fluxo do cliente.
 * Aplicação: quantidade manual, endereço, pagamento, troco e confirmação final do pedido.
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappClienteService {

    private static final String AGUARDANDO_QUANTIDADE = "QUANTIDADE_MANUAL";
    private static final String AGUARDANDO_ENDERECO_ENTREGA = "ENDERECO_ENTREGA";
    private static final String AGUARDANDO_FORMA_PAGAMENTO = "FORMA_PAGAMENTO";
    private static final String AGUARDANDO_TROCO_CONFIRMACAO = "TROCO_CONFIRMACAO";
    private static final String AGUARDANDO_TROCO_VALOR = "TROCO_VALOR";
    private static final String AGUARDANDO_CONFIRMACAO_FINAL = "CONFIRMACAO_FINAL";
    private static final String AGUARDANDO_CEP_ENTREGA = "CEP_ENTREGA";
    private static final String AGUARDANDO_COMPLEMENTO_ENDERECO = "COMPLEMENTO_ENDERECO";
    private static final String AGUARDANDO_ENDERECO_COMPLETO_FALLBACK = "ENDERECO_COMPLETO_FALLBACK";

    private final SessaoWhatsappStore sessaoStore;

    @Transactional
    public void marcarAguardandoQuantidadeManual(Long idSessao, Long idProduto) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardando(AGUARDANDO_QUANTIDADE);
        sessao.setAguardandoQuantidadeManual(true);
        sessao.setIdProdutoQuantidadeManual(idProduto);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoQuantidadeManual(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        return Objects.equals(AGUARDANDO_QUANTIDADE, sessao.getAguardando())
            && Boolean.TRUE.equals(sessao.getAguardandoQuantidadeManual())
            && sessao.getIdProdutoQuantidadeManual() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdProdutoQuantidadeManual(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdProdutoQuantidadeManual();
    }

    @Transactional
    public void limparAguardandoQuantidadeManual(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        if (Objects.equals(AGUARDANDO_QUANTIDADE, sessao.getAguardando())
            || Boolean.TRUE.equals(sessao.getAguardandoQuantidadeManual())
        ) {
            sessao.setAguardando(null);
            sessao.setAguardandoQuantidadeManual(false);
            sessao.setIdProdutoQuantidadeManual(null);

            sessaoStore.salvar(sessao);
        }
    }

    @Transactional
    public void marcarAguardandoEnderecoEntrega(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessao.setAguardando(AGUARDANDO_ENDERECO_ENTREGA);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoEnderecoEntrega(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_ENDERECO_ENTREGA, sessao.getAguardando());
    }

    @Transactional
    public void salvarEnderecoEntrega(Long idSessao, String endereco, String observacoes) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setEnderecoEntrega(StringUtils.hasText(endereco) ? endereco.trim() : null);
        sessao.setObservacoesEntrega(StringUtils.hasText(observacoes) ? observacoes.trim() : null);

        // Ao salvar o endereço textual, a pendência de endereço termina.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void desmarcarAguardandoEnderecoEntrega(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        if (Objects.equals(AGUARDANDO_ENDERECO_ENTREGA, sessao.getAguardando())) {
            sessao.setAguardando(null);
            sessaoStore.salvar(sessao);
        }
    }

    @Transactional
    public void marcarAguardandoFormaPagamento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessao.setAguardando(AGUARDANDO_FORMA_PAGAMENTO);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoFormaPagamento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_FORMA_PAGAMENTO, sessao.getAguardando());
    }

    @Transactional
    public void marcarAguardandoTrocoConfirmacao(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessao.setAguardando(AGUARDANDO_TROCO_CONFIRMACAO);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoTrocoConfirmacao(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_TROCO_CONFIRMACAO, sessao.getAguardando());
    }

    @Transactional
    public void marcarAguardandoTrocoValor(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessao.setAguardando(AGUARDANDO_TROCO_VALOR);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoTrocoValor(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_TROCO_VALOR, sessao.getAguardando());
    }

    @Transactional
    public void salvarFormaPagamento(Long idSessao, FormaPagamentoPedido formaPagamento) {

        if (formaPagamento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formaPagamento é obrigatória");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setFormaPagamento(formaPagamento);

        // Troco só faz sentido para pagamento em dinheiro.
        if (formaPagamento != FormaPagamentoPedido.DINHEIRO) {
            sessao.setPrecisaTroco(null);
            sessao.setTrocoPara(null);
        }

        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void salvarTrocoNecessidade(Long idSessao, boolean precisaTroco) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setPrecisaTroco(precisaTroco);

        if (!precisaTroco) {
            sessao.setTrocoPara(null);
        }

        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void salvarTrocoValor(Long idSessao, BigDecimal trocoPara) {

        if (trocoPara == null || trocoPara.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trocoPara inválido");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setPrecisaTroco(true);
        sessao.setTrocoPara(trocoPara);
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void marcarAguardandoCepEntrega(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessao.setAguardando(AGUARDANDO_CEP_ENTREGA);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoCepEntrega(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_CEP_ENTREGA, sessao.getAguardando());
    }

    @Transactional
    public void marcarAguardandoComplementoEndereco(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessao.setAguardando(AGUARDANDO_COMPLEMENTO_ENDERECO);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoComplementoEndereco(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_COMPLEMENTO_ENDERECO, sessao.getAguardando());
    }

    @Transactional
    public void marcarAguardandoEnderecoCompletoFallback(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessao.setAguardando(AGUARDANDO_ENDERECO_COMPLETO_FALLBACK);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoEnderecoCompletoFallback(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_ENDERECO_COMPLETO_FALLBACK, sessao.getAguardando());
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

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setCepEntrega(StringUtils.hasText(cep8) ? cep8.trim() : null);
        sessao.setEnderecoBaseResolvido(
            StringUtils.hasText(enderecoBaseResolvido) ? enderecoBaseResolvido.trim() : null
        );

        sessao.setBairroEntrega(StringUtils.hasText(bairro) ? bairro.trim() : null);
        sessao.setCidadeEntrega(StringUtils.hasText(cidade) ? cidade.trim() : null);
        sessao.setUfEntrega(StringUtils.hasText(uf) ? uf.trim() : null);

        sessao.setLatitudeEntrega(latitude);
        sessao.setLongitudeEntrega(longitude);
        sessao.setTaxaEntregaCalculada(taxaEntregaCalculada);

        // O endereço final só é montado depois do complemento informado pelo cliente.
        sessao.setEnderecoEntrega(null);
        sessao.setObservacoesEntrega(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void salvarComplementoFinalizarEndereco(
        Long idSessao,
        String enderecoFinal,
        String observacoes
    ) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setEnderecoEntrega(StringUtils.hasText(enderecoFinal) ? enderecoFinal.trim() : null);
        sessao.setObservacoesEntrega(StringUtils.hasText(observacoes) ? observacoes.trim() : null);
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void marcarAguardandoConfirmacaoFinal(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessao.setAguardando(AGUARDANDO_CONFIRMACAO_FINAL);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoConfirmacaoFinal(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_CONFIRMACAO_FINAL, sessao.getAguardando());
    }

    @Transactional
    public void desmarcarAguardandoConfirmacaoFinal(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        if (Objects.equals(AGUARDANDO_CONFIRMACAO_FINAL, sessao.getAguardando())) {
            sessao.setAguardando(null);
            sessaoStore.salvar(sessao);
        }
    }

    @Transactional
    public void salvarEnderecoEntregaEstruturado(
        Long idSessao,
        String enderecoEntrega,
        String observacoesEntrega,
        String cepEntrega,
        String bairroEntrega,
        String cidadeEntrega,
        String ufEntrega,
        Double latitudeEntrega,
        Double longitudeEntrega,
        BigDecimal taxaEntregaCalculada
    ) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setEnderecoEntrega(StringUtils.hasText(enderecoEntrega) ? enderecoEntrega.trim() : null);
        sessao.setObservacoesEntrega(StringUtils.hasText(observacoesEntrega) ? observacoesEntrega.trim() : null);

        sessao.setCepEntrega(StringUtils.hasText(cepEntrega) ? cepEntrega.trim() : null);
        sessao.setBairroEntrega(StringUtils.hasText(bairroEntrega) ? bairroEntrega.trim() : null);
        sessao.setCidadeEntrega(StringUtils.hasText(cidadeEntrega) ? cidadeEntrega.trim() : null);
        sessao.setUfEntrega(StringUtils.hasText(ufEntrega) ? ufEntrega.trim() : null);

        sessao.setLatitudeEntrega(latitudeEntrega);
        sessao.setLongitudeEntrega(longitudeEntrega);
        sessao.setTaxaEntregaCalculada(taxaEntregaCalculada);

        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }
}