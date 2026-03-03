package br.com.oraped.service.relatorios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.TipoPeriodoRelatorio;
import br.com.oraped.dto.relatorios.PeriodoRelatorioDTO;
import br.com.oraped.dto.relatorios.RelatorioBairroAtendidoDTO;
import br.com.oraped.dto.relatorios.RelatorioProdutoVendidoDTO;
import br.com.oraped.dto.relatorios.RelatorioResumoDTO;
import br.com.oraped.repository.relatorios.RelatorioEstabelecimentoRepository;

@Service
public class RelatorioEstabelecimentoService {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RelatorioEstabelecimentoRepository repo;

    public RelatorioEstabelecimentoService(RelatorioEstabelecimentoRepository repo) {
        this.repo = repo;
    }

    public RelatorioResumoDTO gerarResumo(Estabelecimento estabelecimento, TipoPeriodoRelatorio tipoPeriodo) {

        Assert.notNull(estabelecimento, "estabelecimento é obrigatório");
        Assert.notNull(estabelecimento.getId(), "estabelecimento.id é obrigatório");
        Assert.notNull(tipoPeriodo, "tipoPeriodo é obrigatório");

        PeriodoRelatorioDTO periodo = resolverPeriodo(tipoPeriodo);

        Long idEstabelecimento = estabelecimento.getId();

        long totalPedidosAtendidos = repo.buscarTotalPedidosAtendidos(idEstabelecimento, periodo.getInicio(), periodo.getFim());
        BigDecimal volumeFinanceiroTotal = repo.buscarVolumeFinanceiroTotal(idEstabelecimento, periodo.getInicio(), periodo.getFim());
        long totalClientesNovos = repo.buscarTotalClientesNovos(idEstabelecimento, periodo.getInicio(), periodo.getFim());
        List<RelatorioProdutoVendidoDTO> top3Produtos = repo.buscarTop3ProdutosMaisVendidos(idEstabelecimento, periodo.getInicio(), periodo.getFim());
        List<RelatorioBairroAtendidoDTO> bairrosAtendidos = repo.buscarBairrosAtendidos(idEstabelecimento, periodo.getInicio(), periodo.getFim());

        RelatorioResumoDTO dto = new RelatorioResumoDTO();
        dto.setTotalPedidosAtendidos(totalPedidosAtendidos);
        dto.setVolumeFinanceiroTotal(volumeFinanceiroTotal);
        dto.setTotalClientesNovos(totalClientesNovos);
        dto.setTop3ProdutosMaisVendidos(top3Produtos);
        dto.setBairrosAtendidos(bairrosAtendidos);
        dto.setTicketMedio(calcularTicketMedio(volumeFinanceiroTotal, totalPedidosAtendidos));

        return dto;
    }

    public String formatarMensagemResumo(TipoPeriodoRelatorio tipoPeriodo, PeriodoRelatorioDTO periodo, RelatorioResumoDTO resumo) {

        Assert.notNull(tipoPeriodo, "tipoPeriodo é obrigatório");
        Assert.notNull(periodo, "periodo é obrigatório");
        Assert.notNull(resumo, "resumo é obrigatório");

        String tituloPeriodo = formatarTituloPeriodo(tipoPeriodo, periodo);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *RELATÓRIO — ").append(tituloPeriodo).append("*\n\n");

        sb.append("📦 *Pedidos atendidos:* ").append(resumo.getTotalPedidosAtendidos()).append("\n");
        sb.append("💰 *Volume total:* ").append(formatarMoeda(resumo.getVolumeFinanceiroTotal())).append("\n");
        sb.append("🎯 *Ticket médio:* ").append(formatarMoeda(resumo.getTicketMedio())).append("\n\n");

        sb.append("👥 *Clientes novos:* ").append(resumo.getTotalClientesNovos()).append("\n\n");

        sb.append("🔥 *Top 3 produtos:*\n");
        List<RelatorioProdutoVendidoDTO> top3 = resumo.getTop3ProdutosMaisVendidos();
        if (top3 == null || top3.isEmpty()) {
            sb.append("- (nenhuma venda no período)\n\n");
        } else {
            int pos = 1;
            for (RelatorioProdutoVendidoDTO p : top3) {
                String nome = p == null ? "" : safe(p.getNomeProduto());
                Long qtd = p == null ? 0L : p.getQuantidadeVendida();
                sb.append(pos).append("️⃣ ").append(nome).append(" — ").append(qtd).append(" un.\n");
                pos++;
            }
            sb.append("\n");
        }

        sb.append("📍 *Bairros atendidos:*\n");
        List<RelatorioBairroAtendidoDTO> bairros = resumo.getBairrosAtendidos();
        if (bairros == null || bairros.isEmpty()) {
            sb.append("- (nenhum bairro no período)\n");
        } else {
            for (RelatorioBairroAtendidoDTO b : bairros) {
                String nomeBairro = b == null ? "" : safe(b.getBairro());
                Long qtdPedidos = b == null ? 0L : b.getTotalPedidos();
                sb.append("- ").append(nomeBairro).append(" — ").append(qtdPedidos).append(" pedido(s)\n");
            }
        }

        return sb.toString();
    }

    public PeriodoRelatorioDTO resolverPeriodo(TipoPeriodoRelatorio tipo) {

        Assert.notNull(tipo, "tipo é obrigatório");

        LocalDate hoje = LocalDate.now();

        switch (tipo) {

            case HOJE: {
                return new PeriodoRelatorioDTO(
                    hoje.atStartOfDay(),
                    hoje.atTime(LocalTime.MAX)
                );
            }

            case ONTEM: {
                LocalDate ontem = hoje.minusDays(1);
                return new PeriodoRelatorioDTO(
                    ontem.atStartOfDay(),
                    ontem.atTime(LocalTime.MAX)
                );
            }

            case SEMANA_ATUAL: {
                LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
                LocalDate fimSemana = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
                return new PeriodoRelatorioDTO(
                    inicioSemana.atStartOfDay(),
                    fimSemana.atTime(LocalTime.MAX)
                );
            }

            case MES_ATUAL: {
                LocalDate inicioMes = hoje.with(TemporalAdjusters.firstDayOfMonth());
                LocalDate fimMes = hoje.with(TemporalAdjusters.lastDayOfMonth());
                return new PeriodoRelatorioDTO(
                    inicioMes.atStartOfDay(),
                    fimMes.atTime(LocalTime.MAX)
                );
            }

            default:
                throw new IllegalArgumentException("Tipo de período não suportado: " + tipo);
        }
    }

    public String gerarMensagemResumo(Estabelecimento estabelecimento, TipoPeriodoRelatorio tipoPeriodo) {

        Assert.notNull(estabelecimento, "estabelecimento é obrigatório");
        Assert.notNull(tipoPeriodo, "tipoPeriodo é obrigatório");

        PeriodoRelatorioDTO periodo = resolverPeriodo(tipoPeriodo);
        RelatorioResumoDTO resumo = gerarResumo(estabelecimento, tipoPeriodo);

        return formatarMensagemResumo(tipoPeriodo, periodo, resumo);
    }

    private BigDecimal calcularTicketMedio(BigDecimal volumeTotal, long totalPedidos) {

        BigDecimal total = volumeTotal == null ? BigDecimal.ZERO : volumeTotal;

        if (totalPedidos <= 0L) {
            return BigDecimal.ZERO;
        }

        return total.divide(BigDecimal.valueOf(totalPedidos), 2, RoundingMode.HALF_UP);
    }

    private String formatarTituloPeriodo(TipoPeriodoRelatorio tipoPeriodo, PeriodoRelatorioDTO periodo) {

        LocalDateTime ini = periodo.getInicio();
        LocalDateTime fim = periodo.getFim();

        switch (tipoPeriodo) {

            case HOJE:
                return "Hoje (" + ini.toLocalDate().format(FMT_DATA) + ")";

            case ONTEM:
                return "Ontem (" + ini.toLocalDate().format(FMT_DATA) + ")";

            case SEMANA_ATUAL:
                return "Semana (" + ini.toLocalDate().format(FMT_DATA) + " a " + fim.toLocalDate().format(FMT_DATA) + ")";

            case MES_ATUAL:
                return "Mês (" + ini.toLocalDate().format(FMT_DATA) + " a " + fim.toLocalDate().format(FMT_DATA) + ")";

            default:
                return ini.toLocalDate().format(FMT_DATA) + " a " + fim.toLocalDate().format(FMT_DATA);
        }
    }

    private String safe(String s) {
        if (!StringUtils.hasText(s)) {
            return "(sem nome)";
        }
        return s.trim();
    }

    private String formatarMoeda(BigDecimal valor) {

        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        return nf.format(valor == null ? BigDecimal.ZERO : valor);
    }
}