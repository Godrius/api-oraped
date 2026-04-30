package br.com.oraped.service.carrinho;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.carrinho.Carrinho;
import br.com.oraped.domain.carrinho.ComplementoItemCarrinho;
import br.com.oraped.domain.carrinho.ComplementoItemCarrinhoEmMontagem;
import br.com.oraped.domain.carrinho.ItemCarrinho;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.repository.carrinho.CarrinhoRepository;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappStore;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Concentrar as operações do carrinho temporário antes da criação do pedido.
 *
 * Aplicação:
 * Usado pelo fluxo do cliente para armazenar produtos e complementos selecionados
 * durante a conversa no WhatsApp.
 *
 * Utilização:
 * O carrinho é persistido por sessão e será convertido em PedidoRequestDTO apenas
 * no momento do envio definitivo do pedido.
 */
@Service
@RequiredArgsConstructor
public class CarrinhoService {

    private final CarrinhoRepository carrinhoRepository;
    private final SessaoWhatsappStore sessaoStore;

    @Transactional
    public ItemCarrinho adicionarItem(
        Long idSessao,
        Produto produto,
        Integer quantidade,
        List<ComplementoItemCarrinhoEmMontagem> complementosEmMontagem
    ) {

        if (produto == null || produto.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "produto é obrigatório");
        }

        int qtd = quantidade == null ? 0 : quantidade;
        if (qtd < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantidade inválida");
        }

        Carrinho carrinho = obterOuCriar(idSessao);

        ItemCarrinho item = new ItemCarrinho();
        item.setCarrinho(carrinho);
        item.setProduto(produto);
        item.setQuantidade(qtd);
        item.setObservacoes(null);

        if (complementosEmMontagem != null) {
            for (ComplementoItemCarrinhoEmMontagem complementoMontagem : complementosEmMontagem) {

                if (complementoMontagem == null || complementoMontagem.getQuantidade() == null || complementoMontagem.getQuantidade() < 1) {
                    continue;
                }

                ComplementoItemCarrinho complementoItem = new ComplementoItemCarrinho();
                complementoItem.setItemCarrinho(item);
                complementoItem.setComplemento(complementoMontagem.getComplemento());
                complementoItem.setNome(complementoMontagem.getNome());
                complementoItem.setQuantidade(complementoMontagem.getQuantidade());
                complementoItem.setPrecoUnitario(
                    complementoMontagem.getPrecoUnitario() == null
                        ? BigDecimal.ZERO
                        : complementoMontagem.getPrecoUnitario()
                );

                // O complemento pertence ao item, não ao carrinho inteiro.
                item.getComplementos().add(complementoItem);
            }
        }

        carrinho.getItens().add(item);

        carrinhoRepository.save(carrinho);

        return item;
    }

    @Transactional(readOnly = true)
    public Carrinho buscarCarrinhoCompleto(Long idSessao) {

        Carrinho carrinho = carrinhoRepository.buscarComItens(idSessao)
            .orElse(null);

        if (carrinho == null) {
            return null;
        }

        // Segunda consulta evita MultipleBagFetchException ao carregar itens e complementos.
        carrinhoRepository.buscarItensComComplementos(idSessao);

        return carrinho;
    }

    @Transactional(readOnly = true)
    public boolean isCarrinhoVazio(Long idSessao) {

        Carrinho carrinho = buscarCarrinhoCompleto(idSessao);
        return carrinho == null || carrinho.getItens() == null || carrinho.getItens().isEmpty();
    }

    @Transactional
    public void limparCarrinho(Long idSessao) {
        carrinhoRepository.deleteBySessaoId(idSessao);
    }

    private Carrinho obterOuCriar(Long idSessao) {

        return carrinhoRepository.findBySessaoId(idSessao)
            .orElseGet(() -> {
                SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

                Carrinho carrinho = new Carrinho();
                carrinho.setSessao(sessao);

                // Cada sessão mantém um único carrinho em andamento.
                return carrinhoRepository.save(carrinho);
            });
    }
}