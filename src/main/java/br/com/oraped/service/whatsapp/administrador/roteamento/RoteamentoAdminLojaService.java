package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdminLojaService;
import br.com.oraped.service.whatsapp.administrador.MenuAdminService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado ao menu principal e à loja.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar ações de menu,
 * abertura e fechamento do estabelecimento.
 *
 * Utilização:
 * Este service apenas interpreta comandos e delega para MenuAdminService
 * ou AdminLojaService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminLojaService {

    private final AdminLojaService adminLojaService;
    private final MenuAdminService menuAdminService;

    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ADMIN_MENU": {
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    menuAdminService.montarMenuAdmin(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ABRIR_LOJA": {
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminLojaService.abrirLoja(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_FECHAR_LOJA": {
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminLojaService.fecharLoja(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return new RoteamentoResultado(
                    "admin_loja_comando_desconhecido",
                    msg.texto(
                        whatsappAdmin,
                        "⚠️ Não consegui identificar a ação da loja.\n\nTente novamente."
                    )
                );
        }
    }
}