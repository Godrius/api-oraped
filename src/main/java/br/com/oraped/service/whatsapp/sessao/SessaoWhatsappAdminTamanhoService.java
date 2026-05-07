package br.com.oraped.service.whatsapp.sessao;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Controlar os estados administrativos de tamanhos na sessão WhatsApp.
 *
 * Aplicação:
 * Utilizado nos fluxos de cadastro de opções de tamanho, alteração de descrição
 * de tamanho e configuração de preço de produto por tamanho.
 *
 * Utilização:
 * Deve ser chamado por AdminTamanhoService e pelo orquestrador de texto livre.
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappAdminTamanhoService {

    private final SessaoWhatsappStore sessaoStore;

    @Transactional(readOnly = true)
    public boolean isAguardandoNovaOpcaoTamanho(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoNovaOpcaoTamanho())
            && sessao.getIdCategoriaNovaOpcaoTamanho() != null;
    }

    @Transactional(readOnly = true)
    public Long getCategoriaOpcaoTamanho(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovaOpcaoTamanho();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetProdutosOpcaoTamanho(Long idSessao) {
        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetProdutosNovaOpcaoTamanho();
        return normalizarOffset(offset);
    }

    @Transactional
    public void marcarAguardandoNovaOpcaoTamanho(
        Long idSessao,
        Long idCategoria,
        Integer offsetProdutos
    ) {

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaOpcaoTamanho(true);
        sessao.setIdCategoriaNovaOpcaoTamanho(idCategoria);
        sessao.setOffsetProdutosNovaOpcaoTamanho(normalizarOffset(offsetProdutos));

        // Evita conflito com estados de cliente baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovaOpcaoTamanho(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaOpcaoTamanho(false);
        sessao.setIdCategoriaNovaOpcaoTamanho(null);
        sessao.setOffsetProdutosNovaOpcaoTamanho(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoDescricaoOpcaoTamanho(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoDescricaoOpcaoTamanho())
            && sessao.getIdOpcaoTamanhoNovaDescricao() != null
            && sessao.getIdCategoriaOpcaoTamanhoNovaDescricao() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdOpcaoTamanhoNovaDescricao(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdOpcaoTamanhoNovaDescricao();
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaOpcaoTamanhoNovaDescricao(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaOpcaoTamanhoNovaDescricao();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetProdutosOpcaoTamanhoNovaDescricao(Long idSessao) {
        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetProdutosOpcaoTamanhoNovaDescricao();
        return normalizarOffset(offset);
    }

    @Transactional
    public void marcarAguardandoDescricaoOpcaoTamanho(
        Long idSessao,
        Long idOpcaoTamanho,
        Long idCategoria,
        Integer offsetProdutos
    ) {

        if (idOpcaoTamanho == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idOpcaoTamanho é obrigatório");
        }

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoDescricaoOpcaoTamanho(true);
        sessao.setIdOpcaoTamanhoNovaDescricao(idOpcaoTamanho);
        sessao.setIdCategoriaOpcaoTamanhoNovaDescricao(idCategoria);
        sessao.setOffsetProdutosOpcaoTamanhoNovaDescricao(normalizarOffset(offsetProdutos));

        // Evita conflito com estados de cliente baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoDescricaoOpcaoTamanho(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoDescricaoOpcaoTamanho(false);
        sessao.setIdOpcaoTamanhoNovaDescricao(null);
        sessao.setIdCategoriaOpcaoTamanhoNovaDescricao(null);
        sessao.setOffsetProdutosOpcaoTamanhoNovaDescricao(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoNovoPrecoProdutoTamanho(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoNovoPrecoProdutoTamanho())
            && sessao.getIdProdutoNovoPrecoTamanho() != null
            && sessao.getIdOpcaoTamanhoProdutoNovoPreco() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdProdutoNovoPrecoTamanho(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdProdutoNovoPrecoTamanho();
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaNovoPrecoTamanho(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovoPrecoTamanho();
    }

    @Transactional(readOnly = true)
    public Long getIdOpcaoTamanhoProdutoNovoPreco(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdOpcaoTamanhoProdutoNovoPreco();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetListaNovoPrecoTamanho(Long idSessao) {
        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaNovoPrecoTamanho();
        return normalizarOffset(offset);
    }

    @Transactional
    public void marcarAguardandoNovoPrecoProdutoTamanho(
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Long idOpcaoTamanho,
        Integer offsetLista
    ) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        if (idOpcaoTamanho == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idOpcaoTamanho é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoPrecoProdutoTamanho(true);
        sessao.setIdProdutoNovoPrecoTamanho(idProduto);
        sessao.setIdCategoriaNovoPrecoTamanho(idCategoria);
        sessao.setIdOpcaoTamanhoProdutoNovoPreco(idOpcaoTamanho);
        sessao.setOffsetListaNovoPrecoTamanho(normalizarOffset(offsetLista));

        // Evita conflito com estados de cliente baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovoPrecoProdutoTamanho(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoPrecoProdutoTamanho(false);
        sessao.setIdProdutoNovoPrecoTamanho(null);
        sessao.setIdCategoriaNovoPrecoTamanho(null);
        sessao.setIdOpcaoTamanhoProdutoNovoPreco(null);
        sessao.setOffsetListaNovoPrecoTamanho(null);

        sessaoStore.salvar(sessao);
    }

    private Integer normalizarOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }
}