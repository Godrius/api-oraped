// src/main/java/br/com/oraped/service/CategoriaProdutoService.java
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

    if (!Objects.equals(categoria.getEstabelecimento().getId(), estabelecimentoId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria não pertence ao estabelecimento informado");
    }

    return categoria;
  }

  /**
   * Regra:
   * - se veio categoriaId => apenas busca e valida pertencimento ao estabelecimento
   * - se não veio categoriaId => busca por (estabelecimento + nome ignore case); se não existir cria
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

    // 1) tenta achar existente (evita duplicar no mesmo estabelecimento)
    return categoriaProdutoRepository.findByEstabelecimentoAndNomeIgnoreCase(estabelecimento, nome)
      .orElseGet(() -> {
        // 2) não existe -> cria
        CategoriaProduto nova = new CategoriaProduto();
        nova.setEstabelecimento(estabelecimento);
        nova.setNome(nome);
        nova.setAtiva(true);

        try {
          return categoriaProdutoRepository.save(nova);
        } catch (DataIntegrityViolationException ex) {
          // 3) proteção para concorrência caso exista UNIQUE no banco
          return categoriaProdutoRepository.findByEstabelecimentoAndNomeIgnoreCase(estabelecimento, nome)
            .orElseThrow(() -> ex);
        }
      });
  }

  private String normalizarNome(String nome) {
    if (nome == null) {
      return "";
    }
    return nome.trim();
  }
}
