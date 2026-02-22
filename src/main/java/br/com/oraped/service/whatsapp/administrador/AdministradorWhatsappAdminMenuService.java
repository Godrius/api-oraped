package br.com.oraped.service.whatsapp.administrador;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.EstabelecimentoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdministradorWhatsappAdminMenuService {

    private final EstabelecimentoService estabelecimentoService;
    private final AdministradorWhatsappSupport sup;

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuAdmin(Estabelecimento estabelecimento, String whatsappAdmin) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        String cabecalho =
            "🛠️ *Menu do Administrador*\n" +
                "*" + sup.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                "Escolha uma opção:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        if (estabelecimento.isAberto()) {
            itens.add(sup.row("COMANDO|ADMIN_FECHAR_LOJA", "Fechar a loja", "Parar de aceitar pedidos"));
        } else {
            itens.add(sup.row("COMANDO|ADMIN_ABRIR_LOJA", "Abrir loja", "Voltar a aceitar pedidos"));
        }

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|CRIADO|0",
            "Ver pedidos abertos",
            "Status: " + AdministradorWhatsappPedidoService.formatarStatusParaExibicao(StatusPedido.CRIADO)
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|EM_PREPARO|0",
            "Ver pedidos em preparo",
            "Status: " + AdministradorWhatsappPedidoService.formatarStatusParaExibicao(StatusPedido.EM_PREPARO)
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|PRONTO|0",
            "Ver pedidos em entrega",
            "Status: " + AdministradorWhatsappPedidoService.formatarStatusParaExibicao(StatusPedido.PRONTO)
        ));

        itens.add(sup.row(
            "COMANDO|ADMIN_VER_PEDIDOS|ENTREGUE|0",
            "Ver pedidos entregues",
            "Status: " + AdministradorWhatsappPedidoService.formatarStatusParaExibicao(StatusPedido.ENTREGUE)
        ));

        itens.add(sup.row("COMANDO|ADMIN_SUSPENDER_PRODUTO_MENU|0", "Suspender venda", "Tornar produto indisponível"));
        itens.add(sup.row("COMANDO|ADMIN_LIBERAR_PRODUTO_MENU|0", "Liberar venda", "Tornar produto disponível"));

        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_MENU", "Revisar cardápio", "Produtos e marcas"));
        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_MENU", "Taxas de entrega", "CEP da loja e taxas por bairro"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_menu",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Ver opções", "Admin", itens)
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin abrirLoja(Estabelecimento estabelecimento, String whatsappAdmin) {

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

    public AdministradorWhatsappResultados.ResultadoAdmin fecharLoja(Estabelecimento estabelecimento, String whatsappAdmin) {

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

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuCardapio(Estabelecimento estabelecimento, String whatsappAdmin) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        String cabecalho =
            "🧾 *Revisar cardápio*\n" +
                "*" + sup.msg().safe(estabelecimento.getNome()) + "*\n\n" +
                "O que você deseja administrar?";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|0", "Produtos", "Ajustar / excluir"));
        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_MARCAS_MENU|0", "Marcas", "Cadastrar / alterar / excluir"));
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

        boolean temCep = org.springframework.util.StringUtils.hasText(estabelecimento.getCep());
        boolean temBairroBase = (estabelecimento.getBairro() != null && estabelecimento.getBairro().getId() != null);

        String descCep = temCep
            ? ("CEP atual: " + sup.formatarCepParaExibicao(estabelecimento.getCep()))
            : "Salvar o CEP da loja (usado para montar bairros próximos)";

        String descTaxas = (temCep && temBairroBase)
            ? "Configurar taxa padrão e taxa por bairro"
            : "Precisa do CEP da loja para montar a lista";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_CEP_MENU", "Informar CEP da Loja", descCep));
        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_TAXAS_MENU", "Taxa por bairros", descTaxas));
        itens.add(sup.row("COMANDO|ADMIN_MENU", "⬅️ Voltar", "Menu do administrador"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_entregas_menu",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Entregas", "Entregas", itens)
        );
    }
}