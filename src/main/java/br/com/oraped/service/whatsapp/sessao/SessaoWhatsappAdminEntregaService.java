package br.com.oraped.service.whatsapp.sessao;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelos estados administrativos de entrega na sessão WhatsApp.
 * Aplicação: CEP do estabelecimento, taxa por bairro, taxa padrão e bairros atendidos.
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappAdminEntregaService {

    private final SessaoWhatsappStore sessaoStore;

    @Transactional
    public void marcarAguardandoCepEstabelecimento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoCepEstabelecimento(true);

        // Evita conflito com fluxo do cliente baseado no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoCepEstabelecimento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return sessao.isAguardandoCepEstabelecimento();
    }

    @Transactional
    public void limparAguardandoCepEstabelecimento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoCepEstabelecimento(false);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoTaxaEntregaBairro(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        return Boolean.TRUE.equals(sessao.getAguardandoTaxaEntregaBairro())
            && sessao.getIdBairroTaxaEntrega() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdBairroTaxaEntrega(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdBairroTaxaEntrega();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetListaTaxaEntregaBairro(Long idSessao) {

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaTaxaEntregaBairro();
        return normalizarOffset(offset);
    }

    @Transactional
    public void marcarAguardandoTaxaEntregaBairro(
        Long idSessao,
        Long idBairro,
        Integer offsetLista
    ) {

        if (idBairro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoTaxaEntregaBairro(true);
        sessao.setIdBairroTaxaEntrega(idBairro);
        sessao.setOffsetListaTaxaEntregaBairro(normalizarOffset(offsetLista));

        // Evita conflito com fluxo do cliente baseado no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoTaxaEntregaBairro(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoTaxaEntregaBairro(false);
        sessao.setIdBairroTaxaEntrega(null);
        sessao.setOffsetListaTaxaEntregaBairro(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoTaxaEntregaPadrao(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoTaxaEntregaPadrao());
    }

    @Transactional(readOnly = true)
    public Integer getOffsetListaTaxaPadraoVoltar(Long idSessao) {

        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaTaxaPadraoVoltar();
        return normalizarOffset(offset);
    }

    @Transactional
    public void marcarAguardandoTaxaEntregaPadrao(Long idSessao, Integer offsetVoltar) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoTaxaEntregaPadrao(true);
        sessao.setOffsetListaTaxaPadraoVoltar(normalizarOffset(offsetVoltar));

        // Evita conflito com fluxo do cliente baseado no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoTaxaEntregaPadrao(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoTaxaEntregaPadrao(false);
        sessao.setOffsetListaTaxaPadraoVoltar(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoBairrosAtendidos(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoBairrosAtendidos());
    }

    @Transactional
    public void marcarAguardandoBairrosAtendidos(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoBairrosAtendidos(true);

        // Evita conflito com fluxo do cliente baseado no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoBairrosAtendidos(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoBairrosAtendidos(false);

        sessaoStore.salvar(sessao);
    }

    private Integer normalizarOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }
}