package br.com.oraped.service.whatsapp.administrador;

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
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdministradorWhatsappProdutoDisponibilidadeService {

    private static final int LIST_MAX_ROWS = 10;

    private final ProdutoService produtoService;
    private final AdministradorWhatsappSupport sup;

    public AdministradorWhatsappResultados.ResultadoAdmin listarProdutosParaSuspender(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offset
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        List<Produto> todos = produtoService.listarPorEstabelecimento(estabelecimento.getId());
        List<Produto> disponiveis = (todos == null ? List.<Produto>of() : todos).stream()
            .filter(Objects::nonNull)
            .filter(Produto::isDisponivelParaVenda)
            .sorted(Comparator.comparing(p -> sup.msg().safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        if (disponiveis.isEmpty()) {

            String corpo =
                "🚫 Suspender venda\n\n" +
                    "Não há produtos *disponíveis* para suspender.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_suspender_sem_produtos",
                sup.msg().botoes(
                    whatsappAdmin,
                    sup.msg().trunc(corpo, 1024),
                    List.of(sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
                )
            );
        }

        return montarListaProdutosTogglePaginada(
            whatsappAdmin,
            "🚫 Suspender venda\nEscolha um produto:",
            disponiveis,
            safeOffset,
            "COMANDO|ADMIN_SUSPENDER_PRODUTO|",
            "COMANDO|ADMIN_SUSPENDER_PRODUTO_MENU|"
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin listarProdutosParaLiberar(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offset
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        List<Produto> todos = produtoService.listarPorEstabelecimento(estabelecimento.getId());
        List<Produto> indisponiveis = (todos == null ? List.<Produto>of() : todos).stream()
            .filter(Objects::nonNull)
            .filter(p -> !p.isDisponivelParaVenda())
            .sorted(Comparator.comparing(p -> sup.msg().safe(p.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        if (indisponiveis.isEmpty()) {

            String corpo =
                "✅ Liberar venda\n\n" +
                    "Não há produtos *suspensos* para liberar.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_liberar_sem_produtos",
                sup.msg().botoes(
                    whatsappAdmin,
                    sup.msg().trunc(corpo, 1024),
                    List.of(sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin"))
                )
            );
        }

        return montarListaProdutosTogglePaginada(
            whatsappAdmin,
            "✅ Liberar venda\nEscolha um produto:",
            indisponiveis,
            safeOffset,
            "COMANDO|ADMIN_LIBERAR_PRODUTO|",
            "COMANDO|ADMIN_LIBERAR_PRODUTO_MENU|"
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin suspenderProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        produtoService.indisponibilizar(idProduto);

        String corpo =
            "🚫 Venda suspensa ✅\n\n" +
                "*" + sup.msg().safe(p.getNome()) + "*\n" +
                "Agora este produto não pode ser pedido pelo WhatsApp.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_produto_suspenso",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_SUSPENDER_PRODUTO_MENU|0", "🚫 Suspender outro"),
                    sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin liberarProduto(Estabelecimento estabelecimento, String whatsappAdmin, Long idProduto) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        Produto p = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, p);

        produtoService.disponibilizar(idProduto);

        String corpo =
            "✅ Venda liberada ✅\n\n" +
                "*" + sup.msg().safe(p.getNome()) + "*\n" +
                "Agora este produto pode ser pedido pelo WhatsApp.";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_produto_liberado",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_LIBERAR_PRODUTO_MENU|0", "✅ Liberar outro"),
                    sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }

    private AdministradorWhatsappResultados.ResultadoAdmin montarListaProdutosTogglePaginada(
        String whatsappAdmin,
        String cabecalhoBase,
        List<Produto> base,
        int offset,
        String cmdPrefixAcao,
        String cmdPrefixPagina
    ) {

        int total = base == null ? 0 : base.size();
        if (total == 0) {
            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_lista_produtos_vazio",
                sup.msg().botoes(whatsappAdmin, "Nenhum produto encontrado.", List.of(sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")))
            );
        }

        int safeOffset = Math.max(0, offset);
        if (safeOffset >= total) safeOffset = 0;

        boolean precisaPaginacao = total > LIST_MAX_ROWS;
        int pageSizeBase = precisaPaginacao ? 8 : 9;
        int paginasTotal = precisaPaginacao ? (int) Math.ceil(total / (double) pageSizeBase) : 1;
        int paginaAtual = (safeOffset / pageSizeBase) + 1;

        int endExclusive = Math.min(safeOffset + pageSizeBase, total);
        List<Produto> page = base.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < total;

        String cabecalho =
            cabecalhoBase + "\n" +
                (paginasTotal > 1
                    ? ("Página " + paginaAtual + " de " + paginasTotal)
                    : "Página 1"
                );

        List<MensagemInterativaItemListaWhatsappDTO> itens = page.stream()
            .map(p -> {
                String nome = sup.msg().safe(p.getNome());
                String preco = sup.msg().formatarMoeda(p.getPreco());

                String title = sup.msg().trunc(nome + " • " + preco, 24);

                String desc = StringUtils.hasText(p.getDescricao())
                    ? sup.msg().trunc(p.getDescricao(), 72)
                    : sup.msg().trunc("Preço: " + preco, 72);

                return MensagemInterativaItemListaWhatsappDTO.builder()
                    .id(cmdPrefixAcao + p.getId())
                    .title(title)
                    .description(desc)
                    .build();
            })
            .collect(Collectors.toList());

        if (temMais) {
            int nextOffset = safeOffset + page.size();
            itens.add(sup.row(cmdPrefixPagina + nextOffset, "➡️ Mais produtos", "Ver próxima página"));
        }

        itens.add(sup.row("COMANDO|ADMIN_MENU", "⬅️ Voltar", "Menu do administrador"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_lista_produtos",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Produtos", "Produtos", itens)
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
}