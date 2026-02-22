// src/main/java/br/com/oraped/service/whatsapp/SessaoAtendimentoWhatsappService.java
package br.com.oraped.service.whatsapp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.enums.FormaPagamentoPedido;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.repository.whatsapp.SessaoAtendimentoWhatsappRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessaoAtendimentoWhatsappService {

    private final SessaoAtendimentoWhatsappRepository sessaoRepo;

    // =========================================================
    // CONTEXTO GENÉRICO (fluxo do CLIENTE) -> campo "aguardando"
    // =========================================================
    private static final String AGUARDANDO_ENDERECO_ENTREGA = "ENDERECO_ENTREGA";
    private static final String AGUARDANDO_FORMA_PAGAMENTO = "FORMA_PAGAMENTO";
    private static final String AGUARDANDO_TROCO_CONFIRMACAO = "TROCO_CONFIRMACAO";
    private static final String AGUARDANDO_TROCO_VALOR = "TROCO_VALOR";
    private static final String AGUARDANDO_CONFIRMACAO_FINAL = "CONFIRMACAO_FINAL";
    
    // =========================================================
    // BÁSICO (sessão)
    // =========================================================

    @Transactional
    public SessaoAtendimentoWhatsapp obterOuCriar(
        String whatsappCliente,
        String whatsappReceptor,
        Long idEstabelecimento
    ) {

        if (!StringUtils.hasText(whatsappCliente)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappCliente é obrigatório");
        }
        if (!StringUtils.hasText(whatsappReceptor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappReceptor é obrigatório");
        }
        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        String wCliente = whatsappCliente.trim();
        String wReceptor = whatsappReceptor.trim();

        return sessaoRepo.findByWhatsappClienteAndWhatsappReceptor(wCliente, wReceptor)
            .map(s -> {
                s.setIdEstabelecimento(idEstabelecimento);
                garantirDefaults(s);
                s.setUltimaInteracaoEm(OffsetDateTime.now());
                return sessaoRepo.save(s);
            })
            .orElseGet(() -> {
                SessaoAtendimentoWhatsapp s = new SessaoAtendimentoWhatsapp();
                s.setWhatsappCliente(wCliente);
                s.setWhatsappReceptor(wReceptor);
                s.setIdEstabelecimento(idEstabelecimento);
                garantirDefaults(s);
                s.setUltimaInteracaoEm(OffsetDateTime.now());
                return sessaoRepo.save(s);
            });
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
    // ADMIN: PREÇO POR DIGITAÇÃO (já existente no seu fluxo)
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
        s.setAguardando(null);
        salvar(s);
    }

    @Transactional
    public void limparPedidoEmAndamento(Long idSessao) {

        SessaoAtendimentoWhatsapp s = buscarPorId(idSessao);

        s.setAguardando(null);
        s.setEnderecoEntrega(null);
        s.setObservacoesEntrega(null);

        s.setFormaPagamento(null);
        s.setPrecisaTroco(null);
        s.setTrocoPara(null);

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

        //taxas
        s.setAguardandoTaxaEntregaBairro(false);
        s.setIdBairroTaxaEntrega(null);
        s.setOffsetListaTaxaEntregaBairro(null);

        s.setAguardandoTaxaEntregaPadrao(false);
        s.setOffsetListaTaxaPadraoVoltar(null);

        salvar(s);
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
    }

    @Transactional
    private SessaoAtendimentoWhatsapp salvar(SessaoAtendimentoWhatsapp s) {
        s.setUltimaInteracaoEm(OffsetDateTime.now());
        return sessaoRepo.save(s);
    }
}