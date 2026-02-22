// src/main/java/br/com/oraped/service/whatsapp/orquestrador/OrquestradorExtracaoEstabelecimentoService.java
package br.com.oraped.service.whatsapp.orquestrador;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.CategoriaProduto;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.Produto;

@Service
public class OrquestradorExtracaoEstabelecimentoService {

    public List<CategoriaProduto> extrairCategoriasDoEstabelecimento(Estabelecimento e) {

        if (e == null || e.getProdutos() == null) return List.of();

        Map<Long, CategoriaProduto> mapa = new LinkedHashMap<>();

        for (Produto p : e.getProdutos()) {
            if (p == null) continue;

            CategoriaProduto c = p.getCategoria();
            if (c == null || c.getId() == null) continue;

            mapa.putIfAbsent(c.getId(), c);
        }

        return new ArrayList<>(mapa.values());
    }

    public List<Produto> extrairProdutosPorCategoria(Estabelecimento e, Long idCategoria) {

        if (e == null || e.getProdutos() == null) return List.of();

        return e.getProdutos().stream()
            .filter(Objects::nonNull)
            .filter(p -> p.getCategoria() != null && Objects.equals(p.getCategoria().getId(), idCategoria))
            .toList();
    }

    public Produto extrairProduto(Estabelecimento e, Long idProduto) {

        if (e == null || e.getProdutos() == null) return null;

        return e.getProdutos().stream()
            .filter(Objects::nonNull)
            .filter(p -> Objects.equals(p.getId(), idProduto))
            .findFirst()
            .orElse(null);
    }

    public String extrairNomeCategoria(Estabelecimento e, Long idCategoria) {

        if (e == null || e.getProdutos() == null) return null;

        return e.getProdutos().stream()
            .filter(Objects::nonNull)
            .map(Produto::getCategoria)
            .filter(Objects::nonNull)
            .filter(c -> Objects.equals(c.getId(), idCategoria))
            .map(CategoriaProduto::getNome)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null);
    }
}