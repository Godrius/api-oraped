package br.com.oraped.service.whatsapp.sessao;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelos estados administrativos de produto na sessão WhatsApp.
 *
 * Aplicação:
 * - alteração de preço, nome, descrição e foto de produto
 * - cadastro de categoria/produto por digitação
 * - cadastro de opções de tamanho
 * - alteração de preço por tamanho do produto
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappAdminProdutoService {

    private final SessaoWhatsappStore sessaoStore;

    
    @Transactional(readOnly = true)
    public boolean isAguardandoNovoPreco(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoNovoPreco())
            && sessao.getIdProdutoNovoPreco() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdProdutoNovoPreco(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdProdutoNovoPreco();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetListaNovoPreco(Long idSessao) {
        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaNovoPreco();
        return normalizarOffset(offset);
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaNovoPreco(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovoPreco();
    }

    @Transactional
    public void marcarAguardandoNovoPreco(
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoPreco(true);
        sessao.setIdProdutoNovoPreco(idProduto);
        sessao.setIdCategoriaNovoPreco(idCategoria);
        sessao.setOffsetListaNovoPreco(normalizarOffset(offsetLista));

        // Evita conflito com estados de cliente baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovoPreco(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoPreco(false);
        sessao.setIdProdutoNovoPreco(null);
        sessao.setIdCategoriaNovoPreco(null);
        sessao.setOffsetListaNovoPreco(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoNovoNomeProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoNovoNomeProduto())
            && sessao.getIdProdutoNovoNome() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdProdutoNovoNome(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdProdutoNovoNome();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetListaNovoNome(Long idSessao) {
        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaNovoNome();
        return normalizarOffset(offset);
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaNovoNome(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovoNome();
    }

    @Transactional
    public void marcarAguardandoNovoNomeProduto(
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoNomeProduto(true);
        sessao.setIdProdutoNovoNome(idProduto);
        sessao.setIdCategoriaNovoNome(idCategoria);
        sessao.setOffsetListaNovoNome(normalizarOffset(offsetLista));

        // Evita conflito com estados de cliente baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovoNomeProduto(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoNomeProduto(false);
        sessao.setIdProdutoNovoNome(null);
        sessao.setIdCategoriaNovoNome(null);
        sessao.setOffsetListaNovoNome(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoNovaDescricaoProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoNovaDescricaoProduto())
            && sessao.getIdProdutoNovaDescricao() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdProdutoNovaDescricao(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdProdutoNovaDescricao();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetListaNovaDescricao(Long idSessao) {
        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaNovaDescricao();
        return normalizarOffset(offset);
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaNovaDescricao(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovaDescricao();
    }

    @Transactional
    public void marcarAguardandoNovaDescricaoProduto(
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaDescricaoProduto(true);
        sessao.setIdProdutoNovaDescricao(idProduto);
        sessao.setIdCategoriaNovaDescricao(idCategoria);
        sessao.setOffsetListaNovaDescricao(normalizarOffset(offsetLista));

        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovaDescricaoProduto(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaDescricaoProduto(false);
        sessao.setIdProdutoNovaDescricao(null);
        sessao.setIdCategoriaNovaDescricao(null);
        sessao.setOffsetListaNovaDescricao(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoNovaFotoProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoNovaFotoProduto())
            && sessao.getIdProdutoNovaFoto() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdProdutoNovaFoto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdProdutoNovaFoto();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetListaNovaFoto(Long idSessao) {
        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaNovaFoto();
        return normalizarOffset(offset);
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaNovaFoto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovaFoto();
    }

    @Transactional
    public void marcarAguardandoNovaFotoProduto(
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaFotoProduto(true);
        sessao.setIdProdutoNovaFoto(idProduto);
        sessao.setIdCategoriaNovaFoto(idCategoria);
        sessao.setOffsetListaNovaFoto(normalizarOffset(offsetLista));

        // Evita conflito com estados de cliente baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovaFotoProduto(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovaFotoProduto(false);
        sessao.setIdProdutoNovaFoto(null);
        sessao.setIdCategoriaNovaFoto(null);
        sessao.setOffsetListaNovaFoto(null);

        sessaoStore.salvar(sessao);
    }

    
    // =========================================================
    // ADMIN — Produto (Criar por digitação)
    // =========================================================

    @Transactional(readOnly = true)
    public boolean isAguardandoNovoProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getAguardandoNovoProduto())
            && sessao.getIdCategoriaNovoProduto() != null;
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaNovoProduto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovoProduto();
    }

    @Transactional(readOnly = true)
    public Integer getOffsetListaNovoProduto(Long idSessao) {
        Integer offset = sessaoStore.buscarPorId(idSessao).getOffsetListaNovoProduto();
        return normalizarOffset(offset);
    }

    @Transactional
    public void marcarAguardandoNovoProduto(
        Long idSessao,
        Long idCategoria,
        Integer offsetLista
    ) {

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoProduto(true);
        sessao.setIdCategoriaNovoProduto(idCategoria);
        sessao.setOffsetListaNovoProduto(normalizarOffset(offsetLista));

        // Evita conflito com estados de cliente baseados no campo genérico aguardando.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovoProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setAguardandoNovoProduto(false);
        sessao.setIdCategoriaNovoProduto(null);
        sessao.setOffsetListaNovoProduto(null);

        sessaoStore.salvar(sessao);
    }
     
	
    @Transactional(readOnly = true)
    public boolean isCadastroGuiadoProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        return Boolean.TRUE.equals(sessao.getCadastroGuiadoProduto());
    }

    @Transactional
    public void marcarCadastroGuiadoProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        // Marca que os próximos estados reaproveitados fazem parte do cadastro inicial do produto.
        sessao.setCadastroGuiadoProduto(true);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparCadastroGuiadoProduto(Long idSessao) {
        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setCadastroGuiadoProduto(false);

        sessaoStore.salvar(sessao);
    }
    
    private Integer normalizarOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }
	 
}