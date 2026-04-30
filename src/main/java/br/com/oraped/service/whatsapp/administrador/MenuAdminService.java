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

@Service
@RequiredArgsConstructor
public class MenuAdminService {

    private final AdminWhatsappUiHelper sup;

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuAdmin(Estabelecimento estabelecimento, String whatsappAdmin) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        String cabecalho =
            "🛠️ *Menu do Administrador*\n" +
                "*" + sup.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                "Escolha uma opção:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        if (estabelecimento.isAberto()) {
            itens.add(sup.row("COMANDO|ADMIN_FECHAR_LOJA", "🔒 Fechar a loja", "Parar de aceitar pedidos"));
        } else {
            itens.add(sup.row("COMANDO|ADMIN_ABRIR_LOJA", "🔓 Abrir loja", "Voltar a aceitar pedidos"));
        }

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|CRIADO|0",
            "📥 Pedidos abertos",
            "Pedidos que ainda não foram aceitos ou recusados"
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|EM_PREPARO|0",
            "👨‍🍳 Pedidos em preparo",
            "Pedidos aceitos e em preparação"
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|PRONTO|0",
            "🛵 Pedidos em entrega",
            "Pedidos em rota de entrega"
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|ENTREGUE|0",
            "✅ Ver pedidos entregues",
            "Entregas confirmadas pelos clientes"
        ));

        itens.add(sup.row("COMANDO|ADMIN_SUSPENDER_PRODUTO_MENU|0", "⛔ Suspender venda", "Tornar produto indisponível para venda"));
        itens.add(sup.row("COMANDO|ADMIN_LIBERAR_PRODUTO_MENU|0", "✅ Liberar venda", "Tornar produto disponível para venda"));

        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_MENU", "🧾 Revisar cardápio", "Produtos e marcas"));
        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_MENU", "🚚 Taxas de entrega", "CEP da loja e taxas por bairro"));

        itens.add(sup.row("COMANDO|ADMIN_RELATORIOS_MENU", "📊 Relatórios", "Indicadores de hoje, ontem, semana e mês"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_menu",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Ver opções", "Admin", itens)
        );
    }

    

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuCardapio(Estabelecimento estabelecimento, String whatsappAdmin) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        String cabecalho =
            "🧾 *Revisar cardápio*\n" +
                "*" + sup.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                "O que você deseja administrar?";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_CATEGORIAS_MENU|0", "Categorias", "Listar / editar / excluir"));
        itens.add(sup.row("COMANDO|ADMIN_CATEGORIA_NOVA_MENU|0", "➕ Nova categoria", "Cadastrar categoria"));
        itens.add(sup.row("COMANDO|ADMIN_PRODUTO_NOVO_CATEGORIA_MENU|0", "➕ Novo produto", "Cadastrar produto"));
        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_MARCAS_MENU|0", "Marcas", "Cadastrar / alterar / excluir"));
        itens.add(sup.row("COMANDO|ADMIN_COMP_GRUPOS_MENU|0", "Grupos de complementos", "Grupos reutilizáveis"));
        itens.add(sup.row("COMANDO|ADMIN_MENU", "⬅️ Voltar", "Menu do administrador"));
        
        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cardapio_menu",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Ver opções", "Cardápio", itens)
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuEntregas(Estabelecimento estabelecimento, String whatsappAdmin) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        String cabecalho =
            "🚚 *Taxas de entrega*\n" +
                "*" + sup.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                "Escolha uma opção:";

        boolean temCep = StringUtils.hasText(estabelecimento.getCep());
        boolean temBairroBase = (estabelecimento.getBairro() != null && estabelecimento.getBairro().getId() != null);
        boolean abrangenciaBairro = estabelecimento.getAbrangenciaEntrega() == AbrangenciaEntrega.BAIRRO;

        String descCep = temCep
            ? ("CEP atual: " + sup.formatarCepParaExibicao(estabelecimento.getCep()))
            : "Salvar o CEP da loja (usado para montar bairros próximos)";

        String taxaAtual = estabelecimento.getTaxaEntregaPadrao() == null
            ? "não definida"
            : sup.msg().formatarMoeda(estabelecimento.getTaxaEntregaPadrao());

        String descTaxaPadrao = "Atual: " + taxaAtual;

        String descTaxasBairro = (temCep && temBairroBase)
            ? "Configurar taxas individuais por bairro"
            : "Precisa do CEP da loja para montar a lista";

        String descBairrosAtendidos = (temCep && temBairroBase && abrangenciaBairro)
            ? "Marcar quais bairros da vizinhança são atendidos"
            : "Disponível apenas para abrangência por bairro com CEP configurado";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_CEP_MENU", "Informar CEP da Loja", descCep));

        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_TAXA_PADRAO_DIGITAR|0", "💰 Taxa padrão", descTaxaPadrao));

        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|0", "📍 Taxas por bairros", descTaxasBairro));

        if (abrangenciaBairro) {
            itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_BAIRROS_ATENDIDOS", "✅ Bairros atendidos", descBairrosAtendidos));
        }

        itens.add(sup.row("COMANDO|ADMIN_MENU", "⬅️ Voltar", "Menu do administrador"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_entregas_menu",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Entregas", "Entregas", itens)
        );
    }
}