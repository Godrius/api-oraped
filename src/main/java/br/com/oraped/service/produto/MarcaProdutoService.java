// src/main/java/br/com/oraped/service/MarcaProdutoService.java
package br.com.oraped.service.produto;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.MarcaProduto;
import br.com.oraped.repository.produto.MarcaProdutoRepository;
import br.com.oraped.service.EstabelecimentoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarcaProdutoService {

    private final MarcaProdutoRepository marcaProdutoRepository;
    private final EstabelecimentoService estabelecimentoService;

    // =========================================================
    // BUSCAS (COMPATÍVEL + NOVAS)
    // =========================================================

    @Transactional(readOnly = true)
    public MarcaProduto buscar(Long idMarca, Long idEstabelecimento) {

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        MarcaProduto marca = marcaProdutoRepository.findById(idMarca)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "MarcaProduto não encontrada: " + idMarca
            ));

        if (marca.getEstabelecimento() == null || marca.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Marca sem estabelecimento associado");
        }

        if (!Objects.equals(marca.getEstabelecimento().getId(), idEstabelecimento)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Marca não pertence ao estabelecimento informado");
        }

        return marca;
    }

    @Transactional(readOnly = true)
    public MarcaProduto buscarObrigatorio(Long idMarca) {

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        return marcaProdutoRepository.findById(idMarca)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marca não encontrada"));
    }

    // =========================================================
    // LISTAGENS
    // =========================================================

    /**
     * Menu WhatsApp (cliente):
     * Lista marcas (ativas) que possuem ao menos 1 produto disponível para venda dentro da categoria.
     */
    @Transactional(readOnly = true)
    public List<MarcaProduto> listar(Long idEstabelecimento, Long idCategoria) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (idCategoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        return marcaProdutoRepository
            .findDistinctByProdutosEstabelecimentoIdAndProdutosCategoriaIdAndProdutosDisponivelParaVendaTrueAndAtivaTrueOrderByNomeAsc(
                idEstabelecimento,
                idCategoria
            );
    }

    /**
     * Admin:
     * Lista todas as marcas do estabelecimento (ativas e inativas).
     */
    @Transactional(readOnly = true)
    public List<MarcaProduto> listarPorEstabelecimento(Long idEstabelecimento) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        estabelecimentoService.validarExiste(idEstabelecimento);

        return marcaProdutoRepository.findByEstabelecimentoIdOrderByNomeAsc(idEstabelecimento);
    }

    // =========================================================
    // CREATE / UPDATE / DELETE (ADMIN)
    // =========================================================

    @Transactional
    public MarcaProduto criar(Long idEstabelecimento, String nomeMarca) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (!StringUtils.hasText(nomeMarca)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nomeMarca é obrigatório");
        }

        Estabelecimento e = estabelecimentoService.buscar(idEstabelecimento);

        String nome = nomeMarca.trim();
        if (!StringUtils.hasText(nome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nomeMarca é obrigatório");
        }

        if (nome.length() > 80) {
            nome = nome.substring(0, 80);
        }

        MarcaProduto nova = new MarcaProduto();
        nova.setEstabelecimento(e);
        nova.setNome(nome);
        nova.setAtiva(true);

        return marcaProdutoRepository.save(nova);
    }

    @Transactional
    public MarcaProduto atualizarNome(Long idMarca, Long idEstabelecimento, String novoNome) {

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }
        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }
        if (!StringUtils.hasText(novoNome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoNome é obrigatório");
        }

        MarcaProduto marca = buscar(idMarca, idEstabelecimento);

        String nome = novoNome.trim();
        if (!StringUtils.hasText(nome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoNome é obrigatório");
        }
        if (nome.length() > 80) {
            nome = nome.substring(0, 80);
        }

        marca.setNome(nome);

        return marcaProdutoRepository.save(marca);
    }

    @Transactional
    public void excluir(Long idMarca, Long idEstabelecimento) {

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }
        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        MarcaProduto marca = buscar(idMarca, idEstabelecimento);

        boolean temProdutos = marcaProdutoRepository.existsByProdutosMarcaIdAndProdutosEstabelecimentoId(idMarca, idEstabelecimento);
        if (temProdutos) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Não é possível excluir a marca: existem produtos vinculados"
            );
        }

        marcaProdutoRepository.delete(marca);
    }

    // =========================================================
    // REGRA: buscarOuCriar (BATCH / N8N) - COMPATÍVEL
    // =========================================================

    /**
     * Regra:
     * - se veio idMarca => busca e valida pertencimento ao estabelecimento
     * - se não veio idMarca => cria usando nomeMarca (obrigatório)
     */
    @Transactional
    public MarcaProduto buscarOuCriar(Long idMarca, String nomeMarca, Estabelecimento estabelecimento) {

        if (estabelecimento == null || estabelecimento.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }

        if (idMarca != null) {
            return buscar(idMarca, estabelecimento.getId());
        }

        if (!StringUtils.hasText(nomeMarca)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "nomeMarca é obrigatório quando idMarca não é informado"
            );
        }

        String nome = nomeMarca.trim();
        if (!StringUtils.hasText(nome)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "nomeMarca é obrigatório quando idMarca não é informado"
            );
        }

        if (nome.length() > 80) {
            nome = nome.substring(0, 80);
        }

        MarcaProduto nova = new MarcaProduto();
        nova.setEstabelecimento(estabelecimento);
        nova.setNome(nome);
        nova.setAtiva(true);

        return marcaProdutoRepository.save(nova);
    }
}