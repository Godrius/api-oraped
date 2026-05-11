package br.com.oraped.service.whatsapp.administrador;

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
    private final MenuAdminService menuAdminService;
    
    public AdministradorWhatsappResultados.ResultadoAdmin abrirLoja(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    estabelecimentoService.abrir(estabelecimento.getId());

	    // Atualiza o objeto em memória para o menu refletir o novo estado imediatamente.
	    estabelecimento.setAberto(true);

	    return menuAdminService.montarMenuAdmin(
	        estabelecimento,
	        whatsappAdmin
	    );
	}

    
    public AdministradorWhatsappResultados.ResultadoAdmin fecharLoja(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    estabelecimentoService.fechar(estabelecimento.getId());

	    // Atualiza o objeto em memória para o menu refletir o novo estado imediatamente.
	    estabelecimento.setAberto(false);

	    return menuAdminService.montarMenuAdmin(
	        estabelecimento,
	        whatsappAdmin
	    );
	}
}