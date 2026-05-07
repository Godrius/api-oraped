package br.com.oraped.service.produto.complemento;

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
import br.com.oraped.domain.produto.complemento.Complemento;
import br.com.oraped.domain.produto.complemento.GrupoComplemento;
import br.com.oraped.domain.produto.complemento.GrupoComplementoCategoriaProduto;
import br.com.oraped.domain.produto.complemento.GrupoComplementoProduto;
import br.com.oraped.dto.produto.complemento.ComplementoRequestDTO;
import br.com.oraped.dto.produto.complemento.ComplementoResponseDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoCategoriaProdutoResponseDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoProdutoRequestDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoProdutoResponseDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoRequestDTO;
import br.com.oraped.dto.produto.complemento.GrupoComplementoResponseDTO;
import br.com.oraped.repository.produto.CategoriaProdutoRepository;
import br.com.oraped.repository.produto.complemento.ComplementoRepository;
import br.com.oraped.repository.produto.complemento.GrupoComplementoCategoriaProdutoRepository;
import br.com.oraped.repository.produto.complemento.GrupoComplementoProdutoRepository;
import br.com.oraped.repository.produto.complemento.GrupoComplementoRepository;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.produto.ProdutoService;
import lombok.RequiredArgsConstructor;

/**
 * Serviço administrativo para grupos de complementos, complementos e associações com categorias/produtos.
 *
 * Aplicação:
 * - mantém grupos reutilizáveis por estabelecimento
 * - permite cadastrar opções dentro de cada grupo
 * - associa grupos às categorias para herança automática pelos produtos
 * - associa grupos diretamente aos produtos para exceções específicas
 * - aplica exclusão lógica nos grupos para preservar histórico de pedidos e associações
 */
@Service
@RequiredArgsConstructor
public class GrupoComplementoService {
	
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final GrupoComplementoRepository grupoComplementoRepository;
    private final ComplementoRepository complementoRepository;
    private final GrupoComplementoProdutoRepository grupoComplementoProdutoRepository;
    private final GrupoComplementoCategoriaProdutoRepository grupoComplementoCategoriaProdutoRepository;
    
    
    private final ProdutoService produtoService;
    private final EstabelecimentoService estabelecimentoService;

    @Transactional(readOnly = true)
    public GrupoComplemento buscarObrigatorio(Long idGrupo) {

        if (idGrupo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idGrupo é obrigatório");
        }

        return grupoComplementoRepository.findById(idGrupo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo de complementos não encontrado"));
    }

    @Transactional(readOnly = true)
    public Complemento buscarComplementoObrigatorio(Long idComplemento) {

        if (idComplemento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idComplemento é obrigatório");
        }

        return complementoRepository.findById(idComplemento)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complemento não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<GrupoComplementoResponseDTO> listarGrupos(Long idEstabelecimento, Boolean somenteAtivos) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        estabelecimentoService.validarExiste(idEstabelecimento);

        boolean ativos = somenteAtivos == null || Boolean.TRUE.equals(somenteAtivos);

        List<GrupoComplemento> grupos = ativos
            ? grupoComplementoRepository.findByEstabelecimentoIdAndAtivoTrueAndExcluidoFalseOrderByNomeAsc(idEstabelecimento)
            : grupoComplementoRepository.findByEstabelecimentoIdAndExcluidoFalseOrderByNomeAsc(idEstabelecimento);

        return grupos.stream()
            .map(GrupoComplementoResponseDTO::new)
            .toList();
    }

    @Transactional
    public GrupoComplementoResponseDTO salvarGrupo(Long idGrupo, GrupoComplementoRequestDTO dto) {

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do grupo são obrigatórios");
        }

        String nome = normalizarTextoObrigatorio(dto.getNome(), "nome", 120);
        Integer minimo = normalizarQuantidade(dto.getMinimoSelecoes(), 0);
        Integer maximo = normalizarQuantidade(dto.getMaximoSelecoes(), 1);

        if (maximo < minimo) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maximoSelecoes não pode ser menor que minimoSelecoes");
        }

        GrupoComplemento grupo = idGrupo == null
            ? new GrupoComplemento()
            : buscarObrigatorio(idGrupo);

        if (idGrupo == null) {
            if (dto.getIdEstabelecimento() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
            }

            Estabelecimento estabelecimento = estabelecimentoService.buscar(dto.getIdEstabelecimento());
            grupo.setEstabelecimento(estabelecimento);
            grupo.setExcluido(false);
        } else if (grupo.isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Grupo de complementos excluído não pode ser alterado");
        }

        grupo.setNome(nome);
        grupo.setDescricao(normalizarTextoOpcional(dto.getDescricao(), 2000));
        grupo.setMinimoSelecoes(minimo);
        grupo.setMaximoSelecoes(maximo);

        if (dto.getAtivo() != null) {
            grupo.setAtivo(Boolean.TRUE.equals(dto.getAtivo()));
        }

        return new GrupoComplementoResponseDTO(grupoComplementoRepository.save(grupo));
    }

    @Transactional
    public GrupoComplementoResponseDTO atualizarStatusGrupo(Long idGrupo, boolean ativo) {

        GrupoComplemento grupo = buscarObrigatorio(idGrupo);

        if (grupo.isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Grupo de complementos excluído não pode ter status alterado");
        }

        // Grupo inativo deixa de aparecer para novas associações, preservando histórico e configurações.
        grupo.setAtivo(ativo);

        return new GrupoComplementoResponseDTO(grupoComplementoRepository.save(grupo));
    }

    @Transactional
    public GrupoComplementoResponseDTO excluirGrupoLogicamente(Long idGrupo) {

        GrupoComplemento grupo = buscarObrigatorio(idGrupo);

        // Exclusão lógica evita quebrar pedidos/associações históricas que referenciam o grupo.
        grupo.setAtivo(false);
        grupo.setExcluido(true);

        return new GrupoComplementoResponseDTO(grupoComplementoRepository.save(grupo));
    }

    @Transactional(readOnly = true)
    public List<ComplementoResponseDTO> listarComplementos(Long idGrupo, Boolean somenteAtivos) {

        GrupoComplemento grupo = buscarObrigatorio(idGrupo);

        boolean ativos = somenteAtivos == null || Boolean.TRUE.equals(somenteAtivos);

        List<Complemento> complementos = ativos
            ? complementoRepository.findByGrupoIdAndAtivoTrueOrderByNomeAsc(grupo.getId())
            : complementoRepository.findByGrupoIdOrderByNomeAsc(grupo.getId());

        return complementos.stream()
            .map(ComplementoResponseDTO::new)
            .toList();
    }

    @Transactional
    public ComplementoResponseDTO salvarComplemento(Long idComplemento, ComplementoRequestDTO dto) {

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do complemento são obrigatórios");
        }

        String nome = normalizarTextoObrigatorio(dto.getNome(), "nome", 120);
        BigDecimal precoAdicional = dto.getPrecoAdicional() == null ? BigDecimal.ZERO : dto.getPrecoAdicional();

        if (precoAdicional.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "precoAdicional não pode ser negativo");
        }

        Complemento complemento = idComplemento == null
            ? new Complemento()
            : buscarComplementoObrigatorio(idComplemento);

        if (idComplemento == null) {
            GrupoComplemento grupo = buscarObrigatorio(dto.getIdGrupo());

            if (grupo.isExcluido()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível adicionar complemento em grupo excluído");
            }

            complemento.setGrupo(grupo);
        } else if (complemento.getGrupo() != null && complemento.getGrupo().isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complemento de grupo excluído não pode ser alterado");
        }

        complemento.setNome(nome);
        complemento.setDescricao(normalizarTextoOpcional(dto.getDescricao(), 2000));
        complemento.setPrecoAdicional(precoAdicional);

        if (dto.getAtivo() != null) {
            complemento.setAtivo(Boolean.TRUE.equals(dto.getAtivo()));
        }

        return new ComplementoResponseDTO(complementoRepository.save(complemento));
    }

    @Transactional
    public ComplementoResponseDTO atualizarStatusComplemento(Long idComplemento, boolean ativo) {

        Complemento complemento = buscarComplementoObrigatorio(idComplemento);

        if (complemento.getGrupo() != null && complemento.getGrupo().isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complemento de grupo excluído não pode ter status alterado");
        }

        // Mantém o cadastro histórico e apenas controla se a opção aparece para seleção.
        complemento.setAtivo(ativo);

        return new ComplementoResponseDTO(complementoRepository.save(complemento));
    }

    @Transactional
    public ComplementoResponseDTO atualizarPrecoComplemento(Long idComplemento, BigDecimal novoPreco) {

        if (novoPreco == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoPreco é obrigatório");
        }

        Complemento complemento = buscarComplementoObrigatorio(idComplemento);

        if (complemento.getGrupo() != null && complemento.getGrupo().isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complemento de grupo excluído não pode ter preço alterado");
        }

        BigDecimal preco = novoPreco;
        if (preco.compareTo(BigDecimal.ZERO) < 0) {
            preco = BigDecimal.ZERO;
        }

        // O preço adicional nunca fica negativo, pois ele compõe o total do item no pedido.
        complemento.setPrecoAdicional(preco);

        return new ComplementoResponseDTO(complementoRepository.save(complemento));
    }

    @Transactional(readOnly = true)
    public List<GrupoComplementoProdutoResponseDTO> listarGruposDoProduto(Long idProduto, Boolean somenteAtivos) {

        Produto produto = produtoService.buscarObrigatorio(idProduto);

        boolean ativos = somenteAtivos == null || Boolean.TRUE.equals(somenteAtivos);

        List<GrupoComplementoProduto> associacoes = ativos
            ? grupoComplementoProdutoRepository.findByProdutoIdAndAtivoTrueOrderByOrdemAsc(produto.getId())
            : grupoComplementoProdutoRepository.findByProdutoIdOrderByOrdemAsc(produto.getId());

        return associacoes.stream()
            .filter(associacao -> associacao.getGrupo() == null || !associacao.getGrupo().isExcluido())
            .map(GrupoComplementoProdutoResponseDTO::new)
            .toList();
    }

    @Transactional
    public GrupoComplementoProdutoResponseDTO associarGrupoAoProduto(GrupoComplementoProdutoRequestDTO dto) {

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados da associação são obrigatórios");
        }

        Produto produto = produtoService.buscarObrigatorio(dto.getIdProduto());
        GrupoComplemento grupo = buscarObrigatorio(dto.getIdGrupo());

        if (grupo.isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Grupo de complementos excluído não pode ser associado ao produto");
        }

        validarMesmoEstabelecimento(produto, grupo);

        GrupoComplementoProduto associacao = grupoComplementoProdutoRepository
            .findByProdutoIdAndGrupoId(produto.getId(), grupo.getId())
            .orElseGet(GrupoComplementoProduto::new);

        associacao.setProduto(produto);
        associacao.setGrupo(grupo);
        associacao.setOrdem(normalizarQuantidade(dto.getOrdem(), 1));

        if (dto.getAtivo() != null) {
            associacao.setAtivo(Boolean.TRUE.equals(dto.getAtivo()));
        } else {
            associacao.setAtivo(true);
        }

        return new GrupoComplementoProdutoResponseDTO(grupoComplementoProdutoRepository.save(associacao));
    }

    @Transactional
    public void desassociarGrupoDoProduto(Long idProduto, Long idGrupo) {

        Produto produto = produtoService.buscarObrigatorio(idProduto);
        GrupoComplemento grupo = buscarObrigatorio(idGrupo);

        validarMesmoEstabelecimento(produto, grupo);

        GrupoComplementoProduto associacao = grupoComplementoProdutoRepository
            .findByProdutoIdAndGrupoId(produto.getId(), grupo.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não está associado ao produto"));

        // A associação fica inativa para preservar rastreabilidade e evitar perda acidental de configuração.
        associacao.setAtivo(false);
        grupoComplementoProdutoRepository.save(associacao);
    }

    
    @Transactional(readOnly = true)
    public List<GrupoComplementoCategoriaProdutoResponseDTO> listarGruposDaCategoriaProduto(
        Long idCategoria,
        Boolean somenteAtivos
    ) {

        CategoriaProduto categoria = buscarCategoriaObrigatoria(idCategoria);

        boolean ativos = somenteAtivos == null || Boolean.TRUE.equals(somenteAtivos);

        List<GrupoComplementoCategoriaProduto> associacoes = ativos
            ? grupoComplementoCategoriaProdutoRepository.findByCategoriaIdAndAtivoTrueOrderByOrdemAsc(categoria.getId())
            : grupoComplementoCategoriaProdutoRepository.findByCategoriaIdOrderByOrdemAsc(categoria.getId());

        return associacoes.stream()
            .filter(a -> a.getGrupo() == null || !a.getGrupo().isExcluido())
            .map(this::montarGrupoComplementoCategoriaProdutoResponse)
            .toList();
    }

    @Transactional
    public GrupoComplementoCategoriaProdutoResponseDTO associarGrupoACategoriaProduto(
        Long idCategoria,
        Long idGrupo,
        Integer ordem
    ) {

        CategoriaProduto categoria = buscarCategoriaObrigatoria(idCategoria);
        GrupoComplemento grupo = buscarObrigatorio(idGrupo);

        if (grupo.isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Grupo de complementos excluído não pode ser associado à categoria");
        }

        validarMesmoEstabelecimento(categoria, grupo);

        GrupoComplementoCategoriaProduto associacao = grupoComplementoCategoriaProdutoRepository
            .findByCategoriaIdAndGrupoId(categoria.getId(), grupo.getId())
            .orElseGet(GrupoComplementoCategoriaProduto::new);

        associacao.setCategoria(categoria);
        associacao.setGrupo(grupo);
        associacao.setOrdem(normalizarQuantidade(ordem, 1));
        associacao.setAtivo(true);

        return montarGrupoComplementoCategoriaProdutoResponse(
    	    grupoComplementoCategoriaProdutoRepository.save(associacao)
    	);
    }
    
    
    private GrupoComplementoCategoriaProdutoResponseDTO montarGrupoComplementoCategoriaProdutoResponse(
	    GrupoComplementoCategoriaProduto associacao
	) {

	    Long idGrupo = associacao.getGrupo() == null ? null : associacao.getGrupo().getId();

	    Integer quantidadeComplementos = idGrupo == null
	        ? 0
	        : Math.toIntExact(complementoRepository.countByGrupoId(idGrupo));

	    return new GrupoComplementoCategoriaProdutoResponseDTO(
	        associacao,
	        quantidadeComplementos
	    );
	}

    @Transactional
    public void desassociarGrupoDaCategoriaProduto(Long idCategoria, Long idGrupo) {

        CategoriaProduto categoria = buscarCategoriaObrigatoria(idCategoria);
        GrupoComplemento grupo = buscarObrigatorio(idGrupo);

        validarMesmoEstabelecimento(categoria, grupo);

        GrupoComplementoCategoriaProduto associacao = grupoComplementoCategoriaProdutoRepository
            .findByCategoriaIdAndGrupoId(categoria.getId(), grupo.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não está associado à categoria"));

        // Desassociação lógica para preservar configuração/histórico.
        associacao.setAtivo(false);

        grupoComplementoCategoriaProdutoRepository.save(associacao);
    }

    private CategoriaProduto buscarCategoriaObrigatoria(Long idCategoria) {

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        return categoriaProdutoRepository.findById(idCategoria)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
    }

    private void validarMesmoEstabelecimento(CategoriaProduto categoria, GrupoComplemento grupo) {

        Long idEstabelecimentoCategoria = categoria.getEstabelecimento() == null
            ? null
            : categoria.getEstabelecimento().getId();

        Long idEstabelecimentoGrupo = grupo.getEstabelecimento() == null
            ? null
            : grupo.getEstabelecimento().getId();

        if (!Objects.equals(idEstabelecimentoCategoria, idEstabelecimentoGrupo)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Grupo de complementos não pertence ao mesmo estabelecimento da categoria"
            );
        }
    }
    
    private void validarMesmoEstabelecimento(Produto produto, GrupoComplemento grupo) {

        Long idEstabelecimentoProduto = produto.getEstabelecimento() == null ? null : produto.getEstabelecimento().getId();
        Long idEstabelecimentoGrupo = grupo.getEstabelecimento() == null ? null : grupo.getEstabelecimento().getId();

        if (!Objects.equals(idEstabelecimentoProduto, idEstabelecimentoGrupo)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Grupo de complementos não pertence ao mesmo estabelecimento do produto"
            );
        }
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
}