package br.com.oraped.service.produto.tamanho;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.domain.produto.tamanho.GradeTamanho;
import br.com.oraped.domain.produto.tamanho.OpcaoTamanho;
import br.com.oraped.domain.produto.tamanho.OpcaoTamanhoProduto;
import br.com.oraped.dto.produto.tamanho.GradeTamanhoRequestDTO;
import br.com.oraped.dto.produto.tamanho.GradeTamanhoResponseDTO;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoProdutoRequestDTO;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoProdutoResponseDTO;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoRequestDTO;
import br.com.oraped.dto.produto.tamanho.OpcaoTamanhoResponseDTO;
import br.com.oraped.repository.produto.CategoriaProdutoRepository;
import br.com.oraped.repository.produto.ProdutoRepository;
import br.com.oraped.repository.produto.tamanho.GradeTamanhoRepository;
import br.com.oraped.repository.produto.tamanho.OpcaoTamanhoProdutoRepository;
import br.com.oraped.repository.produto.tamanho.OpcaoTamanhoRepository;
import br.com.oraped.service.EstabelecimentoService;
import lombok.RequiredArgsConstructor;

/**
 * Serviço administrativo para grade única de tamanhos, opções de tamanho e preços por produto.
 *
 * Aplicação:
 * - mantém uma grade reutilizável por estabelecimento
 * - cadastra opções como Pequena, Média, Grande e Família
 * - associa a grade única às categorias que devem vender por tamanho
 * - define o preço final de cada tamanho em cada produto
 *
 * Regra:
 * - a grade é global por estabelecimento
 * - a categoria define quais tamanhos estão disponíveis
 * - o produto define o preço final de cada tamanho
 * - Produto.preco deixa de ser usado quando a categoria possui grade ativa
 */
@Service
@RequiredArgsConstructor
public class GradeTamanhoService {

    private final EstabelecimentoService estabelecimentoService;

    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final ProdutoRepository produtoRepository;

    private final GradeTamanhoRepository gradeTamanhoRepository;
    private final OpcaoTamanhoRepository opcaoTamanhoRepository;
    private final OpcaoTamanhoProdutoRepository opcaoTamanhoProdutoRepository;
    
    @Transactional(readOnly = true)
    public GradeTamanho buscarObrigatoria(Long idGrade) {

        if (idGrade == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idGrade é obrigatório");
        }

        return gradeTamanhoRepository.findById(idGrade)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grade de tamanhos não encontrada"));
    }

    @Transactional(readOnly = true)
    public OpcaoTamanho buscarOpcaoObrigatoria(Long idOpcaoTamanho) {

        if (idOpcaoTamanho == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idOpcaoTamanho é obrigatório");
        }

        return opcaoTamanhoRepository.findById(idOpcaoTamanho)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Opção de tamanho não encontrada"));
    }

    @Transactional(readOnly = true)
    public OpcaoTamanhoProduto buscarOpcaoProdutoObrigatoria(Long idOpcaoTamanhoProduto) {

        if (idOpcaoTamanhoProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idOpcaoTamanhoProduto é obrigatório");
        }

        return opcaoTamanhoProdutoRepository.findById(idOpcaoTamanhoProduto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preço do tamanho no produto não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<GradeTamanhoResponseDTO> listarGrades(Long idEstabelecimento, Boolean somenteAtivas) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        estabelecimentoService.validarExiste(idEstabelecimento);

        boolean ativas = somenteAtivas == null || Boolean.TRUE.equals(somenteAtivas);

        List<GradeTamanho> grades = ativas
            ? gradeTamanhoRepository.findByEstabelecimentoIdAndAtivoTrueAndExcluidoFalseOrderByNomeAsc(idEstabelecimento)
            : gradeTamanhoRepository.findByEstabelecimentoIdAndExcluidoFalseOrderByNomeAsc(idEstabelecimento);

        return grades.stream()
            .map(GradeTamanhoResponseDTO::new)
            .toList();
    }

    @Transactional
    public GradeTamanhoResponseDTO salvarGrade(Long idGrade, GradeTamanhoRequestDTO dto) {

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados da grade são obrigatórios");
        }

        String nome = normalizarTextoObrigatorio(dto.getNome(), "nome", 120);

        GradeTamanho grade = idGrade == null
            ? new GradeTamanho()
            : buscarObrigatoria(idGrade);

        if (idGrade == null) {
            if (dto.getIdEstabelecimento() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
            }

            Estabelecimento estabelecimento = estabelecimentoService.buscar(dto.getIdEstabelecimento());

            grade.setEstabelecimento(estabelecimento);
            grade.setExcluido(false);

            // O escopo nasce junto com a grade para impedir reaproveitamento indevido.
            aplicarEscopoGrade(grade, dto);
        } else {
            if (grade.isExcluido()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Grade de tamanhos excluída não pode ser alterada");
            }

            // Alteração de grade existente preserva o escopo original.
            validarEscopoGrade(grade);
        }

        grade.setNome(nome);
        grade.setDescricao(normalizarTextoOpcional(dto.getDescricao(), 2000));

        if (dto.getAtivo() != null) {
            grade.setAtivo(Boolean.TRUE.equals(dto.getAtivo()));
        }

        return new GradeTamanhoResponseDTO(gradeTamanhoRepository.save(grade));
    }

    @Transactional
    public GradeTamanhoResponseDTO atualizarStatusGrade(Long idGrade, boolean ativo) {

        GradeTamanho grade = buscarObrigatoria(idGrade);

        if (grade.isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Grade de tamanhos excluída não pode ter status alterado");
        }

        // A grade inativa deixa de ser oferecida, mas preserva histórico e associações.
        grade.setAtivo(ativo);

        return new GradeTamanhoResponseDTO(gradeTamanhoRepository.save(grade));
    }

    @Transactional
    public GradeTamanhoResponseDTO excluirGradeLogicamente(Long idGrade) {

        GradeTamanho grade = buscarObrigatoria(idGrade);

        // Exclusão lógica evita quebrar categorias, produtos e pedidos que referenciem tamanhos.
        grade.setAtivo(false);
        grade.setExcluido(true);

        return new GradeTamanhoResponseDTO(gradeTamanhoRepository.save(grade));
    }

    @Transactional(readOnly = true)
    public List<OpcaoTamanhoResponseDTO> listarOpcoes(Long idGrade, Boolean somenteAtivas) {

        GradeTamanho grade = buscarObrigatoria(idGrade);

        boolean ativas = somenteAtivas == null || Boolean.TRUE.equals(somenteAtivas);

        List<OpcaoTamanho> opcoes = ativas
            ? opcaoTamanhoRepository.findByGradeIdAndAtivoTrueOrderByOrdemAscNomeAsc(grade.getId())
            : opcaoTamanhoRepository.findByGradeIdOrderByOrdemAscNomeAsc(grade.getId());

        return opcoes.stream()
            .map(OpcaoTamanhoResponseDTO::new)
            .toList();
    }

    @Transactional
    public OpcaoTamanhoResponseDTO salvarOpcao(Long idOpcaoTamanho, OpcaoTamanhoRequestDTO dto) {

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados da opção de tamanho são obrigatórios");
        }

        String nome = normalizarTextoObrigatorio(dto.getNome(), "nome", 120);

        OpcaoTamanho opcao = idOpcaoTamanho == null
            ? new OpcaoTamanho()
            : buscarOpcaoObrigatoria(idOpcaoTamanho);

        if (idOpcaoTamanho == null) {
            GradeTamanho grade = buscarObrigatoria(dto.getIdGrade());

            if (grade.isExcluido()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível adicionar opção em grade excluída");
            }

            opcao.setGrade(grade);
        } else if (opcao.getGrade() != null && opcao.getGrade().isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Opção de grade excluída não pode ser alterada");
        }

        opcao.setNome(nome);
        opcao.setDescricao(normalizarTextoOpcional(dto.getDescricao(), 2000));
        opcao.setOrdem(normalizarQuantidade(dto.getOrdem(), 1));

        if (dto.getAtivo() != null) {
            opcao.setAtivo(Boolean.TRUE.equals(dto.getAtivo()));
        }

        return new OpcaoTamanhoResponseDTO(opcaoTamanhoRepository.save(opcao));
    }

    @Transactional
    public OpcaoTamanhoResponseDTO atualizarStatusOpcao(Long idOpcaoTamanho, boolean ativo) {

        OpcaoTamanho opcao = buscarOpcaoObrigatoria(idOpcaoTamanho);

        if (opcao.getGrade() != null && opcao.getGrade().isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Opção de grade excluída não pode ter status alterado");
        }

        // Controla se a opção aparece para seleção sem apagar o cadastro.
        opcao.setAtivo(ativo);

        return new OpcaoTamanhoResponseDTO(opcaoTamanhoRepository.save(opcao));
    }

    @Transactional
    public OpcaoTamanhoResponseDTO atualizarDescricaoOpcao(
        Long idOpcaoTamanho,
        String novaDescricao
    ) {

        OpcaoTamanho opcao = buscarOpcaoObrigatoria(idOpcaoTamanho);

        if (opcao.getGrade() != null && opcao.getGrade().isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Opção de grade excluída não pode ser alterada");
        }

        // A descrição pertence ao tamanho da categoria, não ao preço do produto.
        opcao.setDescricao(normalizarTextoOpcional(novaDescricao, 2000));

        return new OpcaoTamanhoResponseDTO(opcaoTamanhoRepository.save(opcao));
    }
    
    

    @Transactional(readOnly = true)
    public boolean categoriaPossuiGradeAtiva(Long idCategoria) {

        if (idCategoria == null) {
            return false;
        }

        return gradeTamanhoRepository
            .findByCategoriaIdAndAtivoTrueAndExcluidoFalse(idCategoria)
            .isPresent();
    }
    

    @Transactional(readOnly = true)
    public List<OpcaoTamanhoProdutoResponseDTO> listarPrecosDoProduto(
        Long idProduto,
        Boolean somenteAtivos
    ) {

        Produto produto = buscarProdutoObrigatorio(idProduto);

        boolean ativos = somenteAtivos == null || Boolean.TRUE.equals(somenteAtivos);

        List<OpcaoTamanhoProduto> precos = ativos
            ? opcaoTamanhoProdutoRepository.findByProdutoIdAndAtivoTrueOrderByOpcaoTamanhoOrdemAscOpcaoTamanhoNomeAsc(produto.getId())
            : opcaoTamanhoProdutoRepository.findByProdutoIdOrderByOpcaoTamanhoOrdemAscOpcaoTamanhoNomeAsc(produto.getId());

        return precos.stream()
            .map(OpcaoTamanhoProdutoResponseDTO::new)
            .toList();
    }

    @Transactional
    public OpcaoTamanhoProdutoResponseDTO salvarPrecoProduto(
        Long idOpcaoTamanhoProduto,
        OpcaoTamanhoProdutoRequestDTO dto
    ) {

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do preço do tamanho são obrigatórios");
        }

        Produto produto = buscarProdutoObrigatorio(dto.getIdProduto());
        OpcaoTamanho opcaoTamanho = buscarOpcaoObrigatoria(dto.getIdOpcaoTamanho());

        validarProdutoPermiteOpcaoTamanho(produto, opcaoTamanho);

        BigDecimal preco = normalizarPrecoObrigatorio(dto.getPreco());

        OpcaoTamanhoProduto item = idOpcaoTamanhoProduto == null
            ? opcaoTamanhoProdutoRepository
                .findByProdutoIdAndOpcaoTamanhoId(produto.getId(), opcaoTamanho.getId())
                .orElseGet(OpcaoTamanhoProduto::new)
            : buscarOpcaoProdutoObrigatoria(idOpcaoTamanhoProduto);

        item.setProduto(produto);
        item.setOpcaoTamanho(opcaoTamanho);
        item.setPreco(preco);

        if (dto.getAtivo() != null) {
            item.setAtivo(Boolean.TRUE.equals(dto.getAtivo()));
        } else if (idOpcaoTamanhoProduto == null) {
            item.setAtivo(true);
        }

        return new OpcaoTamanhoProdutoResponseDTO(opcaoTamanhoProdutoRepository.save(item));
    }

    @Transactional
    public OpcaoTamanhoProdutoResponseDTO atualizarStatusPrecoProduto(
        Long idOpcaoTamanhoProduto,
        boolean ativo
    ) {

        OpcaoTamanhoProduto item = buscarOpcaoProdutoObrigatoria(idOpcaoTamanhoProduto);

        // O vínculo fica inativo para ocultar o tamanho do produto sem perder configuração.
        item.setAtivo(ativo);

        return new OpcaoTamanhoProdutoResponseDTO(opcaoTamanhoProdutoRepository.save(item));
    }

    @Transactional
    public OpcaoTamanhoProdutoResponseDTO atualizarPrecoProduto(
        Long idOpcaoTamanhoProduto,
        BigDecimal novoPreco
    ) {

        OpcaoTamanhoProduto item = buscarOpcaoProdutoObrigatoria(idOpcaoTamanhoProduto);

        // O preço do tamanho no produto é final, não adicional.
        item.setPreco(normalizarPrecoObrigatorio(novoPreco));

        return new OpcaoTamanhoProdutoResponseDTO(opcaoTamanhoProdutoRepository.save(item));
    }

    private Produto buscarProdutoObrigatorio(Long idProduto) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        return produtoRepository.findById(idProduto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
    }

    private CategoriaProduto buscarCategoriaObrigatoria(Long idCategoria) {

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        return categoriaProdutoRepository.findById(idCategoria)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
    }

    private void validarProdutoPermiteOpcaoTamanho(
	    Produto produto,
	    OpcaoTamanho opcaoTamanho
	) {

	    GradeTamanho gradeAplicavel = buscarGradeAplicavelAoProduto(produto);

	    if (gradeAplicavel == null) {
	        throw new ResponseStatusException(
	            HttpStatus.CONFLICT,
	            "Produto não possui grade de tamanhos ativa"
	        );
	    }

	    Long idGradeAplicavel = gradeAplicavel.getId();
	    Long idGradeOpcao = opcaoTamanho.getGrade() == null ? null : opcaoTamanho.getGrade().getId();

	    if (!Objects.equals(idGradeAplicavel, idGradeOpcao)) {
	        throw new ResponseStatusException(
	            HttpStatus.BAD_REQUEST,
	            "Opção de tamanho não pertence à grade ativa do produto"
	        );
	    }
	}
    

    private void validarCategoriaDoEstabelecimento(
        CategoriaProduto categoria,
        Estabelecimento estabelecimento
    ) {

        Long idEstabelecimentoCategoria = categoria.getEstabelecimento() == null
            ? null
            : categoria.getEstabelecimento().getId();

        Long idEstabelecimento = estabelecimento == null ? null : estabelecimento.getId();

        if (!Objects.equals(idEstabelecimentoCategoria, idEstabelecimento)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Categoria não pertence ao estabelecimento"
            );
        }
    }
    
    @Transactional(readOnly = true)
    public GradeTamanho buscarGradeAplicavelAoProduto(Produto produto) {

        if (produto == null || produto.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "produto é obrigatório");
        }

        return gradeTamanhoRepository
            .findByProdutoIdAndAtivoTrueAndExcluidoFalse(produto.getId())
            .orElseGet(() -> {
                if (produto.getCategoria() == null || produto.getCategoria().getId() == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto não possui categoria");
                }

                return gradeTamanhoRepository
                    .findByCategoriaIdAndAtivoTrueAndExcluidoFalse(produto.getCategoria().getId())
                    .orElse(null);
            });
    }

    private String normalizarTextoObrigatorio(String valor, String campo, int limite) {

        if (!StringUtils.hasText(valor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " é obrigatório");
        }

        String texto = valor.trim();

        if (!StringUtils.hasText(texto)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " é obrigatório");
        }

        return texto.length() > limite ? texto.substring(0, limite) : texto;
    }

    private String normalizarTextoOpcional(String valor, int limite) {

        if (!StringUtils.hasText(valor)) {
            return null;
        }

        String texto = valor.trim();

        return texto.length() > limite ? texto.substring(0, limite) : texto;
    }

    private Integer normalizarQuantidade(Integer valor, Integer padrao) {

        Integer quantidade = valor == null ? padrao : valor;

        if (quantidade == null || quantidade < 0) {
            return 0;
        }

        return quantidade;
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
    
    private void aplicarEscopoGrade(GradeTamanho grade, GradeTamanhoRequestDTO dto) {

        boolean possuiCategoria = dto.getIdCategoria() != null;
        boolean possuiProduto = dto.getIdProduto() != null;

        if (possuiCategoria == possuiProduto) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A grade deve estar associada a uma categoria ou a um produto"
            );
        }

        if (possuiCategoria) {
            CategoriaProduto categoria = buscarCategoriaObrigatoria(dto.getIdCategoria());
            validarCategoriaDoEstabelecimento(categoria, grade.getEstabelecimento());

            gradeTamanhoRepository.findByCategoriaIdAndAtivoTrueAndExcluidoFalse(categoria.getId())
                .ifPresent(g -> {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Categoria já possui uma grade de tamanhos ativa"
                    );
                });

            grade.setCategoria(categoria);
            grade.setProduto(null);
            return;
        }

        Produto produto = buscarProdutoObrigatorio(dto.getIdProduto());
        validarProdutoDoEstabelecimento(produto, grade.getEstabelecimento());

        gradeTamanhoRepository.findByProdutoIdAndAtivoTrueAndExcluidoFalse(produto.getId())
            .ifPresent(g -> {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Produto já possui uma grade de tamanhos ativa"
                );
            });

        grade.setProduto(produto);
        grade.setCategoria(null);
    }

    private void validarEscopoGrade(GradeTamanho grade) {

        boolean possuiCategoria = grade.getCategoria() != null;
        boolean possuiProduto = grade.getProduto() != null;

        if (possuiCategoria == possuiProduto) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Grade de tamanho com escopo inválido"
            );
        }
    }

    private void validarProdutoDoEstabelecimento(
        Produto produto,
        Estabelecimento estabelecimento
    ) {

        Long idEstabelecimentoProduto = produto.getEstabelecimento() == null
            ? null
            : produto.getEstabelecimento().getId();

        Long idEstabelecimento = estabelecimento == null ? null : estabelecimento.getId();

        if (!Objects.equals(idEstabelecimentoProduto, idEstabelecimento)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Produto não pertence ao estabelecimento"
            );
        }
    }
    
    
    @Transactional(readOnly = true)
    public GradeTamanho buscarGradeAtivaDaCategoria(Long idCategoria) {

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        return gradeTamanhoRepository
            .findByCategoriaIdAndAtivoTrueAndExcluidoFalse(idCategoria)
            .orElse(null);
    }

    @Transactional
    public GradeTamanho buscarOuCriarGradeDaCategoria(
        Estabelecimento estabelecimento,
        Long idCategoria
    ) {

        if (estabelecimento == null || estabelecimento.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }

        GradeTamanho gradeExistente = buscarGradeAtivaDaCategoria(idCategoria);

        if (gradeExistente != null) {
            return gradeExistente;
        }

        CategoriaProduto categoria = buscarCategoriaObrigatoria(idCategoria);
        validarCategoriaDoEstabelecimento(categoria, estabelecimento);

        GradeTamanhoRequestDTO dto = new GradeTamanhoRequestDTO();
        dto.setIdEstabelecimento(estabelecimento.getId());
        dto.setIdCategoria(categoria.getId());
        dto.setNome("Tamanhos - " + categoria.getNome());
        dto.setDescricao("Tamanhos disponíveis para a categoria " + categoria.getNome());
        dto.setAtivo(true);

        GradeTamanhoResponseDTO response = salvarGrade(null, dto);

        return buscarObrigatoria(response.getIdGrade());
    }
    
    @Transactional(readOnly = true)
    public GradeTamanho buscarGradeAtivaDoProduto(Long idProduto) {

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        return gradeTamanhoRepository
            .findByProdutoIdAndAtivoTrueAndExcluidoFalse(idProduto)
            .orElse(null);
    }
    
    @Transactional
    public OpcaoTamanho criarOpcaoExclusivaProduto(
        Long idEstabelecimento,
        Long idProduto,
        String nomeOpcao
    ) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        Produto produto = buscarProdutoObrigatorio(idProduto);
        Estabelecimento estabelecimento = estabelecimentoService.buscar(idEstabelecimento);

        validarProdutoDoEstabelecimento(produto, estabelecimento);

        String nomeLimpo = normalizarTextoObrigatorio(nomeOpcao, "nome", 120);

        GradeTamanho grade = gradeTamanhoRepository
            .findByProdutoIdAndAtivoTrueAndExcluidoFalse(produto.getId())
            .orElseGet(() -> {
                GradeTamanhoRequestDTO dto = new GradeTamanhoRequestDTO();

                dto.setIdEstabelecimento(estabelecimento.getId());
                dto.setIdProduto(produto.getId());
                dto.setNome("Tamanhos - " + produto.getNome());
                dto.setDescricao("Tamanhos exclusivos do produto " + produto.getNome());
                dto.setAtivo(true);

                GradeTamanhoResponseDTO response = salvarGrade(null, dto);

                return buscarObrigatoria(response.getIdGrade());
            });

        List<OpcaoTamanho> opcoesExistentes =
            opcaoTamanhoRepository.findByGradeIdOrderByOrdemAscNomeAsc(grade.getId());

        boolean nomeJaExiste = opcoesExistentes.stream()
            .anyMatch(opcao -> opcao.getNome() != null && opcao.getNome().equalsIgnoreCase(nomeLimpo));

        if (nomeJaExiste) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Já existe um tamanho com este nome para o produto"
            );
        }

        OpcaoTamanho opcao = new OpcaoTamanho();

        opcao.setGrade(grade);
        opcao.setNome(nomeLimpo);
        opcao.setDescricao(null);
        opcao.setOrdem(opcoesExistentes.size() + 1);
        opcao.setAtivo(true);

        // A opção nasce dentro da grade exclusiva do produto para não afetar os demais produtos da categoria.
        return opcaoTamanhoRepository.save(opcao);
    }
}