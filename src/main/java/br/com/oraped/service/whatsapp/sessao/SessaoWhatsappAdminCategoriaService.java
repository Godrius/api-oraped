package br.com.oraped.service.whatsapp.sessao;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Controlar os estados administrativos de categoria na sessão WhatsApp.
 *
 * Aplicação:
 * Utilizado nos fluxos de cadastro de categorias por digitação.
 *
 * Utilização:
 * Deve ser chamado pelos serviços administrativos de categoria e pelo orquestrador
 * quando houver texto livre pendente relacionado à categoria.
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappAdminCategoriaService {

    private final SessaoWhatsappStore sessaoStore;

    @Transactional(readOnly = true)
    public boolean isAguardandoNovaCategoria(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoNovaCategoria());
    }

    @Transactional(readOnly = true)
    public Integer getOffsetListaNovaCategoria(Long idSessao) {
        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaNovaCategoria();
        return normalizarOffset(offset);
    }

    @Transactional
    public void marcarAguardandoNovaCategoria(Long idSessao, Integer offsetLista) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaCategoria(true);
        sessao.setOffsetListaNovaCategoria(normalizarOffset(offsetLista));

        // Evita conflito com estados de cliente baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovaCategoria(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaCategoria(false);
        sessao.setOffsetListaNovaCategoria(null);

        sessaoStore.salvar(sessao);
    }

    private Integer normalizarOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }
}