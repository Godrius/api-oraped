package br.com.oraped.service.whatsapp.sessao;

import org.springframework.stereotype.Component;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;

/**
 * Componente de limpeza da sessão WhatsApp.
 * Centraliza resets reutilizados pelos fluxos de cliente, marketplace e administração.
 */
@Component
public class SessaoWhatsappCleaner {

    public void limparAguardando(SessaoAtendimentoWhatsapp sessao) {

        sessao.setAguardando(null);

        sessao.setAguardandoQuantidadeManual(false);
        sessao.setIdProdutoQuantidadeManual(null);

        sessao.setAguardandoNovoPreco(false);
        sessao.setIdProdutoNovoPreco(null);
        sessao.setIdCategoriaNovoPreco(null);
        sessao.setOffsetListaNovoPreco(null);

        sessao.setAguardandoNovoNomeProduto(false);
        sessao.setIdProdutoNovoNome(null);
        sessao.setIdCategoriaNovoNome(null);
        sessao.setOffsetListaNovoNome(null);

        sessao.setAguardandoNovaDescricaoProduto(false);
        sessao.setIdProdutoNovaDescricao(null);
        sessao.setIdCategoriaNovaDescricao(null);
        sessao.setOffsetListaNovaDescricao(null);

        sessao.setAguardandoNovaFotoProduto(false);
        sessao.setIdProdutoNovaFoto(null);
        sessao.setIdCategoriaNovaFoto(null);
        sessao.setOffsetListaNovaFoto(null);

        sessao.setAguardandoNovaMarca(false);
        sessao.setOffsetListaMarcasNova(null);

        sessao.setAguardandoEditarMarcaNome(false);
        sessao.setIdMarcaEditarNome(null);
        sessao.setOffsetListaMarcasEditarNome(null);

        sessao.setAguardandoCepEstabelecimento(false);

        sessao.setAguardandoTaxaEntregaBairro(false);
        sessao.setIdBairroTaxaEntrega(null);
        sessao.setOffsetListaTaxaEntregaBairro(null);

        sessao.setAguardandoTaxaEntregaPadrao(false);
        sessao.setOffsetListaTaxaPadraoVoltar(null);

        sessao.setAguardandoBairrosAtendidos(false);

        sessao.setAguardandoNovoPrecoComplemento(false);
        sessao.setIdProdutoNovoPrecoComplemento(null);
        sessao.setIdCategoriaNovoPrecoComplemento(null);
        sessao.setIdGrupoNovoPrecoComplemento(null);
        sessao.setIdComplementoNovoPreco(null);
        sessao.setOffsetListaProdutoNovoPrecoComplemento(0);
    }

    public void limparPedidoEmAndamento(SessaoAtendimentoWhatsapp sessao) {

        limparAguardando(sessao);

        sessao.setEnderecoEntrega(null);
        sessao.setObservacoesEntrega(null);

        sessao.setFormaPagamento(null);
        sessao.setPrecisaTroco(null);
        sessao.setTrocoPara(null);

        sessao.setCepEntrega(null);
        sessao.setBairroEntrega(null);
        sessao.setCidadeEntrega(null);
        sessao.setUfEntrega(null);
        sessao.setLatitudeEntrega(null);
        sessao.setLongitudeEntrega(null);
        sessao.setTaxaEntregaCalculada(null);
        sessao.setEnderecoBaseResolvido(null);

        // =========================================================
        // CLIENTE — Carrinho em montagem
        // =========================================================

        // Remove o estado do item que estava sendo montado antes da escolha de quantidade.
        sessao.setIdProdutoItemEmMontagem(null);
        sessao.setIdCategoriaItemEmMontagem(null);
        sessao.setQuantidadeMultiplaItemEmMontagem(null);
        sessao.setOrdemGrupoComplementoItemEmMontagem(null);
    }

    public void limparContextoMarketplace(SessaoAtendimentoWhatsapp sessao) {

        sessao.setIdMarketplace(null);
        sessao.setIdCategoriaMarketplace(null);
        sessao.setIdSubcategoriaMarketplace(null);
        sessao.setLatitudeOrigemCliente(null);
        sessao.setLongitudeOrigemCliente(null);
    }

    public void limparContextoEstabelecimento(SessaoAtendimentoWhatsapp sessao) {

        sessao.setIdEstabelecimento(null);
    }

    public void limparDiscoveryMarketplace(SessaoAtendimentoWhatsapp sessao) {

        sessao.setIdCategoriaMarketplace(null);
        sessao.setIdSubcategoriaMarketplace(null);
        sessao.setLatitudeOrigemCliente(null);
        sessao.setLongitudeOrigemCliente(null);
    }

    public void limparCategoriaMarketplace(SessaoAtendimentoWhatsapp sessao) {

        sessao.setIdCategoriaMarketplace(null);
        sessao.setIdSubcategoriaMarketplace(null);
    }
}