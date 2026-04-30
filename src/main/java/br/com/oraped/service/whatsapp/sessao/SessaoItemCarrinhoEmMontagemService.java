package br.com.oraped.service.whatsapp.sessao;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.carrinho.ComplementoItemCarrinhoEmMontagem;
import br.com.oraped.domain.produto.Complemento;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.repository.carrinho.ComplementoItemCarrinhoEmMontagemRepository;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Controlar o item temporário em montagem durante o fluxo de compra no WhatsApp.
 *
 * Aplicação:
 * Guarda produto, categoria, etapa do grupo de complementos e complementos escolhidos
 * antes da definição da quantidade final do item.
 *
 * Utilização:
 * Ao finalizar a escolha de complementos, o conteúdo em montagem será convertido
 * em ItemCarrinho e ComplementoItemCarrinho.
 */
@Service
@RequiredArgsConstructor
public class SessaoItemCarrinhoEmMontagemService {

    private final SessaoWhatsappStore sessaoStore;
    private final ComplementoItemCarrinhoEmMontagemRepository complementoMontagemRepository;

    @Transactional
    public void iniciarMontagem(
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer quantidadeMultipla
    ) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        complementoMontagemRepository.deleteBySessaoId(idSessao);

        sessao.setIdProdutoItemEmMontagem(idProduto);
        sessao.setIdCategoriaItemEmMontagem(idCategoria);
        sessao.setQuantidadeMultiplaItemEmMontagem(
            quantidadeMultipla == null || quantidadeMultipla < 1 ? 1 : quantidadeMultipla
        );
        sessao.setOrdemGrupoComplementoItemEmMontagem(1);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void avancarGrupoComplemento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        Integer ordemAtual = sessao.getOrdemGrupoComplementoItemEmMontagem();
        sessao.setOrdemGrupoComplementoItemEmMontagem(ordemAtual == null || ordemAtual < 1 ? 2 : ordemAtual + 1);

        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void adicionarComplemento(Long idSessao, Complemento complemento) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        ComplementoItemCarrinhoEmMontagem item = complementoMontagemRepository
            .findBySessaoIdAndComplementoId(idSessao, complemento.getId())
            .orElseGet(ComplementoItemCarrinhoEmMontagem::new);

        if (item.getId() == null) {
            item.setSessao(sessao);
            item.setComplemento(complemento);
            item.setNome(StringUtils.hasText(complemento.getNome()) ? complemento.getNome().trim() : "Complemento");
            item.setPrecoUnitario(complemento.getPrecoAdicional() == null ? BigDecimal.ZERO : complemento.getPrecoAdicional());
            item.setQuantidade(0);
        }

        item.setQuantidade((item.getQuantidade() == null ? 0 : item.getQuantidade()) + 1);

        complementoMontagemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<ComplementoItemCarrinhoEmMontagem> listarComplementos(Long idSessao) {
        return complementoMontagemRepository.findBySessaoIdOrderByIdAsc(idSessao);
    }

    @Transactional
    public void limparMontagem(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        complementoMontagemRepository.deleteBySessaoId(idSessao);

        sessao.setIdProdutoItemEmMontagem(null);
        sessao.setIdCategoriaItemEmMontagem(null);
        sessao.setQuantidadeMultiplaItemEmMontagem(null);
        sessao.setOrdemGrupoComplementoItemEmMontagem(null);

        sessaoStore.salvar(sessao);
    }
}