package br.com.oraped.service.produto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.domain.produto.MarcaProduto;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.dto.produto.ProdutoBatchRequestDTO;
import br.com.oraped.dto.produto.ProdutoRequestDTO;
import br.com.oraped.dto.produto.ProdutoResponseDTO;
import br.com.oraped.repository.produto.ProdutoRepository;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.produto.tamanho.GradeTamanhoService;
import lombok.RequiredArgsConstructor;


/**
 * Finalidade:
 * Gerenciar o cadastro e as regras operacionais de produtos do cardápio.
 *
 * Aplicação:
 * - cria produtos em lote
 * - controla disponibilidade para venda
 * - atualiza nome, descrição, foto e preço
 * - aplica a regra de preço conforme a categoria possuir ou não grade de tamanhos
 *
 * Utilização:
 * Deve ser usado pelos fluxos administrativos, APIs de produto e montagem do cardápio.
 */
@Service
@RequiredArgsConstructor
public class ProdutoService {


    private final ProdutoRepository produtoRepository;

    private final EstabelecimentoService estabelecimentoService;
    private final CategoriaProdutoService categoriaProdutoService;
    private final MarcaProdutoService marcaProdutoService;
    private final GradeTamanhoService gradeTamanhoProdutoService;

    
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

            String nome = dto.getNome() == null ? "" : dto.getNome().trim();
            String descricao = dto.getDescricao() == null ? null : dto.getDescricao().trim();

            // Mantém compatibilidade com payload antigo do n8n, que enviava apenas descrição.
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

            aplicarPrecoConformeRegraDaCategoria(produto, categoria, dto.getPreco());

            boolean disponivel = dto.getDisponivelParaVenda() == null
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

    @Transactional
    public void atualizarPreco(Long idProduto, BigDecimal novoPreco) {

        if (novoPreco == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoPreco é obrigatório");
        }

        Produto produto = buscarObrigatorio(idProduto);

        if (categoriaPossuiGradeTamanhoAtiva(produto.getCategoria())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Preço do produto não pode ser alterado porque a categoria usa grade de tamanhos"
            );
        }

        BigDecimal preco = novoPreco;
        if (preco.compareTo(BigDecimal.ZERO) < 0) {
            preco = BigDecimal.ZERO;
        }

        produto.setPreco(preco);
        produtoRepository.save(produto);
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

        // Limite defensivo para evitar payloads excessivos mesmo com coluna TEXT.
        if (desc.length() > 2000) {
            desc = desc.substring(0, 2000);
        }

        p.setDescricao(desc);
        produtoRepository.save(p);
    }

    @Transactional
    public void excluir(Long idProduto) {

        Produto produto = buscarObrigatorio(idProduto);

        produto.setExcluido(true);
        produto.setDataExclusao(java.time.OffsetDateTime.now());
        produto.setDisponivelParaVenda(false);

        produtoRepository.save(produto);
    }

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

        boolean somenteDisponiveis = somenteDisponiveisParaVenda == null
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
    public List<Produto> listarPorEstabelecimentoECategoria(Long idEstabelecimento, Long idCategoria) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        estabelecimentoService.validarExiste(idEstabelecimento);
        categoriaProdutoService.buscar(idCategoria, idEstabelecimento);

        return produtoRepository.findByEstabelecimentoIdAndCategoriaIdOrderByDescricaoAsc(
            idEstabelecimento,
            idCategoria
        );
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
                idEstabelecimento,
                idMarca
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

    @Transactional
    public void atualizarUrlFoto(Long idProduto, String novaUrlFoto) {

        Produto p = buscarObrigatorio(idProduto);

        String urlFoto = null;

        if (StringUtils.hasText(novaUrlFoto)) {
            urlFoto = novaUrlFoto.trim();

            if (urlFoto.length() > 500) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novaUrlFoto excede o limite de 500 caracteres");
            }
        }

        p.setUrlFoto(urlFoto);
        produtoRepository.save(p);
    }

    @Transactional(readOnly = true)
    public boolean categoriaPossuiGradeTamanhoAtiva(CategoriaProduto categoria) {

        if (categoria == null || categoria.getId() == null) {
            return false;
        }

        return gradeTamanhoProdutoService.categoriaPossuiGradeAtiva(categoria.getId());
    }

    private void aplicarPrecoConformeRegraDaCategoria(
        Produto produto,
        CategoriaProduto categoria,
        BigDecimal precoInformado
    ) {

        if (categoriaPossuiGradeTamanhoAtiva(categoria)) {
            // Categoria com grade usa preço das opções de tamanho; Produto.preco fica propositalmente nulo.
            produto.setPreco(null);
            return;
        }

        if (precoInformado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço é obrigatório para categoria sem grade de tamanhos");
        }

        if (precoInformado.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço não pode ser negativo");
        }

        produto.setPreco(precoInformado);
    }
}