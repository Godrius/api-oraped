package br.com.oraped.service.whatsapp.orquestrador;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.Produto;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.service.whatsapp.MensagemAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorCarrinhoService {

    private final MensagemAtendimentoWhatsappService mensagemService;
    private final OrquestradorParseService parseService;

    private final OrquestradorExtracaoEstabelecimentoService extracaoService;
    private final WhatsappMensagemFactory msg;

    public Map<Long, Integer> montarCarrinhoAtual(Long idSessao) {

        Map<Long, Integer> qtdPorProduto = new LinkedHashMap<>();

        for (var m : mensagemService.listarEntradas(idSessao)) {

            String txt = (m == null) ? null : m.getConteudoTexto();
            if (!StringUtils.hasText(txt)) continue;

            if (txt.startsWith("COMANDO|LIMPAR_CARRINHO")) {
                qtdPorProduto.clear();
                continue;
            }

            if (!txt.startsWith("COMANDO|ADICIONAR_PRODUTO|")) continue;

            ComandoWhatsapp c = ComandoWhatsapp.parse(txt);

            Long idProduto = parseService.parseLongObrigatorio(c.getParte(2), "idProduto");
            Integer quantidade = parseService.parseIntObrigatorio(c.getParte(3), "quantidade");

            if (quantidade == null || quantidade < 1) continue;

            qtdPorProduto.merge(idProduto, quantidade, Integer::sum);
        }

        return qtdPorProduto;
    }

    public String montarResumoItensDoCarrinho(
        Estabelecimento estabelecimento,
        Map<Long, Integer> carrinho
    ) {

        if (carrinho == null || carrinho.isEmpty()) return "(sem itens)";

        StringBuilder sb = new StringBuilder();

        for (var e : carrinho.entrySet()) {

            Long idProduto = e.getKey();
            int qtd = e.getValue() == null ? 0 : e.getValue();

            Produto p = extracaoService.extrairProduto(estabelecimento, idProduto);

            String nome = (p == null ? ("Produto #" + idProduto) : msg.safe(p.getNome()));

            BigDecimal precoUnit = (p == null || p.getPreco() == null) ? BigDecimal.ZERO : p.getPreco();
            BigDecimal subtotal = precoUnit.multiply(BigDecimal.valueOf(qtd));

            sb.append("- ")
                .append(nome)
                .append(" x").append(qtd)
                .append(" = ").append(msg.formatarMoeda(subtotal))
                .append("\n");
        }

        return sb.toString().trim();
    }
}