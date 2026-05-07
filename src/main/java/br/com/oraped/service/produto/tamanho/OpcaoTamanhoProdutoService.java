package br.com.oraped.service.produto.tamanho;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.produto.Produto;
import br.com.oraped.domain.produto.tamanho.OpcaoTamanho;
import br.com.oraped.domain.produto.tamanho.OpcaoTamanhoProduto;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoProdutoRequestDTO;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoProdutoResponseDTO;
import br.com.oraped.repository.produto.tamanho.OpcaoTamanhoProdutoRepository;
import br.com.oraped.service.produto.ProdutoService;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelos preços de tamanhos por produto.
 *
 * Aplicação:
 * - vincula uma opção de tamanho a um produto
 * - define o preço final daquele tamanho naquele produto específico
 *
 * Regra:
 * - OpcaoTamanho define apenas o tamanho: P, M, G, Família
 * - OpcaoTamanhoProduto define o preço desse tamanho para um produto
 * - o preço é final, não adicional
 */
@Service
@RequiredArgsConstructor
public class OpcaoTamanhoProdutoService {

    private final ProdutoService produtoService;
    private final GradeTamanhoService gradeTamanhoService;
    private final OpcaoTamanhoProdutoRepository opcaoTamanhoProdutoRepository;

    @Transactional(readOnly = true)
    public OpcaoTamanhoProduto buscarObrigatoria(Long idOpcaoTamanhoProduto) {

        if (idOpcaoTamanhoProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idOpcaoTamanhoProduto é obrigatório");
        }

        return opcaoTamanhoProdutoRepository.findById(idOpcaoTamanhoProduto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preço de tamanho do produto não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<OpcaoTamanhoProdutoResponseDTO> listarPorProduto(Long idProduto, Boolean somenteAtivas) {

        Produto produto = produtoService.buscarObrigatorio(idProduto);

        boolean ativas = somenteAtivas == null || Boolean.TRUE.equals(somenteAtivas);

        List<OpcaoTamanhoProduto> opcoes = ativas
            ? opcaoTamanhoProdutoRepository.findByProdutoIdAndAtivoTrueOrderByOpcaoTamanhoOrdemAscOpcaoTamanhoNomeAsc(produto.getId())
            : opcaoTamanhoProdutoRepository.findByProdutoIdOrderByOpcaoTamanhoOrdemAscOpcaoTamanhoNomeAsc(produto.getId());

        return opcoes.stream()
            .map(OpcaoTamanhoProdutoResponseDTO::new)
            .toList();
    }

    @Transactional(readOnly = true)
    public OpcaoTamanhoProdutoResponseDTO buscarPorProdutoEOpcao(
        Long idProduto,
        Long idOpcaoTamanho
    ) {

        Produto produto = produtoService.buscarObrigatorio(idProduto);
        OpcaoTamanho opcaoTamanho = gradeTamanhoService.buscarOpcaoObrigatoria(idOpcaoTamanho);

        return opcaoTamanhoProdutoRepository
            .findByProdutoIdAndOpcaoTamanhoId(produto.getId(), opcaoTamanho.getId())
            .map(OpcaoTamanhoProdutoResponseDTO::new)
            .orElse(null);
    }

    @Transactional
    public OpcaoTamanhoProdutoResponseDTO salvarPrecoProdutoTamanho(OpcaoTamanhoProdutoRequestDTO dto) {

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do preço por tamanho são obrigatórios");
        }

        Produto produto = produtoService.buscarObrigatorio(dto.getIdProduto());
        OpcaoTamanho opcaoTamanho = gradeTamanhoService.buscarOpcaoObrigatoria(dto.getIdOpcaoTamanho());

        validarOpcaoPertenceCategoriaDoProduto(produto, opcaoTamanho);

        OpcaoTamanhoProduto relacao = opcaoTamanhoProdutoRepository
            .findByProdutoIdAndOpcaoTamanhoId(produto.getId(), opcaoTamanho.getId())
            .orElseGet(OpcaoTamanhoProduto::new);

        relacao.setProduto(produto);
        relacao.setOpcaoTamanho(opcaoTamanho);
        relacao.setPreco(normalizarPrecoObrigatorio(dto.getPreco()));

        if (dto.getAtivo() != null) {
            relacao.setAtivo(Boolean.TRUE.equals(dto.getAtivo()));
        } else if (relacao.getId() == null) {
            relacao.setAtivo(true);
        }

        return new OpcaoTamanhoProdutoResponseDTO(opcaoTamanhoProdutoRepository.save(relacao));
    }

    

    @Transactional
    public OpcaoTamanhoProdutoResponseDTO atualizarStatus(
        Long idProduto,
        Long idOpcaoTamanho,
        boolean ativo
    ) {

        Produto produto = produtoService.buscarObrigatorio(idProduto);
        OpcaoTamanho opcaoTamanho = gradeTamanhoService.buscarOpcaoObrigatoria(idOpcaoTamanho);

        validarOpcaoPertenceCategoriaDoProduto(produto, opcaoTamanho);

        OpcaoTamanhoProduto relacao = opcaoTamanhoProdutoRepository
            .findByProdutoIdAndOpcaoTamanhoId(produto.getId(), opcaoTamanho.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Preço de tamanho ainda não foi configurado para este produto"
            ));

        relacao.setAtivo(ativo);

        return new OpcaoTamanhoProdutoResponseDTO(opcaoTamanhoProdutoRepository.save(relacao));
    }

    private void validarOpcaoPertenceCategoriaDoProduto(
        Produto produto,
        OpcaoTamanho opcaoTamanho
    ) {

        if (produto.getCategoria() == null || produto.getCategoria().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto sem categoria associada");
        }

        var gradeCategoria = gradeTamanhoService.buscarGradeDaCategoria(produto.getCategoria().getId());

        if (gradeCategoria == null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Categoria do produto não possui grade de tamanhos ativa"
            );
        }

        Long idGradeOpcao = opcaoTamanho.getGrade() == null ? null : opcaoTamanho.getGrade().getId();

        if (!Objects.equals(gradeCategoria.getIdGrade(), idGradeOpcao)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Opção de tamanho não pertence à grade ativa da categoria do produto"
            );
        }
    }

    private BigDecimal normalizarPrecoObrigatorio(BigDecimal valor) {

        if (valor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "preco é obrigatório");
        }

        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "preco não pode ser negativo");
        }

        return valor;
    }
}