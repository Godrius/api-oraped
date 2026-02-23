package br.com.oraped.service.geolocalizacao;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.repository.geolocalizacao.BairroRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaxaEntregaClienteService {

    private final BairroRepository bairroRepository;
    private final TaxaEntregaBairroService taxaEntregaBairroService;

    public BigDecimal calcularTaxaEntrega(Estabelecimento estabelecimento, EnderecoResolvidoDTO end) {

        if (estabelecimento == null || estabelecimento.getId() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal taxaPadrao = nvl(estabelecimento.getTaxaEntregaPadrao());

        if (end == null) {
            return taxaPadrao;
        }

        String bairro = safe(end.getBairro());
        String cidade = safe(end.getCidade());
        String uf = safe(end.getUf());

        if (!StringUtils.hasText(bairro) || !StringUtils.hasText(cidade) || !StringUtils.hasText(uf)) {
            return taxaPadrao;
        }

        String nomeNormalizado = normalizar(bairro);

        Bairro b = bairroRepository
            .findByNomeNormalizadoIgnoreCaseAndCidadeIgnoreCaseAndUfIgnoreCase(nomeNormalizado, cidade, uf)
            .orElse(null);

        if (b == null || b.getId() == null) {
            return taxaPadrao;
        }

        BigDecimal taxaBairro = taxaEntregaBairroService.buscarValor(estabelecimento.getId(), b.getId());

        return taxaBairro != null ? taxaBairro : taxaPadrao;
    }

    public EnderecoResolvidoDTO montarEnderecoParaFallback(
        Estabelecimento estabelecimento,
        String bairroDigitado
    ) {

        EnderecoResolvidoDTO end = new EnderecoResolvidoDTO();

        if (estabelecimento != null && estabelecimento.getBairro() != null) {
            end.setCidade(estabelecimento.getBairro().getCidade());
            end.setUf(estabelecimento.getBairro().getUf());
        }

        end.setBairro(safe(bairroDigitado));
        return end;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String safe(String s) {
        if (!StringUtils.hasText(s)) return null;
        return s.trim();
    }

    private String normalizar(String s) {
        if (!StringUtils.hasText(s)) return "";
        String v = s.trim().toLowerCase(Locale.ROOT);
        v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        v = v.replaceAll("[^a-z0-9\\s]", " ");
        v = v.replaceAll("\\s{2,}", " ").trim();
        return v;
    }
}