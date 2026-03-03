package br.com.oraped.service.whatsapp.administrador;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.TipoPeriodoRelatorio;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.relatorios.RelatorioEstabelecimentoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdministradorWhatsappRelatorioService {

    private final AdministradorWhatsappSupport sup;
    private final RelatorioEstabelecimentoService relatorioService;

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuRelatorios(Estabelecimento estabelecimento, String whatsappAdmin) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        String cabecalho =
            "📊 *Relatórios*\n" +
                "*" + sup.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                "Selecione o período:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row("COMANDO|ADMIN_RELATORIOS_HOJE", "Dados de hoje", "Pedidos entregues e indicadores do dia"));
        itens.add(sup.row("COMANDO|ADMIN_RELATORIOS_ONTEM", "Dados de ontem", "Pedidos entregues e indicadores de ontem"));
        itens.add(sup.row("COMANDO|ADMIN_RELATORIOS_SEMANA", "Dados da semana", "De segunda a domingo"));
        itens.add(sup.row("COMANDO|ADMIN_RELATORIOS_MES", "Dados do mês", "Do primeiro ao último dia"));

        itens.add(sup.row("COMANDO|ADMIN_MENU", "⬅️ Voltar", "Menu do administrador"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_relatorios_menu",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(cabecalho, 1024),
                "Ver opções",
                "Relatórios",
                itens
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin gerarRelatorio(Estabelecimento estabelecimento, String whatsappAdmin, TipoPeriodoRelatorio periodo) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        String texto = relatorioService.gerarMensagemResumo(estabelecimento, periodo);

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_relatorio_" + periodo.name().toLowerCase(),
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(texto, 4096),
                List.of(
                    sup.btn("COMANDO|ADMIN_RELATORIOS_MENU", "📊 Relatórios"),
                    sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }
}