package br.com.oraped.service;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.produto.Produto;
import br.com.oraped.service.produto.ProdutoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrecoService {

    private final ProdutoService produtoService;

    public BigDecimal parseValorMonetario(String raw) {

        if (!StringUtils.hasText(raw)) return null;

        String s = raw.trim()
            .replace("R$", "")
            .replace("r$", "")
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".");

        s = s.replaceAll("[^0-9.]", "");

        if (!StringUtils.hasText(s)) return null;

        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    public BigDecimal normalizarNaoNegativo(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO;
        return v.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : v;
    }

    public Produto buscarProdutoOuFalhar(Long idProduto) {
        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }
        Produto p = produtoService.buscar(idProduto);
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }
        return p;
    }

    public void atualizarPreco(Long idProduto, BigDecimal novoPreco) {
        produtoService.atualizarPreco(idProduto, normalizarNaoNegativo(novoPreco));
    }
}