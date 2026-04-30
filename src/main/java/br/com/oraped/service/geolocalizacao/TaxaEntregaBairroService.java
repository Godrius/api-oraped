package br.com.oraped.service.geolocalizacao;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.geolocalizacao.TaxaEntregaBairro;
import br.com.oraped.repository.geolocalizacao.TaxaEntregaBairroRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaxaEntregaBairroService {

    private final TaxaEntregaBairroRepository repo;
    private final BairroService bairroService;

    @Transactional(readOnly = true)
    public BigDecimal buscarValor(Long idEstabelecimento, Long idBairro) {

        return repo.findByEstabelecimentoIdAndBairroId(idEstabelecimento, idBairro)
            .map(TaxaEntregaBairro::getValor)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public TaxaEntregaBairro buscarConfiguracao(Long idEstabelecimento, Long idBairro) {
        return repo.findByEstabelecimentoIdAndBairroId(idEstabelecimento, idBairro).orElse(null);
    }

    @Transactional
    public BigDecimal salvarValor(
        Estabelecimento estabelecimento,
        Long idBairro,
        BigDecimal valor
    ) {

        if (estabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }

        if (idBairro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
        }

        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valor inválido");
        }

        Bairro bairroRef = bairroService.buscar(idBairro);

        TaxaEntregaBairro te = repo
            .findByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro)
            .orElseGet(() -> {
                TaxaEntregaBairro novo = new TaxaEntregaBairro();
                novo.setEstabelecimento(estabelecimento);
                novo.setBairro(bairroRef);
                return novo;
            });

        // Sempre que houver taxa manual, o bairro deixa de ser isento.
        te.setValor(valor);
        te.setIsento(false);

        return repo.save(te).getValor();
    }

    @Transactional
    public void marcarIsento(
        Estabelecimento estabelecimento,
        Long idBairro
    ) {

        if (estabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estabelecimento é obrigatório");
        }

        if (idBairro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
        }

        Bairro bairroRef = bairroService.buscar(idBairro);

        TaxaEntregaBairro te = repo
            .findByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro)
            .orElseGet(() -> {
                TaxaEntregaBairro novo = new TaxaEntregaBairro();
                novo.setEstabelecimento(estabelecimento);
                novo.setBairro(bairroRef);
                return novo;
            });

        // Isenção explícita mantém registro para diferenciar de ausência de configuração.
        te.setValor(BigDecimal.ZERO.setScale(2));
        te.setIsento(true);

        repo.save(te);
    }

    @Transactional
    public void remover(Long idEstabelecimento, Long idBairro) {
        repo.deleteByEstabelecimentoIdAndBairroId(idEstabelecimento, idBairro);
    }

    public BigDecimal parseValorMonetario(String raw) {

        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String s = raw.trim()
            .replace("R$", "")
            .replace("r$", "")
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".");

        s = s.replaceAll("[^0-9.]", "");

        if (!StringUtils.hasText(s)) {
            return null;
        }

        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}