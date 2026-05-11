package br.com.oraped.service.whatsapp.administrador;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.AbrangenciaEntrega;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Concentrar os menus principais da administração via WhatsApp.
 *
 * Aplicação:
 * - menu principal do administrador
 * - hub do cardápio
 * - hub de entregas/logística
 *
 * Utilização:
 * Deve montar apenas menus de navegação de alto nível.
 * As regras de categoria, produto, tamanho, complemento, entrega e relatório ficam nos serviços específicos.
 */
@Service
@RequiredArgsConstructor
public class MenuAdminService {

    private final AdminWhatsappUiHelper sup;

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuAdmin(
        Estabelecimento estabelecimento,
        String whatsappAdmin
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        String statusLoja = estabelecimento.isAberto()
    	    ? "🟢 Loja aberta para pedidos"
    	    : "🔴 Loja fechada para pedidos";

    	String cabecalho =
    	    "🛠️ *Painel Administrativo*\n\n" +
    	    "*" + sup.msg().safe(estabelecimento.getNome()) + "*\n" +
    	    statusLoja + "\n\n" +
    	    "Digite *menu* a qualquer momento para voltar aqui.\n\n" +
    	    "Escolha uma opção:";

    	List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

    	if (estabelecimento.isAberto()) {
    	    itens.add(sup.row(
    	        "COMANDO|ADMIN_FECHAR_LOJA",
    	        "🔴 Fechar loja",
    	        "Parar de receber pedidos"
    	    ));
    	} else {
    	    itens.add(sup.row(
    	        "COMANDO|ADMIN_ABRIR_LOJA",
    	        "🟢 Abrir loja",
    	        "Voltar a receber pedidos"
    	    ));
    	}

    	itens.add(sup.row(
    	    "COMANDO|ADMIN_PEDIDOS_MENU",
    	    "📦 Pedidos",
    	    "Acompanhete todos os pedidos por status"
    	));

    	itens.add(sup.row(
    	    "COMANDO|ADMIN_CARDAPIO_MENU",
    	    "🧾 Cardápio",
    	    "Produtos, complementos, tamanhos e preços"
    	));

    	itens.add(sup.row(
    	    "COMANDO|ADMIN_ENTREGAS_MENU",
    	    "🚚 Logística",
    	    "Áreas atendidas e taxas de entrega"
    	));

    	itens.add(sup.row(
    	    "COMANDO|ADMIN_RELATORIOS_MENU",
    	    "📊 Relatórios",
    	    "Indicadores de desempenho"
    	));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_menu",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(cabecalho, 1024),
                "Ver opções",
                "Admin",
                itens
            )
        );
    }

    

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuEntregas(
        Estabelecimento estabelecimento,
        String whatsappAdmin
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        String cabecalho =
            "🚚 *Taxas de entrega*\n" +
                "*" + sup.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                "Escolha uma opção:";

        boolean temCep = StringUtils.hasText(estabelecimento.getCep());
        boolean temBairroBase = estabelecimento.getBairro() != null && estabelecimento.getBairro().getId() != null;
        boolean abrangenciaBairro = estabelecimento.getAbrangenciaEntrega() == AbrangenciaEntrega.BAIRRO;

        String descCep = temCep
            ? "CEP atual: " + sup.formatarCepParaExibicao(estabelecimento.getCep())
            : "Salvar o CEP da loja";

        String taxaAtual = estabelecimento.getTaxaEntregaPadrao() == null
            ? "não definida"
            : sup.msg().formatarMoeda(estabelecimento.getTaxaEntregaPadrao());

        String descTaxaPadrao = "Atual: " + taxaAtual;

        String descTaxasBairro = temCep && temBairroBase
            ? "Configurar taxas individuais"
            : "Precisa do CEP da loja";

        String descBairrosAtendidos = temCep && temBairroBase && abrangenciaBairro
            ? "Marcar bairros atendidos"
            : "Disponível para abrangência por bairro";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row(
            "COMANDO|ADMIN_ENTREGAS_CEP_MENU",
            "Informar CEP",
            descCep
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_ENTREGAS_TAXA_PADRAO_DIGITAR|0",
            "💰 Taxa padrão",
            descTaxaPadrao
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|0",
            "📍 Taxas por bairro",
            descTaxasBairro
        ));

        if (abrangenciaBairro) {
            itens.add(sup.row(
                "COMANDO|ADMIN_ENTREGAS_BAIRROS_ATENDIDOS",
                "✅ Bairros atendidos",
                descBairrosAtendidos
            ));
        }

        itens.add(sup.row(
            "COMANDO|ADMIN_MENU",
            "⬅️ Voltar",
            "Menu do administrador"
        ));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_entregas_menu",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(cabecalho, 1024),
                "Entregas",
                "Entregas",
                itens
            )
        );
    }
}