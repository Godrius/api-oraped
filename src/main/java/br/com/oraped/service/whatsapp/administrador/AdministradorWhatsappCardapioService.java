package br.com.oraped.service.whatsapp.administrador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.Produto;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.ProdutoService;
import br.com.oraped.service.whatsapp.SessaoAtendimentoWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdministradorWhatsappCardapioService {

    private static final int LIST_MAX_ROWS = 10;

    private final ProdutoService produtoService;
    private final SessaoAtendimentoWhatsappService sessaoService;
    private final AdministradorWhatsappSupport sup;

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuCardapioProdutos(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offset
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        List<Produto> produtos = produtoService.listarPorEstabelecimento(estabelecimento.getId());
        if (produtos == null) produtos = List.of();

        List<Produto> ordenados = produtos.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(p -> sup.msg().safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        int total = ordenados.size();

        if (total == 0) {

            String corpo =
                "🧾 *Produtos do cardápio*\n\n" +
                    "Nenhum produto cadastrado.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_cardapio_produtos_vazio",
                sup.msg().botoes(
                    whatsappAdmin,
                    sup.msg().trunc(corpo, 1024),
                    List.of(
                        sup.btn("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Voltar"),
                        sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                    )
                )
            );
        }

        if (safeOffset >= total) safeOffset = 0;

        boolean precisaPaginacao = total > LIST_MAX_ROWS;
        int pageSizeBase = precisaPaginacao ? 8 : 9;
        int paginasTotal = precisaPaginacao ? (int) Math.ceil(total / (double) pageSizeBase) : 1;
        int paginaAtual = (safeOffset / pageSizeBase) + 1;

        int endExclusive = Math.min(safeOffset + pageSizeBase, total);
        List<Produto> page = ordenados.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < total;

        String cabecalho =
            "🧾 *Produtos do cardápio*\n" +
                (paginasTotal > 1
                    ? ("Página " + paginaAtual + " de " + paginasTotal)
                    : "Página 1"
                );

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        for (Produto p : page) {

            String nome = sup.msg().safe(p.getNome());
            String preco = sup.msg().formatarMoeda(p.getPreco());

            String title = sup.msg().trunc(nome + " • " + preco, 24);

            String desc = sup.msg().safe(p.getDescricao());
            String description = StringUtils.hasText(desc)
                ? sup.msg().trunc(desc, 72)
                : sup.msg().trunc("Preço: " + preco, 72);

            itens.add(MensagemInterativaItemListaWhatsappDTO.builder()
                .id("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + p.getId() + "|" + safeOffset)
                .title(title)
                .description(description)
                .build());
        }

        if (temMais) {
            int nextOffset = safeOffset + page.size();
            itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + nextOffset, "➡️ Mais produtos", "Ver próxima página"));
        }

        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Voltar", "Revisar cardápio"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cardapio_produtos_menu",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Produtos", "Produtos", itens)
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuAcoesProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String nome = sup.msg().trunc(sup.msg().safe(p.getNome()), 80);

        String descricao = sup.msg().safe(p.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String preco = sup.msg().formatarMoeda(p.getPreco());

        String cabecalho =
            "*" + nome + "*\n" +
                sup.msg().trunc(descricao, 500) + "\n\n" +
                "*Preço atual:* " + preco + "\n\n" +
                "O que deseja fazer?";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row("COMANDO|ADMIN_PROD_PRECO_MENU|" + idProduto + "|" + safeOffset, "Ajustar preço", "Incrementos ou informar valor"));
        itens.add(sup.row("COMANDO|ADMIN_PROD_NOME_MENU|" + idProduto + "|" + safeOffset, "Ajustar nome", "Enviar 1 mensagem com o novo nome"));
        itens.add(sup.row("COMANDO|ADMIN_PROD_DESC_MENU|" + idProduto + "|" + safeOffset, "Ajustar descrição", "Enviar 1 mensagem com a nova descrição"));
        itens.add(sup.row("COMANDO|ADMIN_PROD_EXCLUIR_CONFIRM|" + idProduto + "|" + safeOffset, "Excluir produto", "Remover do cardápio"));
        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + safeOffset, "⬅️ Voltar", "Lista de produtos"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_cardapio_produto_acoes",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Ações", "Ações", itens)
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuAjustePrecoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String nome = sup.msg().trunc(sup.msg().safe(p.getNome()), 80);

        String descricao = sup.msg().safe(p.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String preco = sup.msg().formatarMoeda(p.getPreco());

        String cabecalho =
            "💲 Ajustar preço\n\n" +
                "*" + nome + "*\n" +
                sup.msg().trunc(descricao, 500) + "\n\n" +
                "*Preço atual:* " + preco + "\n\n" +
                "Escolha um ajuste:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|100|" + safeOffset, "+ R$ 1,00", "Aumentar"));
        itens.add(sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|200|" + safeOffset, "+ R$ 2,00", "Aumentar"));
        itens.add(sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|500|" + safeOffset, "+ R$ 5,00", "Aumentar"));

        itens.add(sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-100|" + safeOffset, "- R$ 1,00", "Diminuir"));
        itens.add(sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-200|" + safeOffset, "- R$ 2,00", "Diminuir"));
        itens.add(sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-500|" + safeOffset, "- R$ 5,00", "Diminuir"));

        itens.add(sup.row("COMANDO|ADMIN_PROD_PRECO_MANUAL|" + idProduto + "|" + safeOffset, "Outro valor (digitar)", "Enviar 1 mensagem com o valor"));
        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + safeOffset, "⬅️ Voltar", "Ações do produto"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_preco_menu",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Preço", "Preço", itens)
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdminPreco aplicarDeltaPrecoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer deltaCentavos,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }
        if (deltaCentavos == null || deltaCentavos == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deltaCentavos é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        BigDecimal atual = p.getPreco() == null ? BigDecimal.ZERO : p.getPreco();
        BigDecimal delta = BigDecimal.valueOf(deltaCentavos).movePointLeft(2);

        BigDecimal novo = atual.add(delta);
        if (novo.compareTo(BigDecimal.ZERO) < 0) novo = BigDecimal.ZERO;

        produtoService.atualizarPreco(idProduto, novo);

        String nome = sup.msg().trunc(sup.msg().safe(p.getNome()), 80);
        String desc = sup.msg().safe(p.getDescricao());
        if (!StringUtils.hasText(desc)) desc = "Sem descrição.";

        AdministradorWhatsappResultados.ResultadoAdmin lista = montarMenuCardapioProdutos(estabelecimento, whatsappAdmin, safeOffset);

        return new AdministradorWhatsappResultados.ResultadoAdminPreco(lista, novo, nome, desc);
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarPrecoManualProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        sessaoService.marcarAguardandoNovoPreco(idSessao, idProduto, safeOffset);

        String corpo =
            "💲 *Ajustar preço*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(p.getNome()), 80) + "*\n\n" +
                "Agora envie apenas o *novo preço*.\n\n" +
                "Exemplos:\n" +
                "- 10\n" +
                "- 10,50\n" +
                "- R$ 10,50";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_preco_digitacao",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdminPreco concluirPrecoManualProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String textoDigitado
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (!StringUtils.hasText(textoDigitado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "textoDigitado é obrigatório");
        }

        if (!sessaoService.isAguardandoNovoPreco(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo preço");
        }

        Long idProduto = sessaoService.getIdProdutoNovoPreco(idSessao);
        int safeOffset = sessaoService.getOffsetListaNovoPreco(idSessao);

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        BigDecimal novoPreco = parsePrecoDigitado(textoDigitado);
        if (novoPreco.compareTo(BigDecimal.ZERO) < 0) novoPreco = BigDecimal.ZERO;

        produtoService.atualizarPreco(idProduto, novoPreco);
        sessaoService.limparAguardandoNovoPreco(idSessao);

        String nome = sup.msg().trunc(sup.msg().safe(p.getNome()), 80);
        String desc = sup.msg().safe(p.getDescricao());
        if (!StringUtils.hasText(desc)) desc = "Sem descrição.";

        AdministradorWhatsappResultados.ResultadoAdmin lista = montarMenuCardapioProdutos(estabelecimento, whatsappAdmin, safeOffset);

        return new AdministradorWhatsappResultados.ResultadoAdminPreco(lista, novoPreco, nome, desc);
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoNomeProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        sessaoService.marcarAguardandoNovoNomeProduto(idSessao, idProduto, safeOffset);

        String corpo =
            "✏️ *Ajustar nome*\n\n" +
                "Atual: *" + sup.msg().trunc(sup.msg().safe(p.getNome()), 80) + "*\n\n" +
                "Agora envie apenas o *novo nome*.\n\n" +
                "Exemplos:\n" +
                "- Coca-Cola 2L\n" +
                "- Heineken Lata 350ml";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_nome_digitacao",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoNomeProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novoNome
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (!StringUtils.hasText(novoNome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoNome é obrigatório");
        }

        if (!sessaoService.isAguardandoNovoNomeProduto(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo nome do produto");
        }

        Long idProduto = sessaoService.getIdProdutoNovoNome(idSessao);
        int safeOffset = sessaoService.getOffsetListaNovoNome(idSessao);

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String nomeLimpo = novoNome.trim();
        produtoService.atualizarNome(idProduto, nomeLimpo);
        sessaoService.limparAguardandoNovoNomeProduto(idSessao);

        String corpo =
            "✅ Nome atualizado.\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(nomeLimpo), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_nome_ok",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + safeOffset, "🧾 Voltar ao produto"),
                    sup.btn("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + safeOffset, "📦 Voltar à lista")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoDescricaoProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        sessaoService.marcarAguardandoNovaDescricaoProduto(idSessao, idProduto, safeOffset);

        String corpo =
            "📝 *Ajustar descrição*\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(p.getNome()), 80) + "*\n\n" +
                "Agora envie apenas a *nova descrição*.\n\n" +
                "Dica: tente manter curto.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_desc_digitacao",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoDescricaoProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String novaDesc
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (!StringUtils.hasText(novaDesc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novaDesc é obrigatória");
        }

        if (!sessaoService.isAguardandoNovaDescricaoProduto(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando nova descrição do produto");
        }

        Long idProduto = sessaoService.getIdProdutoNovaDescricao(idSessao);
        int safeOffset = sessaoService.getOffsetListaNovaDescricao(idSessao);

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String descLimpa = novaDesc.trim();
        produtoService.atualizarDescricao(idProduto, descLimpa);
        sessaoService.limparAguardandoNovaDescricaoProduto(idSessao);

        String corpo =
            "✅ Descrição atualizada.\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(p.getNome()), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_desc_ok",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + safeOffset, "🧾 Voltar ao produto"),
                    sup.btn("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + safeOffset, "📦 Voltar à lista")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin confirmarExclusaoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        String corpo =
            "⚠️ *Excluir produto*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(p.getNome()), 80) + "*\n" +
                "Preço: " + sup.msg().formatarMoeda(p.getPreco()) + "\n\n" +
                "Tem certeza que deseja excluir?";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_excluir_confirm",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_PROD_EXCLUIR|" + idProduto + "|" + safeOffset, "🗑️ Excluir"),
                    sup.btn("COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + safeOffset, "⬅️ Cancelar")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin excluirProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        produtoService.excluir(idProduto);

        String corpo =
            "🗑️ Produto excluído.\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(p.getNome()), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_excluir_ok",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_CARDAPIO_PRODUTOS_MENU|" + safeOffset, "🧾 Voltar à lista"),
                    sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }

    private void validarProdutoDoEstabelecimento(Estabelecimento estabelecimento, Produto produto) {

        if (produto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }
        if (produto.getEstabelecimento() == null || produto.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto sem estabelecimento associado");
        }
        if (!Objects.equals(produto.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto não pertence ao estabelecimento");
        }
    }

    private BigDecimal parsePrecoDigitado(String texto) {

        String v = texto == null ? "" : texto.trim();

        if (!StringUtils.hasText(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }

        v = v.replace("R$", "")
            .replace("r$", "")
            .replace(" ", "")
            .trim();

        v = v.replace(",", ".");
        v = v.replaceAll("[^0-9.\\-+]", "");

        if (!StringUtils.hasText(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }

        try {
            BigDecimal bd = new BigDecimal(v);
            return bd.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }
    }
}