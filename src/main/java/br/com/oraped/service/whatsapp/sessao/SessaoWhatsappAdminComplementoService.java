package br.com.oraped.service.whatsapp.sessao;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelos estados administrativos de complemento na sessão WhatsApp.
 * Aplicação: alteração de preço de complemento por digitação no fluxo administrativo.
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappAdminComplementoService {

    private final SessaoWhatsappStore sessaoStore;

    @Transactional
    public void marcarAguardandoNovoPrecoComplemento(
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Long idGrupo,
        Long idComplemento,
        Integer offsetListaProduto
    ) {

        if (idComplemento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idComplemento é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoPrecoComplemento(true);
        sessao.setIdProdutoNovoPrecoComplemento(idProduto);
        sessao.setIdCategoriaNovoPrecoComplemento(idCategoria);
        sessao.setIdGrupoNovoPrecoComplemento(idGrupo);
        sessao.setIdComplementoNovoPreco(idComplemento);
        sessao.setOffsetListaProdutoNovoPrecoComplemento(normalizarOffset(offsetListaProduto));

        // Evita conflito com fluxos baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoNovoPrecoComplemento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return sessao.isAguardandoNovoPrecoComplemento()
            && sessao.getIdComplementoNovoPreco() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdProdutoNovoPrecoComplemento(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdProdutoNovoPrecoComplemento();
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaNovoPrecoComplemento(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovoPrecoComplemento();
    }

    @Transactional(readOnly = true)
    public Long getIdGrupoNovoPrecoComplemento(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdGrupoNovoPrecoComplemento();
    }

    @Transactional(readOnly = true)
    public Long getIdComplementoNovoPreco(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdComplementoNovoPreco();
    }

    @Transactional(readOnly = true)
    public int getOffsetListaProdutoNovoPrecoComplemento(Long idSessao) {

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaProdutoNovoPrecoComplemento();
        return normalizarOffset(offset);
    }

    @Transactional
    public void limparAguardandoNovoPrecoComplemento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoPrecoComplemento(false);
        sessao.setIdProdutoNovoPrecoComplemento(null);
        sessao.setIdCategoriaNovoPrecoComplemento(null);
        sessao.setIdGrupoNovoPrecoComplemento(null);
        sessao.setIdComplementoNovoPreco(null);
        sessao.setOffsetListaProdutoNovoPrecoComplemento(0);

        sessaoStore.salvar(sessao);
    }

    private Integer normalizarOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }
}