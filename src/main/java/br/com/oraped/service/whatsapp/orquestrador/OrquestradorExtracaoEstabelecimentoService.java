package br.com.oraped.service.whatsapp.orquestrador;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.repository.produto.ProdutoRepository;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar a extração de categorias e produtos usados no fluxo conversacional
 * do cliente sem depender de coleções carregadas dentro de Estabelecimento.
 *
 * Aplicação:
 * Utilizado pelos menus do WhatsApp para listar categorias, produtos e dados
 * básicos de produto durante o pedido.
 *
 * Utilização:
 * Deve buscar produtos diretamente no repositório para evitar mutação de entidade
 * gerenciada e problemas com orphanRemoval nas coleções do estabelecimento.
 */
@Service
@RequiredArgsConstructor
public class OrquestradorExtracaoEstabelecimentoService {

    private final ProdutoRepository produtoRepository;

    public List<CategoriaProduto> extrairCategoriasDoEstabelecimento(Estabelecimento e) {

        List<Produto> produtos = listarProdutosDoEstabelecimento(e);

        if (produtos.isEmpty()) {
            return List.of();
        }

        Map<Long, CategoriaProduto> mapa = new LinkedHashMap<>();

        for (Produto p : produtos) {
            if (p == null || !p.isDisponivelParaVenda()) {
                continue;
            }

            CategoriaProduto c = p.getCategoria();

            if (c == null || c.getId() == null || !c.isAtiva()) {
                continue;
            }

            mapa.putIfAbsent(c.getId(), c);
        }

        return new ArrayList<>(mapa.values());
    }

    public List<Produto> extrairProdutosPorCategoria(Estabelecimento e, Long idCategoria) {

        if (idCategoria == null) {
            return List.of();
        }

        return listarProdutosDoEstabelecimento(e).stream()
            .filter(Objects::nonNull)
            .filter(Produto::isDisponivelParaVenda)
            .filter(p -> p.getCategoria() != null)
            .filter(p -> p.getCategoria().isAtiva())
            .filter(p -> Objects.equals(p.getCategoria().getId(), idCategoria))
            .toList();
    }

    public Produto extrairProduto(Estabelecimento e, Long idProduto) {

        if (e == null || e.getId() == null || idProduto == null) {
            return null;
        }

        return produtoRepository.findById(idProduto)
            .filter(p -> p.getEstabelecimento() != null)
            .filter(p -> Objects.equals(p.getEstabelecimento().getId(), e.getId()))
            .orElse(null);
    }

    public String extrairNomeCategoria(Estabelecimento e, Long idCategoria) {

        if (idCategoria == null) {
            return null;
        }

        return listarProdutosDoEstabelecimento(e).stream()
            .filter(Objects::nonNull)
            .map(Produto::getCategoria)
            .filter(Objects::nonNull)
            .filter(c -> Objects.equals(c.getId(), idCategoria))
            .map(CategoriaProduto::getNome)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null);
    }

    private List<Produto> listarProdutosDoEstabelecimento(Estabelecimento e) {

        if (e == null || e.getId() == null) {
            return List.of();
        }

        // Evita carregar/manipular a coleção Estabelecimento.produtos.
        return produtoRepository.findByEstabelecimentoIdOrderByNomeAsc(e.getId());
    }
}