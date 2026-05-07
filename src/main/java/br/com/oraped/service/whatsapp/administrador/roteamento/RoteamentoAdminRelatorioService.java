package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.TipoPeriodoRelatorio;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.AdminRelatorioService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado aos relatórios.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar ações de geração
 * de relatórios operacionais do estabelecimento.
 *
 * Utilização:
 * Este service apenas interpreta comandos e delega a geração efetiva
 * para AdminRelatorioService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminRelatorioService {

    private final AdminRelatorioService adminRelatorioService;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ADMIN_RELATORIOS_MENU": {
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminRelatorioService.montarMenuRelatorios(
                        estabelecimento,
                        whatsappAdmin
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_RELATORIOS_HOJE":
            case "ADMIN_RELATORIOS_ONTEM":
            case "ADMIN_RELATORIOS_SEMANA":
            case "ADMIN_RELATORIOS_MES": {

                TipoPeriodoRelatorio periodo;

                switch (acao) {

                    case "ADMIN_RELATORIOS_HOJE":
                        periodo = TipoPeriodoRelatorio.HOJE;
                        break;

                    case "ADMIN_RELATORIOS_ONTEM":
                        periodo = TipoPeriodoRelatorio.ONTEM;
                        break;

                    case "ADMIN_RELATORIOS_SEMANA":
                        periodo = TipoPeriodoRelatorio.SEMANA_ATUAL;
                        break;

                    case "ADMIN_RELATORIOS_MES":
                        periodo = TipoPeriodoRelatorio.MES_ATUAL;
                        break;

                    default:
                        throw new IllegalArgumentException("Período inválido");
                }

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminRelatorioService.gerarRelatorio(
                        estabelecimento,
                        whatsappAdmin,
                        periodo
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return null;
        }
    }
}