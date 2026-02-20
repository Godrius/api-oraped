// src/main/java/br/com/oraped/service/ProdutoService.java
package br.com.oraped.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.CategoriaProduto;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.MarcaProduto;
import br.com.oraped.domain.Produto;
import br.com.oraped.dto.ProdutoBatchRequestDTO;
import br.com.oraped.dto.ProdutoRequestDTO;
import br.com.oraped.dto.ProdutoResponseDTO;
import br.com.oraped.repository.ProdutoRepository;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    private final EstabelecimentoService estabelecimentoService;
    private final CategoriaProdutoService categoriaProdutoService;
    private final MarcaProdutoService marcaProdutoService;

    public ProdutoService(
        ProdutoRepository produtoRepository,
        @Lazy EstabelecimentoService estabelecimentoService,
        CategoriaProdutoService categoriaProdutoService,
        MarcaProdutoService marcaProdutoService
    ) {
        this.produtoRepository = produtoRepository;
        this.estabelecimentoService = estabelecimentoService;
        this.categoriaProdutoService = categoriaProdutoService;
        this.marcaProdutoService = marcaProdutoService;
    }

    // =========================
    // ENTIDADE
    // =========================

    @Transactional(readOnly = true)
    public Produto buscar(Long idProduto) {
        if (idProduto == null) {
            return null;
        }
        return produtoRepository.findById(idProduto).orElse(null);
    }

    @Transactional(readOnly = true)
    public Produto buscarObrigatorio(Long idProduto) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        return produtoRepository.findById(idProduto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<Produto> listar(Collection<Long> idsProduto) {
        if (idsProduto == null || idsProduto.isEmpty()) {
            return List.of();
        }
        return produtoRepository.findByIdIn(idsProduto);
    }

    // =========================
    // OPERACIONAL (WHATSAPP)
    // =========================

    @Transactional
    public void disponibilizar(Long idProduto) {

        Produto produto = buscarObrigatorio(idProduto);

        if (!produto.isDisponivelParaVenda()) {
            produto.setDisponivelParaVenda(true);
            produtoRepository.save(produto);
        }
    }

    @Transactional
    public void indisponibilizar(Long idProduto) {

        Produto produto = buscarObrigatorio(idProduto);

        if (produto.isDisponivelParaVenda()) {
            produto.setDisponivelParaVenda(false);
            produtoRepository.save(produto);
        }
    }

    // =========================
    // CREATE (BATCH)
    // =========================

    @Transactional
    public List<ProdutoResponseDTO> criarEmLote(ProdutoBatchRequestDTO request) {

        if (request == null || request.getProdutos() == null || request.getProdutos().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de produtos não pode ser vazia");
        }

        Estabelecimento estabelecimento = estabelecimentoService.buscar(request.getIdEstabelecimento());

        List<Produto> paraSalvar = new ArrayList<>();

        for (ProdutoRequestDTO dto : request.getProdutos()) {

            CategoriaProduto categoria = categoriaProdutoService.buscarOuCriar(
                dto.getIdCategoria(),
                dto.getNomeCategoria(),
                estabelecimento
            );

            MarcaProduto marca = marcaProdutoService.buscarOuCriar(
                dto.getIdMarca(),
                dto.getNomeMarca(),
                estabelecimento
            );

            Produto produto = new Produto();
            produto.setEstabelecimento(estabelecimento);
            produto.setCategoria(categoria);
            produto.setMarca(marca);

            String nome = (dto.getNome() == null) ? "" : dto.getNome().trim();
            String descricao = (dto.getDescricao() == null) ? null : dto.getDescricao().trim();

            // Fallback compatível com payload antigo do n8n (que só mandava descricao)
            if (!StringUtils.hasText(nome)) {
                if (StringUtils.hasText(descricao)) {
                    nome = descricao;
                } else {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Produto com nome vazio (e descricao vazia) no lote"
                    );
                }
            }

            if (nome.length() > 120) {
                nome = nome.substring(0, 120);
            }

            produto.setNome(nome);
            produto.setDescricao(descricao);

            if (dto.getPreco() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço é obrigatório no lote");
            }
            if (dto.getPreco().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço não pode ser negativo no lote");
            }
            produto.setPreco(dto.getPreco());

            boolean disponivel = (dto.getDisponivelParaVenda() == null)
                ? true
                : Boolean.TRUE.equals(dto.getDisponivelParaVenda());
            produto.setDisponivelParaVenda(disponivel);

            paraSalvar.add(produto);
        }

        return produtoRepository.saveAll(paraSalvar)
            .stream()
            .map(ProdutoResponseDTO::new)
            .toList();
    }

    // =========================
    // ATUALIZAÇÕES
    // =========================

    @Transactional
    public void atualizarPreco(Long idProduto, BigDecimal novoPreco) {

        if (novoPreco == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoPreco é obrigatório");
        }

        Produto p = buscarObrigatorio(idProduto);

        BigDecimal preco = novoPreco;
        if (preco.compareTo(BigDecimal.ZERO) < 0) {
            preco = BigDecimal.ZERO;
        }

        p.setPreco(preco);
        produtoRepository.save(p);
    }

    @Transactional
    public void atualizarNome(Long idProduto, String novoNome) {

        if (!StringUtils.hasText(novoNome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoNome é obrigatório");
        }

        Produto p = buscarObrigatorio(idProduto);

        String nome = novoNome.trim();
        if (!StringUtils.hasText(nome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoNome é obrigatório");
        }

        if (nome.length() > 120) {
            nome = nome.substring(0, 120);
        }

        p.setNome(nome);
        produtoRepository.save(p);
    }

    @Transactional
    public void atualizarDescricao(Long idProduto, String novaDescricao) {

        if (!StringUtils.hasText(novaDescricao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novaDescricao é obrigatória");
        }

        Produto p = buscarObrigatorio(idProduto);

        String desc = novaDescricao.trim();
        if (!StringUtils.hasText(desc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novaDescricao é obrigatória");
        }

        // coluna é TEXT, mas ainda assim mantemos um limite defensivo
        if (desc.length() > 2000) {
            desc = desc.substring(0, 2000);
        }

        p.setDescricao(desc);
        produtoRepository.save(p);
    }

    @Transactional
    public void excluir(Long idProduto) {

        Produto p = buscarObrigatorio(idProduto);

        // Se existir alguma regra futura (ex.: produto referenciado em pedido),
        // trate aqui antes do delete.

        produtoRepository.delete(p);
    }

    // =========================
    // LISTAGEM (DTO)
    // =========================

    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> listar(
        Long idEstabelecimento,
        Long idCategoria,
        Long idMarca,
        Boolean somenteDisponiveisParaVenda
    ) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        estabelecimentoService.validarExiste(idEstabelecimento);

        boolean somenteDisponiveis = (somenteDisponiveisParaVenda == null)
            ? true
            : Boolean.TRUE.equals(somenteDisponiveisParaVenda);

        if (idCategoria != null) {
            categoriaProdutoService.buscar(idCategoria, idEstabelecimento);
        }
        if (idMarca != null) {
            marcaProdutoService.buscar(idMarca, idEstabelecimento);
        }

        List<Produto> produtos;

        if (idCategoria != null && idMarca != null) {
            produtos = somenteDisponiveis
                ? produtoRepository.findByEstabelecimentoIdAndCategoriaIdAndMarcaIdAndDisponivelParaVendaTrueOrderByDescricaoAsc(
                    idEstabelecimento, idCategoria, idMarca
                )
                : produtoRepository.findByEstabelecimentoIdAndCategoriaIdAndMarcaIdOrderByDescricaoAsc(
                    idEstabelecimento, idCategoria, idMarca
                );

        } else if (idCategoria != null) {
            produtos = somenteDisponiveis
                ? produtoRepository.findByEstabelecimentoIdAndCategoriaIdAndDisponivelParaVendaTrueOrderByDescricaoAsc(
                    idEstabelecimento, idCategoria
                )
                : produtoRepository.findByEstabelecimentoIdAndCategoriaIdOrderByDescricaoAsc(
                    idEstabelecimento, idCategoria
                );

        } else if (idMarca != null) {
            produtos = somenteDisponiveis
                ? produtoRepository.findByEstabelecimentoIdAndMarcaIdAndDisponivelParaVendaTrueOrderByDescricaoAsc(
                    idEstabelecimento, idMarca
                )
                : produtoRepository.findByEstabelecimentoIdAndMarcaIdOrderByDescricaoAsc(
                    idEstabelecimento, idMarca
                );

        } else {
            produtos = somenteDisponiveis
                ? produtoRepository.findByEstabelecimentoIdAndDisponivelParaVendaTrueOrderByDescricaoAsc(idEstabelecimento)
                : produtoRepository.findByEstabelecimentoIdOrderByDescricaoAsc(idEstabelecimento);
        }

        return produtos.stream().map(ProdutoResponseDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> listarIndisponiveis(
        Long idEstabelecimento,
        Long idCategoria,
        Long idMarca
    ) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        estabelecimentoService.validarExiste(idEstabelecimento);

        if (idCategoria != null) {
            categoriaProdutoService.buscar(idCategoria, idEstabelecimento);
        }
        if (idMarca != null) {
            marcaProdutoService.buscar(idMarca, idEstabelecimento);
        }

        List<Produto> produtos;

        if (idCategoria != null && idMarca != null) {
            produtos = produtoRepository.findByEstabelecimentoIdAndCategoriaIdAndMarcaIdAndDisponivelParaVendaFalseOrderByDescricaoAsc(
                idEstabelecimento, idCategoria, idMarca
            );

        } else if (idCategoria != null) {
            produtos = produtoRepository.findByEstabelecimentoIdAndCategoriaIdAndDisponivelParaVendaFalseOrderByDescricaoAsc(
                idEstabelecimento, idCategoria
            );

        } else if (idMarca != null) {
            produtos = produtoRepository.findByEstabelecimentoIdAndMarcaIdAndDisponivelParaVendaFalseOrderByDescricaoAsc(
                idEstabelecimento, idMarca
            );

        } else {
            produtos = produtoRepository.findByEstabelecimentoIdAndDisponivelParaVendaFalseOrderByDescricaoAsc(
                idEstabelecimento
            );
        }

        return produtos.stream().map(ProdutoResponseDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public List<Produto> listarPorEstabelecimento(Long idEstabelecimento) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        estabelecimentoService.validarExiste(idEstabelecimento);

        return produtoRepository.findByEstabelecimentoIdOrderByNomeAsc(idEstabelecimento);
    }
}