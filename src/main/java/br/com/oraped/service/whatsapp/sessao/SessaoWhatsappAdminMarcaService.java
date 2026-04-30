package br.com.oraped.service.whatsapp.sessao;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelos estados administrativos de marca na sessão WhatsApp.
 * Aplicação: criação e edição de marca via digitação no fluxo administrativo.
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappAdminMarcaService {

    private final SessaoWhatsappStore sessaoStore;

    @Transactional(readOnly = true)
    public boolean isAguardandoNovaMarca(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoNovaMarca());
    }

    @Transactional
    public void marcarAguardandoNovaMarca(Long idSessao, Integer offsetLista) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaMarca(true);
        sessao.setOffsetListaMarcasNova(normalizarOffset(offsetLista));

        // Evita conflito com fluxo do cliente baseado em "aguardando"
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public Integer getOffsetNovaMarca(Long idSessao) {

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaMarcasNova();
        return normalizarOffset(offset);
    }

    @Transactional
    public void limparAguardandoNovaMarca(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaMarca(false);
        sessao.setOffsetListaMarcasNova(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoEditarMarcaNome(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        return Boolean.TRUE.equals(sessao.getAguardandoEditarMarcaNome())
            && sessao.getIdMarcaEditarNome() != null;
    }

    @Transactional
    public void marcarAguardandoEditarMarcaNome(
        Long idSessao,
        Long idMarca,
        Integer offsetLista
    ) {

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoEditarMarcaNome(true);
        sessao.setIdMarcaEditarNome(idMarca);
        sessao.setOffsetListaMarcasEditarNome(normalizarOffset(offsetLista));

        // Evita conflito com fluxo do cliente
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public Long getIdMarcaEditarNome(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdMarcaEditarNome();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetEditarMarcaNome(Long idSessao) {

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaMarcasEditarNome();
        return normalizarOffset(offset);
    }

    @Transactional
    public void limparAguardandoEditarMarcaNome(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoEditarMarcaNome(false);
        sessao.setIdMarcaEditarNome(null);
        sessao.setOffsetListaMarcasEditarNome(null);

        sessaoStore.salvar(sessao);
    }

    private Integer normalizarOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }
}