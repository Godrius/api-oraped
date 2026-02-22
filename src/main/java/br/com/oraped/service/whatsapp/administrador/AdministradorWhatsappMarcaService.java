package br.com.oraped.service.whatsapp.administrador;

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
import br.com.oraped.domain.MarcaProduto;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.service.MarcaProdutoService;
import br.com.oraped.service.whatsapp.SessaoAtendimentoWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdministradorWhatsappMarcaService {

    private static final int LIST_MAX_ROWS = 10;

    private final MarcaProdutoService marcaProdutoService;
    private final SessaoAtendimentoWhatsappService sessaoService;
    private final AdministradorWhatsappSupport sup;

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuMarcas(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Integer offset
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = (offset == null || offset < 0) ? 0 : offset;

        List<MarcaProduto> todas = marcaProdutoService.listarPorEstabelecimento(estabelecimento.getId());
        if (todas == null) todas = List.of();

        List<MarcaProduto> base = todas.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(m -> sup.msg().safe(m.getNome()), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        int total = base.size();

        if (total == 0) {

            String corpo =
                "🏷️ *Marcas*\n\n" +
                    "Nenhuma marca cadastrada.\n\n" +
                    "Use a opção abaixo para cadastrar.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_marcas_vazio",
                sup.msg().botoes(
                    whatsappAdmin,
                    sup.msg().trunc(corpo, 1024),
                    List.of(
                        sup.btn("COMANDO|ADMIN_MARCA_NOVA_MENU|0", "➕ Nova marca"),
                        sup.btn("COMANDO|ADMIN_CARDAPIO_MENU", "🧾 Cardápio"),
                        sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                    )
                )
            );
        }

        if (safeOffset >= total) safeOffset = 0;

        boolean precisaPaginacao = total > (LIST_MAX_ROWS - 2);
        int pageSizeBase = precisaPaginacao ? 7 : 8;

        int paginasTotal = precisaPaginacao ? (int) Math.ceil(total / (double) pageSizeBase) : 1;
        int paginaAtual = (safeOffset / pageSizeBase) + 1;

        int endExclusive = Math.min(safeOffset + pageSizeBase, total);
        List<MarcaProduto> page = base.subList(safeOffset, endExclusive);
        boolean temMais = endExclusive < total;

        String cabecalho =
            "🏷️ *Marcas*\n" +
                (paginasTotal > 1
                    ? ("Página " + paginaAtual + " de " + paginasTotal)
                    : "Página 1"
                );

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row(
            "COMANDO|ADMIN_MARCA_NOVA_MENU|" + safeOffset,
            "➕ Cadastrar nova marca",
            "Adicionar uma marca ao cardápio"
        ));

        for (MarcaProduto m : page) {
            String nome = sup.msg().safe(m.getNome());
            String status = m.isAtiva() ? "Ativa" : "Inativa";

            itens.add(MensagemInterativaItemListaWhatsappDTO.builder()
                .id("COMANDO|ADMIN_MARCA_DETALHE|" + m.getId() + "|" + safeOffset)
                .title(sup.msg().trunc(nome, 24))
                .description(sup.msg().trunc("Status: " + status, 72))
                .build());
        }

        if (temMais) {
            int nextOffset = safeOffset + page.size();
            itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_MARCAS_MENU|" + nextOffset, "➡️ Mais marcas", "Ver próxima página"));
        }

        itens.add(sup.row("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Voltar", "Revisar cardápio"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_marcas_menu",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Marcas", "Marcas", itens)
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin montarDetalheMarca(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idMarca,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        MarcaProduto m = marcaProdutoService.buscarObrigatorio(idMarca);
        validarMarcaDoEstabelecimento(estabelecimento, m);

        String corpo =
            "🏷️ *Marca*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(m.getNome()), 120) + "*\n" +
                "Status: " + (m.isAtiva() ? "Ativa" : "Inativa") + "\n\n" +
                "O que deseja fazer?";

        List<br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO> botoes = new ArrayList<>();

        botoes.add(sup.btn("COMANDO|ADMIN_MARCA_EDITAR_MENU|" + idMarca + "|" + safeOffset, "✏️ Alterar nome"));
        botoes.add(sup.btn("COMANDO|ADMIN_MARCA_EXCLUIR_CONFIRM|" + idMarca + "|" + safeOffset, "🗑️ Excluir"));

        if (botoes.size() < 3) {
            botoes.add(sup.btn("COMANDO|ADMIN_CARDAPIO_MARCAS_MENU|" + safeOffset, "⬅️ Voltar"));
        }

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_marca_detalhe",
            sup.msg().botoes(whatsappAdmin, sup.msg().trunc(corpo, 1024), botoes)
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroMarcaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        sessaoService.marcarAguardandoNovaMarca(idSessao, safeOffset);

        String corpo =
            "➕ *Cadastrar nova marca*\n\n" +
                "Agora envie apenas o *nome da marca*.\n\n" +
                "Exemplos:\n" +
                "- Coca-Cola\n" +
                "- Heineken\n" +
                "- Skol";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_marca_nova_digitacao",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdminMarca concluirCadastroMarcaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String nomeMarca
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (!StringUtils.hasText(nomeMarca)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nomeMarca é obrigatório");
        }

        if (!sessaoService.isAguardandoNovaMarca(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando nova marca");
        }

        int safeOffset = sessaoService.getOffsetNovaMarca(idSessao);

        MarcaProduto m = marcaProdutoService.criar(estabelecimento.getId(), nomeMarca.trim());
        sessaoService.limparAguardandoNovaMarca(idSessao);

        AdministradorWhatsappResultados.ResultadoAdmin lista = montarMenuMarcas(estabelecimento, whatsappAdmin, safeOffset);

        return new AdministradorWhatsappResultados.ResultadoAdminMarca(lista, m.getId(), m.getNome());
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoNomeMarcaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idMarca,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        MarcaProduto m = marcaProdutoService.buscarObrigatorio(idMarca);
        validarMarcaDoEstabelecimento(estabelecimento, m);

        sessaoService.marcarAguardandoEditarMarcaNome(idSessao, idMarca, safeOffset);

        String corpo =
            "✏️ *Alterar nome da marca*\n\n" +
                "Atual: *" + sup.msg().trunc(sup.msg().safe(m.getNome()), 120) + "*\n\n" +
                "Agora envie apenas o *novo nome*.\n\n" +
                "Exemplos:\n" +
                "- Coca-Cola (Oficial)\n" +
                "- Heineken (Lata)";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_marca_editar_digitacao",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdminMarca concluirAlteracaoNomeMarcaPorDigitacao(
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

        if (!sessaoService.isAguardandoEditarMarcaNome(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando edição do nome da marca");
        }

        Long idMarca = sessaoService.getIdMarcaEditarNome(idSessao);
        int safeOffset = sessaoService.getOffsetEditarMarcaNome(idSessao);

        MarcaProduto m = marcaProdutoService.buscar(idMarca, estabelecimento.getId());
        validarMarcaDoEstabelecimento(estabelecimento, m);

        String nomeLimpo = novoNome.trim();
        marcaProdutoService.atualizarNome(idMarca, estabelecimento.getId(), nomeLimpo);
        sessaoService.limparAguardandoEditarMarcaNome(idSessao);

        AdministradorWhatsappResultados.ResultadoAdmin lista = montarMenuMarcas(estabelecimento, whatsappAdmin, safeOffset);

        return new AdministradorWhatsappResultados.ResultadoAdminMarca(lista, idMarca, nomeLimpo);
    }

    public AdministradorWhatsappResultados.ResultadoAdmin confirmarExclusaoMarca(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idMarca,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        MarcaProduto m = marcaProdutoService.buscarObrigatorio(idMarca);
        validarMarcaDoEstabelecimento(estabelecimento, m);

        String corpo =
            "⚠️ *Excluir marca*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(m.getNome()), 120) + "*\n\n" +
                "Tem certeza que deseja excluir?";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_marca_excluir_confirm",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_MARCA_EXCLUIR|" + idMarca + "|" + safeOffset, "🗑️ Excluir"),
                    sup.btn("COMANDO|ADMIN_MARCA_DETALHE|" + idMarca + "|" + safeOffset, "⬅️ Cancelar")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin excluirMarca(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idMarca,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idMarca == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMarca é obrigatório");
        }
        if (estabelecimento.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        int safeOffset = (offsetLista == null || offsetLista < 0) ? 0 : offsetLista;

        MarcaProduto m = marcaProdutoService.buscar(idMarca, estabelecimento.getId());
        validarMarcaDoEstabelecimento(estabelecimento, m);

        marcaProdutoService.excluir(idMarca, estabelecimento.getId());

        String corpo =
            "🗑️ Marca excluída.\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(m.getNome()), 120) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_marca_excluir_ok",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_CARDAPIO_MARCAS_MENU|" + safeOffset, "🏷️ Voltar à lista"),
                    sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }

    private void validarMarcaDoEstabelecimento(Estabelecimento estabelecimento, MarcaProduto marca) {

        if (marca == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Marca não encontrada");
        }
        if (marca.getEstabelecimento() == null || marca.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Marca sem estabelecimento associado");
        }
        if (!Objects.equals(marca.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Marca não pertence ao estabelecimento");
        }
    }
}