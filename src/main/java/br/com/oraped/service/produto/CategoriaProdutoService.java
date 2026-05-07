package br.com.oraped.service.produto;

import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.repository.produto.CategoriaProdutoRepository;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Gerenciar categorias de produtos do cardápio por estabelecimento.
 *
 * Aplicação:
 * - listar categorias ativas
 * - buscar categoria validando pertencimento ao estabelecimento
 * - buscar ou criar categoria durante importações/cadastros em lote
 * - criar categoria por digitação no fluxo administrativo do WhatsApp
 *
 * Utilização:
 * Deve ser usado pelos serviços de produto, cardápio e fluxos administrativos.
 */
@Service
@RequiredArgsConstructor
public class CategoriaProdutoService {

    private final CategoriaProdutoRepository categoriaProdutoRepository;

    @Transactional(readOnly = true)
    public List<CategoriaProduto> listar(Long estabelecimentoId) {

        if (estabelecimentoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimentoId é obrigatório");
        }

        return categoriaProdutoRepository.findByEstabelecimentoIdAndAtivaTrueOrderByNomeAsc(estabelecimentoId);
    }

    @Transactional(readOnly = true)
    public CategoriaProduto buscar(Long id, Long estabelecimentoId) {

        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoriaId é obrigatório");
        }

        if (estabelecimentoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimentoId é obrigatório");
        }

        CategoriaProduto categoria = categoriaProdutoRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CategoriaProduto não encontrada: " + id));

        if (categoria.getEstabelecimento() == null
            || !Objects.equals(categoria.getEstabelecimento().getId(), estabelecimentoId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria não pertence ao estabelecimento informado");
        }

        return categoria;
    }

    @Transactional(readOnly = true)
    public boolean existePorNome(Estabelecimento estabelecimento, String nome) {

        if (estabelecimento == null || estabelecimento.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }

        return categoriaProdutoRepository.existsByEstabelecimentoAndNomeIgnoreCase(
            estabelecimento,
            normalizarNome(nome)
        );
    }

    /**
     * Regra:
     * - se veio categoriaId, apenas busca e valida pertencimento ao estabelecimento
     * - se não veio categoriaId, busca por estabelecimento + nome; se não existir, cria
     */
    @Transactional
    public CategoriaProduto buscarOuCriar(Long categoriaId, String categoriaNome, Estabelecimento estabelecimento) {

        if (estabelecimento == null || estabelecimento.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }

        if (categoriaId != null) {
            return buscar(categoriaId, estabelecimento.getId());
        }

        String nome = normalizarNome(categoriaNome);

        if (nome.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "categoriaNome é obrigatório quando categoriaId não é informado"
            );
        }

        return categoriaProdutoRepository.findByEstabelecimentoAndNomeIgnoreCase(estabelecimento, nome)
            .orElseGet(() -> criarCategoriaComProtecaoConcorrencia(estabelecimento, nome));
    }

    @Transactional
    public CategoriaProduto criarPorNomeDigitado(Estabelecimento estabelecimento, String nomeCategoria) {

        if (estabelecimento == null || estabelecimento.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }

        String nome = normalizarNome(nomeCategoria);

        if (nome.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nomeCategoria é obrigatório");
        }

        if (categoriaProdutoRepository.existsByEstabelecimentoAndNomeIgnoreCase(estabelecimento, nome)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma categoria com esse nome");
        }

        return criarCategoriaComProtecaoConcorrencia(estabelecimento, nome);
    }

    private CategoriaProduto criarCategoriaComProtecaoConcorrencia(
        Estabelecimento estabelecimento,
        String nome
    ) {

        CategoriaProduto categoria = new CategoriaProduto();
        categoria.setEstabelecimento(estabelecimento);
        categoria.setNome(nome);
        categoria.setAtiva(true);

        // A categoria nova entra sem ordem manual para seguir a ordenação padrão atual.
        categoria.setOrdem(null);

        try {
            return categoriaProdutoRepository.save(categoria);
        } catch (DataIntegrityViolationException ex) {
            // Protege contra duplicidade em cadastros concorrentes quando houver UNIQUE no banco.
            return categoriaProdutoRepository.findByEstabelecimentoAndNomeIgnoreCase(estabelecimento, nome)
                .orElseThrow(() -> ex);
        }
    }

    private String normalizarNome(String nome) {

        if (nome == null) {
            return "";
        }

        return nome.trim();
    }
}