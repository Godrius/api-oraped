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

        String cabecalho =
            "🛠️ *Menu do Administrador*\n" +
                "*" + sup.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                "Escolha o que deseja fazer:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        if (estabelecimento.isAberto()) {
            itens.add(sup.row(
                "COMANDO|ADMIN_FECHAR_LOJA",
                "🔒 Fechar loja",
                "Parar de aceitar pedidos"
            ));
        } else {
            itens.add(sup.row(
                "COMANDO|ADMIN_ABRIR_LOJA",
                "🔓 Abrir loja",
                "Voltar a aceitar pedidos"
            ));
        }

        itens.add(sup.row(
            "COMANDO|ADMIN_CARDAPIO_MENU",
            "🧾 Cardápio",
            "Categorias, produtos, tamanhos e complementos"
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|CRIADO|0",
            "📥 Pedidos abertos",
            "Aceitar ou recusar pedidos"
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|EM_PREPARO|0",
            "👨‍🍳 Em preparo",
            "Pedidos aceitos e em preparação"
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|PRONTO|0",
            "🛵 Em entrega",
            "Pedidos em rota de entrega"
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|ENTREGUE|0",
            "✅ Entregues",
            "Pedidos já finalizados"
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_ENTREGAS_MENU",
            "🚚 Logística",
            "Bairros atendidos e taxas de entrega"
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_RELATORIOS_MENU",
            "📊 Relatórios",
            "Indicadores de vendas"
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

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuCardapio(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    String cabecalho =
	        "🧾 *Cardápio*\n\n" +
	            "Gerencie aqui tudo que o cliente vê ao fazer um pedido.\n\n" +
	            "Escolha uma opção:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(sup.row(
	        "COMANDO|ADMIN_CARDAPIO_CATEGORIAS_MENU|0",
	        "📂 Categorias",
	        "Ver categorias e produtos"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_PRODUTO_NOVO_CATEGORIA_MENU|0",
	        "➕ Novo produto",
	        "Cadastrar produto em uma categoria"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_COMP_GRUPOS_MENU|0",
	        "🧩 Complementos",
	        "Criar grupos de complementos"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_MENU",
	        "⬅️ Voltar",
	        "Menu do administrador"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_cardapio_menu",
	        sup.msg().lista(
	            whatsappAdmin,
	            sup.msg().truncWord(cabecalho, 1024),
	            "Ver opções",
	            "Cardápio",
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