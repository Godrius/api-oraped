// src/main/java/br/com/oraped/service/EstabelecimentoService.java
package br.com.oraped.service;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.dto.estabelecimento.EstabelecimentoCreateRequestDTO;
import br.com.oraped.repository.EstabelecimentoRepository;
import br.com.oraped.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstabelecimentoService {

    private final EstabelecimentoRepository estabelecimentoRepository;

    // ⚠️ Evita ciclo (EstabelecimentoService -> ProdutoService -> MarcaProdutoService -> EstabelecimentoService)
    // Aqui a gente só precisa listar produtos; então usamos o repositório direto.
    private final ProdutoRepository produtoRepository;

    private final AdministradorEstabelecimentoService administradorEstabelecimentoService;

    @Transactional(readOnly = true)
    public Estabelecimento buscar(Long idEstabelecimento) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        return estabelecimentoRepository.findById(idEstabelecimento)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estabelecimento não encontrado"));
    }

    @Transactional(readOnly = true)
    public void validarExiste(Long idEstabelecimento) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (!estabelecimentoRepository.existsById(idEstabelecimento)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estabelecimento não encontrado");
        }
    }

    @Transactional(readOnly = true)
    public Estabelecimento buscarPorWhatsapp(String whatsapp) {

        if (whatsapp == null || whatsapp.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsapp é obrigatório");
        }

        String w = normalizarWhatsapp(whatsapp);

        Estabelecimento e = estabelecimentoRepository.findByWhatsapp(w)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estabelecimento não encontrado"));

        // carrega por repository/service para evitar MultipleBagFetchException
        e.setProdutos(produtoRepository.findByEstabelecimentoIdOrderByNomeAsc(e.getId()));
        e.setAdministradores(administradorEstabelecimentoService.listarAtivosPorEstabelecimento(e.getId()));

        return e;
    }

    @Transactional
    public void abrir(Long idEstabelecimento) {

        Estabelecimento estabelecimento = buscar(idEstabelecimento);

        if (!estabelecimento.isAberto()) {
            estabelecimento.setAberto(true);
            estabelecimentoRepository.save(estabelecimento);
        }
    }

    @Transactional
    public void fechar(Long idEstabelecimento) {

        Estabelecimento estabelecimento = buscar(idEstabelecimento);

        if (estabelecimento.isAberto()) {
            estabelecimento.setAberto(false);
            estabelecimentoRepository.save(estabelecimento);
        }
    }

    @Transactional
    public Estabelecimento cadastrar(EstabelecimentoCreateRequestDTO req) {

        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload é obrigatório");
        }

        String nome = safeTrim(req.getNome());
        String whatsapp = safeTrim(req.getWhatsapp());

        if (nome.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nome é obrigatório");
        }

        if (whatsapp.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsapp é obrigatório");
        }

        String whatsappNormalizado = normalizarWhatsapp(whatsapp);

        if (estabelecimentoRepository.existsByWhatsapp(whatsappNormalizado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe estabelecimento com este whatsapp");
        }

        Estabelecimento e = new Estabelecimento();
        e.setNome(nome);
        e.setWhatsapp(whatsappNormalizado);
        e.setTimezone(safeTrimOrNull(req.getTimezone()));
        e.setEndereco(safeTrimOrNull(req.getEndereco()));
        e.setConfiguracoesJson(req.getConfiguracoesJson());

        if (req.getAtivo() != null) {
            e.setAtivo(req.getAtivo());
        }
        if (req.getAberto() != null) {
            e.setAberto(req.getAberto());
        }

        return estabelecimentoRepository.save(e);
    }

    
    @Transactional
    public Estabelecimento atualizarCepEBairroBase(Long idEstabelecimento, String cep8, Bairro bairroBase) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        String cepLimpo = (cep8 == null) ? "" : cep8.replaceAll("\\D", "").trim();
        if (!StringUtils.hasText(cepLimpo) || cepLimpo.length() != 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CEP inválido");
        }

        Estabelecimento e = buscar(idEstabelecimento);

        e.setCep(cepLimpo);
        e.setBairro(bairroBase);

        return estabelecimentoRepository.save(e);
    }
    
    
    // =========================
    // Taxa padrão de entrega
    // =========================
    @Transactional
    public Estabelecimento atualizarTaxaEntregaPadrao(Long idEstabelecimento, BigDecimal taxaEntregaPadrao) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (taxaEntregaPadrao != null && taxaEntregaPadrao.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taxaEntregaPadrao inválida");
        }

        Estabelecimento e = buscar(idEstabelecimento);
        e.setTaxaEntregaPadrao(taxaEntregaPadrao);

        return estabelecimentoRepository.save(e);
    }
    
    private String normalizarWhatsapp(String whatsapp) {
        String digits = whatsapp.replaceAll("\\D+", "");
        return digits.trim();
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private String safeTrimOrNull(String s) {
        String v = safeTrim(s);
        return v.isEmpty() ? null : v;
    }
}