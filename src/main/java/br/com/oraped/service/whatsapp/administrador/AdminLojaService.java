package br.com.oraped.service.whatsapp.administrador;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import lombok.RequiredArgsConstructor;

/**
 * Responsável pelas ações operacionais da loja no fluxo administrativo via WhatsApp.
 *
 * Aplicação:
 * - abrir loja
 * - fechar loja
 *
 * Utilização:
 * Deve ser chamado pelo roteamento administrativo quando o administrador executar
 * comandos operacionais da loja.
 */
@Service
@RequiredArgsConstructor
public class AdminLojaService {

    private final EstabelecimentoService estabelecimentoService;
    private final AdminWhatsappUiHelper sup;

    
    public AdministradorWhatsappResultados.ResultadoAdmin abrirLoja(
        Estabelecimento estabelecimento,
        String whatsappAdmin
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        estabelecimentoService.abrir(estabelecimento.getId());

        String corpo =
            "✅ Loja *aberta*.\n\n" +
                "O estabelecimento agora está aceitando pedidos.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_loja_aberta",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
            )
        );
    }

    
    public AdministradorWhatsappResultados.ResultadoAdmin fecharLoja(
        Estabelecimento estabelecimento,
        String whatsappAdmin
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        estabelecimentoService.fechar(estabelecimento.getId());

        String corpo =
            "✅ Loja *fechada*.\n\n" +
                "O estabelecimento não aceitará novos pedidos.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_loja_fechada",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
            )
        );
    }
}