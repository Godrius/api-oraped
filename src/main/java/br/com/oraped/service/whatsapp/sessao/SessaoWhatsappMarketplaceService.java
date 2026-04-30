package br.com.oraped.service.whatsapp.sessao;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelo contexto de marketplace dentro da sessão.
 * Aplicação: localização, categoria, transição para estabelecimento e navegação na árvore.
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappMarketplaceService {

    private static final String AGUARDANDO_CEP_REFINAR_MARKETPLACE = "CEP_REFINAR_MARKETPLACE";

    private final SessaoWhatsappStore sessaoStore;
    private final SessaoWhatsappCleaner sessaoCleaner;

    // =========================================================
    // GEOLOCALIZAÇÃO DO CLIENTE (base do discovery)
    // =========================================================

    @Transactional
    public void salvarLocalizacaoOrigemMarketplace(
        Long idSessao,
        Double latitude,
        Double longitude
    ) {

        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "localização é obrigatória");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setLatitudeOrigemCliente(latitude);
        sessao.setLongitudeOrigemCliente(longitude);

        // Mudança de localização invalida a categoria atual.
        sessaoCleaner.limparCategoriaMarketplace(sessao);

        // Remove qualquer pendência de refinamento anterior.
        if (Objects.equals(AGUARDANDO_CEP_REFINAR_MARKETPLACE, sessao.getAguardando())) {
            sessao.setAguardando(null);
        }

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean hasLocalizacaoOrigemMarketplace(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return sessao.getLatitudeOrigemCliente() != null
            && sessao.getLongitudeOrigemCliente() != null;
    }

    // =========================================================
    // CATEGORIA
    // =========================================================

    @Transactional
    public void salvarCategoriaMarketplaceSelecionada(Long idSessao, Long idCategoria) {

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoriaMarketplace é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setIdCategoriaMarketplace(idCategoria);
        sessao.setIdSubcategoriaMarketplace(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparCategoriaMarketplaceSelecionada(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessaoCleaner.limparCategoriaMarketplace(sessao);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaMarketplace(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaMarketplace();
    }

    // =========================================================
    // REFINAMENTO POR CEP
    // =========================================================

    @Transactional
    public void marcarAguardandoCepRefinarMarketplace(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessao.setAguardando(AGUARDANDO_CEP_REFINAR_MARKETPLACE);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoCepRefinarMarketplace(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Objects.equals(AGUARDANDO_CEP_REFINAR_MARKETPLACE, sessao.getAguardando());
    }

    @Transactional
    public void limparAguardandoCepRefinarMarketplace(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        if (Objects.equals(AGUARDANDO_CEP_REFINAR_MARKETPLACE, sessao.getAguardando())) {
            sessao.setAguardando(null);
            sessaoStore.salvar(sessao);
        }
    }

    // =========================================================
    // TRANSIÇÃO PARA ESTABELECIMENTO
    // =========================================================

    @Transactional
    public void vincularEstabelecimentoAoAtendimentoMarketplace(Long idSessao, Long idEstabelecimento) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        // Mantém contexto do marketplace e entra na loja.
        sessao.setIdEstabelecimento(idEstabelecimento);

        // Subcategoria não é usada no MVP.
        sessao.setIdSubcategoriaMarketplace(null);

        if (Objects.equals(AGUARDANDO_CEP_REFINAR_MARKETPLACE, sessao.getAguardando())) {
            sessao.setAguardando(null);
        }

        sessaoStore.salvar(sessao);
    }

    // =========================================================
    // NAVEGAÇÃO NA ÁRVORE
    // =========================================================

    @Transactional
    public void trocarEstabelecimentoMarketplace(Long idSessao, Long idMarketplace) {

        if (idMarketplace == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarketplace é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        // Mantém categoria e localização; remove apenas a loja.
        sessao.setIdEstabelecimento(null);
        sessao.setIdMarketplace(idMarketplace);

        sessaoCleaner.limparPedidoEmAndamento(sessao);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void trocarCategoriaMarketplace(Long idSessao, Long idMarketplace) {

        if (idMarketplace == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarketplace é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        // Mantém localização; limpa loja e categoria.
        sessao.setIdEstabelecimento(null);
        sessao.setIdMarketplace(idMarketplace);

        sessaoCleaner.limparCategoriaMarketplace(sessao);
        sessaoCleaner.limparPedidoEmAndamento(sessao);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void trocarLocalizacaoMarketplace(Long idSessao, Long idMarketplace) {

        if (idMarketplace == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarketplace é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        // Reset completo do discovery.
        sessao.setIdEstabelecimento(null);
        sessao.setIdMarketplace(idMarketplace);

        sessaoCleaner.limparDiscoveryMarketplace(sessao);
        sessaoCleaner.limparPedidoEmAndamento(sessao);

        sessaoStore.salvar(sessao);
    }

    // =========================================================
    // PÓS-PEDIDO (IMPORTANTE)
    // =========================================================

    @Transactional
    public void limparPedidoFinalizadoPreservandoMarketplace(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        // Remove a loja mas mantém contexto do marketplace.
        sessao.setIdEstabelecimento(null);

        sessaoCleaner.limparPedidoEmAndamento(sessao);

        sessaoStore.salvar(sessao);
    }
}