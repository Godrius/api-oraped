package br.com.oraped.service.whatsapp.sessao;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelos estados administrativos de complemento na sessão WhatsApp.
 *
 * Aplicação:
 * - alteração de nome de complemento global
 * - alteração de preço de complemento
 * - cadastro guiado de complemento próprio de produto
 */
@Service
@RequiredArgsConstructor
public class SessaoWhatsappAdminComplementoService {

    public static final String ETAPA_PRODUTO_COMPLEMENTO_NOME = "NOME";
    public static final String ETAPA_PRODUTO_COMPLEMENTO_DESCRICAO = "DESCRICAO";
    public static final String ETAPA_PRODUTO_COMPLEMENTO_PRECO = "PRECO";
    public static final String ETAPA_PRODUTO_COMPLEMENTO_REGRAS = "REGRAS";

    private final SessaoWhatsappStore sessaoStore;

    
    @Transactional
    public void marcarAguardandoNovoComplementoCategoria(
        Long idSessao,
        Long idCategoria,
        Long idGrupo,
        Integer offsetCategorias
    ) {

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        if (idGrupo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idGrupo é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        limparContextoCadastroGuiadoComplementoCategoria(sessao);
        limparContextoCadastroGuiadoComplementoProduto(sessao);
        limparContextoNovoPrecoComplemento(sessao);

        sessao.setAguardandoNovoComplementoCategoria(true);
        sessao.setEtapaNovoComplementoCategoria(ETAPA_PRODUTO_COMPLEMENTO_NOME);
        sessao.setIdCategoriaNovoComplementoCategoria(idCategoria);
        sessao.setIdGrupoNovoComplementoCategoria(idGrupo);
        sessao.setOffsetListaCategoriaNovoComplemento(normalizarOffset(offsetCategorias));
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoNovoComplementoCategoria(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        return sessao.isAguardandoNovoComplementoCategoria()
            && sessao.getIdCategoriaNovoComplementoCategoria() != null
            && sessao.getIdGrupoNovoComplementoCategoria() != null;
    }

    @Transactional(readOnly = true)
    public String getEtapaNovoComplementoCategoria(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getEtapaNovoComplementoCategoria();
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaNovoComplementoCategoria(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovoComplementoCategoria();
    }

    @Transactional(readOnly = true)
    public Long getIdGrupoNovoComplementoCategoria(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdGrupoNovoComplementoCategoria();
    }

    @Transactional(readOnly = true)
    public int getOffsetListaCategoriaNovoComplemento(Long idSessao) {
        return normalizarOffset(sessaoStore.buscarPorId(idSessao).getOffsetListaCategoriaNovoComplemento());
    }

    @Transactional(readOnly = true)
    public String getNomeNovoComplementoCategoria(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getNomeNovoComplementoCategoria();
    }

    @Transactional(readOnly = true)
    public String getDescricaoNovoComplementoCategoria(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getDescricaoNovoComplementoCategoria();
    }

    @Transactional(readOnly = true)
    public BigDecimal getPrecoNovoComplementoCategoria(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getPrecoNovoComplementoCategoria();
    }

    @Transactional
    public void salvarNomeNovoComplementoCategoria(Long idSessao, String nome) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setNomeNovoComplementoCategoria(nome);
        sessao.setEtapaNovoComplementoCategoria(ETAPA_PRODUTO_COMPLEMENTO_DESCRICAO);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void salvarDescricaoNovoComplementoCategoria(Long idSessao, String descricao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setDescricaoNovoComplementoCategoria(descricao);
        sessao.setEtapaNovoComplementoCategoria(ETAPA_PRODUTO_COMPLEMENTO_PRECO);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void salvarPrecoNovoComplementoCategoria(Long idSessao, BigDecimal preco) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setPrecoNovoComplementoCategoria(preco);
        sessao.setEtapaNovoComplementoCategoria(ETAPA_PRODUTO_COMPLEMENTO_REGRAS);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovoComplementoCategoria(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        limparContextoCadastroGuiadoComplementoCategoria(sessao);

        sessaoStore.salvar(sessao);
    }
    
    
    @Transactional
    public void marcarAguardandoNovoComplementoProduto(
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Long idGrupo,
        Integer offsetListaProduto
    ) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        if (idGrupo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idGrupo é obrigatório");
        }

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        limparContextoCadastroGuiadoComplementoProduto(sessao);
        limparContextoNovoPrecoComplemento(sessao);
        limparContextoCadastroGuiadoComplementoCategoria(sessao);
        
        sessao.setAguardandoNovoComplementoProduto(true);
        sessao.setEtapaNovoComplementoProduto(ETAPA_PRODUTO_COMPLEMENTO_NOME);
        sessao.setIdProdutoNovoComplementoProduto(idProduto);
        sessao.setIdCategoriaNovoComplementoProduto(idCategoria);
        sessao.setIdGrupoNovoComplementoProduto(idGrupo);
        sessao.setOffsetListaProdutoNovoComplemento(normalizarOffset(offsetListaProduto));

        // Garante que texto livre seja tratado pelo fluxo guiado correto.
        sessao.setAguardando(null);

        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public boolean isAguardandoNovoComplementoProduto(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        return sessao.isAguardandoNovoComplementoProduto()
            && sessao.getIdProdutoNovoComplementoProduto() != null
            && sessao.getIdCategoriaNovoComplementoProduto() != null
            && sessao.getIdGrupoNovoComplementoProduto() != null;
    }

    @Transactional(readOnly = true)
    public String getEtapaNovoComplementoProduto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getEtapaNovoComplementoProduto();
    }

    @Transactional(readOnly = true)
    public Long getIdProdutoNovoComplementoProduto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdProdutoNovoComplementoProduto();
    }

    @Transactional(readOnly = true)
    public Long getIdCategoriaNovoComplementoProduto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdCategoriaNovoComplementoProduto();
    }

    @Transactional(readOnly = true)
    public Long getIdGrupoNovoComplementoProduto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getIdGrupoNovoComplementoProduto();
    }

    @Transactional(readOnly = true)
    public int getOffsetListaProdutoNovoComplemento(Long idSessao) {
        return normalizarOffset(sessaoStore.buscarPorId(idSessao).getOffsetListaProdutoNovoComplemento());
    }

    @Transactional(readOnly = true)
    public String getNomeNovoComplementoProduto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getNomeNovoComplementoProduto();
    }

    @Transactional(readOnly = true)
    public String getDescricaoNovoComplementoProduto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getDescricaoNovoComplementoProduto();
    }

    @Transactional(readOnly = true)
    public BigDecimal getPrecoNovoComplementoProduto(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao).getPrecoNovoComplementoProduto();
    }

    @Transactional
    public void salvarNomeNovoComplementoProduto(Long idSessao, String nome) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setNomeNovoComplementoProduto(nome);
        sessao.setEtapaNovoComplementoProduto(ETAPA_PRODUTO_COMPLEMENTO_DESCRICAO);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void salvarDescricaoNovoComplementoProduto(Long idSessao, String descricao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setDescricaoNovoComplementoProduto(descricao);
        sessao.setEtapaNovoComplementoProduto(ETAPA_PRODUTO_COMPLEMENTO_PRECO);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void salvarPrecoNovoComplementoProduto(Long idSessao, BigDecimal preco) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessao.setPrecoNovoComplementoProduto(preco);
        sessao.setEtapaNovoComplementoProduto(ETAPA_PRODUTO_COMPLEMENTO_REGRAS);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparAguardandoNovoComplementoProduto(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        limparContextoCadastroGuiadoComplementoProduto(sessao);

        sessaoStore.salvar(sessao);
    }

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

        limparContextoNovoPrecoComplemento(sessao);
        limparContextoCadastroGuiadoComplementoProduto(sessao);
        limparContextoCadastroGuiadoComplementoCategoria(sessao);
        
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
        return normalizarOffset(sessaoStore.buscarPorId(idSessao).getOffsetEditarNomeComplementoGlobal());
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

        limparContextoCadastroGuiadoComplementoProduto(sessao);
        limparContextoCadastroGuiadoComplementoCategoria(sessao);
        
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
        return normalizarOffset(sessaoStore.buscarPorId(idSessao).getOffsetListaProdutoNovoPrecoComplemento());
    }

    @Transactional
    public void limparAguardandoNovoPrecoComplemento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        limparContextoNovoPrecoComplemento(sessao);

        sessaoStore.salvar(sessao);
    }

    private void limparContextoCadastroGuiadoComplementoProduto(SessaoAtendimentoWhatsapp sessao) {

        sessao.setAguardandoNovoComplementoProduto(false);
        sessao.setEtapaNovoComplementoProduto(null);
        sessao.setIdProdutoNovoComplementoProduto(null);
        sessao.setIdCategoriaNovoComplementoProduto(null);
        sessao.setIdGrupoNovoComplementoProduto(null);
        sessao.setOffsetListaProdutoNovoComplemento(0);
        sessao.setNomeNovoComplementoProduto(null);
        sessao.setDescricaoNovoComplementoProduto(null);
        sessao.setPrecoNovoComplementoProduto(null);
        sessao.setMinimoNovoComplementoProduto(null);
        sessao.setMaximoNovoComplementoProduto(null);
    }

    private void limparContextoNovoPrecoComplemento(SessaoAtendimentoWhatsapp sessao) {

        sessao.setAguardandoNovoPrecoComplemento(false);
        sessao.setIdProdutoNovoPrecoComplemento(null);
        sessao.setIdCategoriaNovoPrecoComplemento(null);
        sessao.setIdGrupoNovoPrecoComplemento(null);
        sessao.setIdComplementoNovoPreco(null);
        sessao.setOffsetListaProdutoNovoPrecoComplemento(0);
    }

    private Integer normalizarOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }
    
    
    private void limparContextoCadastroGuiadoComplementoCategoria(SessaoAtendimentoWhatsapp sessao) {

        sessao.setAguardandoNovoComplementoCategoria(false);
        sessao.setEtapaNovoComplementoCategoria(null);
        sessao.setIdCategoriaNovoComplementoCategoria(null);
        sessao.setIdGrupoNovoComplementoCategoria(null);
        sessao.setOffsetListaCategoriaNovoComplemento(0);
        sessao.setNomeNovoComplementoCategoria(null);
        sessao.setDescricaoNovoComplementoCategoria(null);
        sessao.setPrecoNovoComplementoCategoria(null);
        sessao.setMinimoNovoComplementoCategoria(null);
        sessao.setMaximoNovoComplementoCategoria(null);
    }
}