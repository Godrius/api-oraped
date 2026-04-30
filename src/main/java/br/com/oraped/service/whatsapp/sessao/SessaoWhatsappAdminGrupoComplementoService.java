package br.com.oraped.service.whatsapp.sessao;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelos estados administrativos de grupos de complementos na sessão WhatsApp.
 *
 * Aplicação:
 * - criação de grupo de complemento por digitação
 * - alteração de nome do grupo por digitação
 * - alteração de descrição do grupo por digitação
 *
 * Utilização:
 * Deve ser usado pelos fluxos administrativos do WhatsApp antes de aguardar uma mensagem
 * livre do administrador.
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappAdminGrupoComplementoService {

    private final SessaoWhatsappStore sessaoStore;

    // =========================================================
    // NOVO GRUPO
    // =========================================================

    @Transactional
    public void marcarAguardandoNovoGrupo(Long idSessao, Integer offsetGrupos) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        limparEstadosGrupo(sessao);

        sessao.setAguardandoNovoGrupoComplemento(true);
        sessao.setOffsetNovoGrupoComplemento(normalizarOffset(offsetGrupos));

        // Evita conflito com fluxos baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoNovoGrupo(Long idSessao) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return sessao.isAguardandoNovoGrupoComplemento();
    }

    @Transactional(readOnly = true)
    public int getOffsetNovoGrupo(Long idSessao) {

        validarSessao(idSessao);

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetNovoGrupoComplemento();
        return normalizarOffset(offset);
    }

    @Transactional
    public void limparAguardandoNovoGrupo(Long idSessao) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoGrupoComplemento(false);
        sessao.setOffsetNovoGrupoComplemento(0);

        sessaoStore.salvar(sessao);
    }

    // =========================================================
    // EDITAR NOME DO GRUPO
    // =========================================================

    @Transactional
    public void marcarAguardandoEditarNomeGrupo(
        Long idSessao,
        Long idGrupo,
        Integer offsetGrupos
    ) {

        validarSessao(idSessao);
        validarGrupo(idGrupo);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        limparEstadosGrupo(sessao);

        sessao.setAguardandoEditarNomeGrupoComplemento(true);
        sessao.setIdGrupoComplementoEditarNome(idGrupo);
        sessao.setOffsetEditarNomeGrupoComplemento(normalizarOffset(offsetGrupos));

        // Evita conflito com fluxos baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoEditarNomeGrupo(Long idSessao) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return sessao.isAguardandoEditarNomeGrupoComplemento()
            && sessao.getIdGrupoComplementoEditarNome() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdGrupoEditarNome(Long idSessao) {

        validarSessao(idSessao);

        return sessaoStore.buscarPorId(idSessao).getIdGrupoComplementoEditarNome();
    }

    @Transactional(readOnly = true)
    public int getOffsetEditarNomeGrupo(Long idSessao) {

        validarSessao(idSessao);

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetEditarNomeGrupoComplemento();
        return normalizarOffset(offset);
    }

    @Transactional
    public void limparAguardandoEditarNomeGrupo(Long idSessao) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoEditarNomeGrupoComplemento(false);
        sessao.setIdGrupoComplementoEditarNome(null);
        sessao.setOffsetEditarNomeGrupoComplemento(0);

        sessaoStore.salvar(sessao);
    }

    // =========================================================
    // EDITAR DESCRIÇÃO DO GRUPO
    // =========================================================

    @Transactional
    public void marcarAguardandoEditarDescricaoGrupo(
        Long idSessao,
        Long idGrupo,
        Integer offsetGrupos
    ) {

        validarSessao(idSessao);
        validarGrupo(idGrupo);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        limparEstadosGrupo(sessao);

        sessao.setAguardandoEditarDescricaoGrupoComplemento(true);
        sessao.setIdGrupoComplementoEditarDescricao(idGrupo);
        sessao.setOffsetEditarDescricaoGrupoComplemento(normalizarOffset(offsetGrupos));

        // Evita conflito com fluxos baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoEditarDescricaoGrupo(Long idSessao) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return sessao.isAguardandoEditarDescricaoGrupoComplemento()
            && sessao.getIdGrupoComplementoEditarDescricao() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdGrupoEditarDescricao(Long idSessao) {

        validarSessao(idSessao);

        return sessaoStore.buscarPorId(idSessao).getIdGrupoComplementoEditarDescricao();
    }

    @Transactional(readOnly = true)
    public int getOffsetEditarDescricaoGrupo(Long idSessao) {

        validarSessao(idSessao);

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetEditarDescricaoGrupoComplemento();
        return normalizarOffset(offset);
    }

    @Transactional
    public void limparAguardandoEditarDescricaoGrupo(Long idSessao) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoEditarDescricaoGrupoComplemento(false);
        sessao.setIdGrupoComplementoEditarDescricao(null);
        sessao.setOffsetEditarDescricaoGrupoComplemento(0);

        sessaoStore.salvar(sessao);
    }

    /////////////////////////
    @Transactional
    public void marcarAguardandoNovoComplemento(
        Long idSessao,
        Long idGrupo,
        Integer offsetGrupos
    ) {

        validarSessao(idSessao);
        validarGrupo(idGrupo);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        limparEstadosGrupo(sessao);

        sessao.setAguardandoNovoComplementoGrupo(true);
        sessao.setIdGrupoNovoComplemento(idGrupo);
        sessao.setOffsetNovoComplementoGrupo(normalizarOffset(offsetGrupos));

        // Evita conflito com fluxos baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoNovoComplemento(Long idSessao) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return sessao.isAguardandoNovoComplementoGrupo()
            && sessao.getIdGrupoNovoComplemento() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdGrupoNovoComplemento(Long idSessao) {

        validarSessao(idSessao);

        return sessaoStore.buscarPorId(idSessao).getIdGrupoNovoComplemento();
    }

    @Transactional(readOnly = true)
    public int getOffsetNovoComplementoGrupo(Long idSessao) {

        validarSessao(idSessao);

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetNovoComplementoGrupo();
        return normalizarOffset(offset);
    }

    @Transactional
    public void limparAguardandoNovoComplemento(Long idSessao) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoComplementoGrupo(false);
        sessao.setIdGrupoNovoComplemento(null);
        sessao.setOffsetNovoComplementoGrupo(0);

        sessaoStore.salvar(sessao);
    }
    
    
    // =========================================================
    // LIMPEZA GERAL DO FLUXO DE GRUPO
    // =========================================================

    @Transactional
    public void limparEstadosGrupo(Long idSessao) {

        validarSessao(idSessao);

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        limparEstadosGrupo(sessao);
        
        sessao.setAguardandoNovoComplementoGrupo(false);
        sessao.setIdGrupoNovoComplemento(null);
        sessao.setOffsetNovoComplementoGrupo(0);

        sessaoStore.salvar(sessao);
    }

    private void limparEstadosGrupo(SessaoAtendimentoWhatsapp sessao) {

        sessao.setAguardandoNovoGrupoComplemento(false);
        sessao.setOffsetNovoGrupoComplemento(0);

        sessao.setAguardandoEditarNomeGrupoComplemento(false);
        sessao.setIdGrupoComplementoEditarNome(null);
        sessao.setOffsetEditarNomeGrupoComplemento(0);

        sessao.setAguardandoEditarDescricaoGrupoComplemento(false);
        sessao.setIdGrupoComplementoEditarDescricao(null);
        sessao.setOffsetEditarDescricaoGrupoComplemento(0);
    }

    private void validarSessao(Long idSessao) {

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
    }

    private void validarGrupo(Long idGrupo) {

        if (idGrupo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idGrupo é obrigatório");
        }
    }

    private Integer normalizarOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }
}