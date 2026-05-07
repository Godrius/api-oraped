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

    //NOME
    @Transactional
    public void marcarAguardandoEditarNomeComplementoGlobal(
        Long idSessao,
        Long idGrupo,
        Long idComplemento,
        Integer offsetGrupos
    ) {

        if (idGrupo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idGrupo é obrigatório");
        }

        if (idComplemento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idComplemento é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoEditarNomeComplementoGlobal(true);
        sessao.setIdGrupoEditarNomeComplementoGlobal(idGrupo);
        sessao.setIdComplementoEditarNomeGlobal(idComplemento);
        sessao.setOffsetEditarNomeComplementoGlobal(normalizarOffset(offsetGrupos));

        // Evita conflito com edição de preço do complemento.
        limparContextoNovoPrecoComplemento(sessao);

        // Evita conflito com fluxos baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoEditarNomeComplementoGlobal(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        return sessao.isAguardandoEditarNomeComplementoGlobal()
            && sessao.getIdGrupoEditarNomeComplementoGlobal() != null
            && sessao.getIdComplementoEditarNomeGlobal() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdGrupoEditarNomeComplementoGlobal(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdGrupoEditarNomeComplementoGlobal();
    }

    @Transactional(readOnly = true)
    public Long getIdComplementoEditarNomeGlobal(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdComplementoEditarNomeGlobal();
    }

    @Transactional(readOnly = true)
    public int getOffsetEditarNomeComplementoGlobal(Long idSessao) {

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetEditarNomeComplementoGlobal();
        return normalizarOffset(offset);
    }

    @Transactional
    public void limparAguardandoEditarNomeComplementoGlobal(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoEditarNomeComplementoGlobal(false);
        sessao.setIdGrupoEditarNomeComplementoGlobal(null);
        sessao.setIdComplementoEditarNomeGlobal(null);
        sessao.setOffsetEditarNomeComplementoGlobal(0);

        sessaoStore.salvar(sessao);
    }
    
    //PREÇOS
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
    
    
    private void limparContextoNovoPrecoComplemento(SessaoAtendimentoWhatsapp sessao) {

        sessao.setAguardandoNovoPrecoComplemento(false);
        sessao.setIdProdutoNovoPrecoComplemento(null);
        sessao.setIdCategoriaNovoPrecoComplemento(null);
        sessao.setIdGrupoNovoPrecoComplemento(null);
        sessao.setIdComplementoNovoPreco(null);
        sessao.setOffsetListaProdutoNovoPrecoComplemento(0);
    }
}