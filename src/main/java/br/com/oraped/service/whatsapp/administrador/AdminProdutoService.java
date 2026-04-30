package br.com.oraped.service.whatsapp.administrador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.produto.CategoriaProduto;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.integration.HostingerClient;
import br.com.oraped.integration.WhatsappCloudMediaClient;
import br.com.oraped.repository.produto.CategoriaProdutoRepository;
import br.com.oraped.repository.produto.ProdutoRepository;
import br.com.oraped.service.produto.CategoriaProdutoService;
import br.com.oraped.service.produto.ProdutoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminProdutoService;
import lombok.RequiredArgsConstructor;

/**
 * Serviço administrativo para manutenção direta dos dados do produto.
 *
 * Aplicação:
 * - concentra ações de preço, nome, descrição, foto e exclusão
 * - mantém o AdministradorWhatsappCardapioService focado em navegação do cardápio
 * - evita que novos módulos, como complementos, aumentem ainda mais o service de cardápio
 */
@Service
@RequiredArgsConstructor
public class AdminProdutoService {

    private final ProdutoService produtoService;
    private final CategoriaProdutoService categoriaProdutoService;
    private final SessaoWhatsappAdminProdutoService sessaoAdminProdutoService;
    private final WhatsappCloudMediaClient whatsappCloudMediaClient;
    private final HostingerClient hostingerClient;
    private final AdminCardapioService cardapioService;
    private final AdminWhatsappUiHelper sup;
    
    private final ProdutoRepository produtoRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    
    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuAjustePrecoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        String descricao = sup.msg().safe(produto.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        String cabecalho =
            "💲 Ajustar preço\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                sup.msg().trunc(descricao, 500) + "\n\n" +
                "*Preço atual:* " + sup.msg().formatarMoeda(produto.getPreco()) + "\n\n" +
                "Escolha um ajuste:";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_preco_menu",
            sup.msg().lista(
                whatsappAdmin,
                sup.msg().truncWord(cabecalho, 1024),
                "Preço",
                "Preço",
                List.of(
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|100|" + idCategoria + "|" + safeOffset, "+ R$ 1,00", "Aumentar"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|200|" + idCategoria + "|" + safeOffset, "+ R$ 2,00", "Aumentar"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|500|" + idCategoria + "|" + safeOffset, "+ R$ 5,00", "Aumentar"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-100|" + idCategoria + "|" + safeOffset, "- R$ 1,00", "Diminuir"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-200|" + idCategoria + "|" + safeOffset, "- R$ 2,00", "Diminuir"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_APLICAR|" + idProduto + "|-500|" + idCategoria + "|" + safeOffset, "- R$ 5,00", "Diminuir"),
                    sup.row("COMANDO|ADMIN_PROD_PRECO_MANUAL|" + idProduto + "|" + idCategoria + "|" + safeOffset, "Outro valor", "Enviar 1 mensagem"),
                    sup.row(montarComandoVoltarProduto(idProduto, idCategoria, safeOffset), "⬅️ Voltar", "Ações do produto")
                )
            )
        );
    }

    
    public AdministradorWhatsappResultados.ResultadoAdmin listarCategoriasParaNovoProduto(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Integer offsetCategorias
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    List<CategoriaProduto> categorias =
	        categoriaProdutoRepository.findByEstabelecimentoIdAndAtivaTrueOrderByNomeAsc(estabelecimento.getId());

	    if (categorias.isEmpty()) {
	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_produto_novo_sem_categorias",
	            sup.msg().botoes(
	                whatsappAdmin,
	                "➕ *Novo produto*\n\nAntes de cadastrar produtos, cadastre pelo menos uma categoria.",
	                List.of(
	                    sup.btn("COMANDO|ADMIN_CATEGORIA_NOVA_MENU|0", "➕ Nova categoria"),
	                    sup.btn("COMANDO|ADMIN_CARDAPIO_MENU", "⬅️ Cardápio")
	                )
	            )
	        );
	    }

	    int safeOffset = normalizarOffset(offsetCategorias);
	    if (safeOffset >= categorias.size()) {
	        safeOffset = 0;
	    }

	    int pageSize = categorias.size() > 10 ? 8 : 9;
	    int endExclusive = Math.min(safeOffset + pageSize, categorias.size());
	    List<CategoriaProduto> page = categorias.subList(safeOffset, endExclusive);
	    boolean temMais = endExclusive < categorias.size();

	    String corpo =
	        "➕ *Novo produto*\n\n" +
	            "Escolha em qual categoria o produto será cadastrado.";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    for (CategoriaProduto categoria : page) {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_PRODUTO_NOVO_MENU|" + categoria.getId() + "|" + safeOffset,
	            sup.msg().trunc(sup.msg().safe(categoria.getNome()), 24),
	            "Cadastrar produto aqui"
	        ));
	    }

	    if (temMais) {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_PRODUTO_NOVO_CATEGORIA_MENU|" + endExclusive,
	            "➡️ Mais categorias",
	            "Ver próxima página"
	        ));
	    }

	    itens.add(sup.row(
	        "COMANDO|ADMIN_CARDAPIO_MENU",
	        "⬅️ Voltar",
	        "Menu do cardápio"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_produto_novo_categorias",
	        sup.msg().lista(
	            whatsappAdmin,
	            sup.msg().truncWord(corpo, 1024),
	            "Categorias",
	            "Categorias",
	            itens
	        )
	    );
	}
    
	 // =========================================================
	 // PRODUTO: CRIAÇÃO POR DIGITAÇÃO
	 // =========================================================
	
	 public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroProdutoPorDigitacao(
	     Estabelecimento estabelecimento,
	     String whatsappAdmin,
	     Long idSessao,
	     Long idCategoria,
	     Integer offsetCategorias
	 ) {
	     sup.validarBasico(estabelecimento, whatsappAdmin);
	
	     sessaoAdminProdutoService.marcarAguardandoNovoProduto(
	         idSessao,
	         idCategoria,
	         offsetCategorias
	     );
	
	     String corpo =
	         "➕ *Novo produto*\n\n" +
	             "Digite o *nome do produto*.\n\n" +
	             "Exemplos:\n" +
	             "- X-Burger\n" +
	             "- Coca-Cola 2L\n" +
	             "- Açaí 500ml";
	
	     return new AdministradorWhatsappResultados.ResultadoAdmin(
	         "admin_produto_novo_digitacao",
	         sup.msg().botoes(
	             whatsappAdmin,
	             sup.msg().trunc(corpo, 1024),
	             List.of(
	                 sup.btn(
	                     "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + offsetCategorias,
	                     "⬅️ Cancelar"
	                 )
	             )
	         )
	     );
	 }
 
 
 	public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroProdutoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String nomeProduto
	) {
	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    if (!StringUtils.hasText(nomeProduto)) {
	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_produto_nome_invalido",
	            sup.msg().texto(
	                whatsappAdmin,
	                "Não consegui identificar o nome do produto.\n\nEnvie apenas o nome."
	            )
	        );
	    }

	    Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovoProduto(idSessao);
	    Integer offset = sessaoAdminProdutoService.getOffsetListaNovoProduto(idSessao);

	    CategoriaProduto categoria = categoriaProdutoRepository.findById(idCategoria)
	    		.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));

	    validarCategoriaDoEstabelecimento(estabelecimento, categoria);
	    
	    Produto produto = new Produto();
	    produto.setEstabelecimento(estabelecimento);
	    produto.setCategoria(categoria);
	    produto.setNome(nomeProduto.trim());
	    
	    // Começa sem preço — admin ajusta depois
	    produto.setPreco(BigDecimal.ZERO);

	    produtoRepository.save(produto);

	    sessaoAdminProdutoService.limparAguardandoNovoProduto(idSessao);

	    String corpo =
	        "✅ Produto cadastrado.\n\n" +
	            "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
	            "⚠️ Lembre-se de ajustar o preço.";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_produto_novo_ok",
	        sup.msg().botoes(
	            whatsappAdmin,
	            sup.msg().trunc(corpo, 1024),
	            List.of(
	                sup.btn(
	                    "COMANDO|ADMIN_PROD_PRECO_MENU|" + produto.getId() + "|" + idCategoria + "|" + offset,
	                    "💰 Definir preço"
	                ),
	                sup.btn(
	                    "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + offset,
	                    "📂 Ver produtos"
	                )
	            )
	        )
	    );
	}
 	
    public AdministradorWhatsappResultados.ResultadoAdminPreco aplicarDeltaPrecoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Integer deltaCentavos,
        Long idCategoria,
        Integer offsetLista
    ) {

        if (deltaCentavos == null || deltaCentavos == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deltaCentavos é obrigatório");
        }

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        BigDecimal atual = produto.getPreco() == null ? BigDecimal.ZERO : produto.getPreco();
        BigDecimal novo = atual.add(BigDecimal.valueOf(deltaCentavos).movePointLeft(2));

        if (novo.compareTo(BigDecimal.ZERO) < 0) {
            novo = BigDecimal.ZERO;
        }

        produtoService.atualizarPreco(idProduto, novo);

        AdministradorWhatsappResultados.ResultadoAdmin lista =
            cardapioService.montarMenuCardapioProdutosPorCategoria(estabelecimento, whatsappAdmin, idCategoria, safeOffset);

        String descricao = sup.msg().safe(produto.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        return new AdministradorWhatsappResultados.ResultadoAdminPreco(
            lista,
            novo,
            sup.msg().trunc(sup.msg().safe(produto.getNome()), 80),
            descricao
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarPrecoManualProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        validarSessao(idSessao);
        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        sessaoAdminProdutoService.marcarAguardandoNovoPreco(idSessao, idProduto, idCategoria, safeOffset);

        String corpo =
            "💲 *Ajustar preço*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
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

        validarSessao(idSessao);

        if (!StringUtils.hasText(textoDigitado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "textoDigitado é obrigatório");
        }
        if (!sessaoAdminProdutoService.isAguardandoNovoPreco(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo preço");
        }

        Long idProduto = sessaoAdminProdutoService.getIdProdutoNovoPreco(idSessao);
        Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovoPreco(idSessao);
        int safeOffset = sessaoAdminProdutoService.getOffsetListaNovoPreco(idSessao);

        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        BigDecimal novoPreco = parsePrecoDigitado(textoDigitado);
        if (novoPreco.compareTo(BigDecimal.ZERO) < 0) {
            novoPreco = BigDecimal.ZERO;
        }

        produtoService.atualizarPreco(idProduto, novoPreco);
        sessaoAdminProdutoService.limparAguardandoNovoPreco(idSessao);

        AdministradorWhatsappResultados.ResultadoAdmin lista =
            cardapioService.montarMenuCardapioProdutosPorCategoria(estabelecimento, whatsappAdmin, idCategoria, safeOffset);

        String descricao = sup.msg().safe(produto.getDescricao());
        if (!StringUtils.hasText(descricao)) {
            descricao = "Sem descrição.";
        }

        return new AdministradorWhatsappResultados.ResultadoAdminPreco(
            lista,
            novoPreco,
            sup.msg().trunc(sup.msg().safe(produto.getNome()), 80),
            descricao
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoNomeProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        validarSessao(idSessao);
        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        sessaoAdminProdutoService.marcarAguardandoNovoNomeProduto(idSessao, idProduto, idCategoria, safeOffset);

        String corpo =
            "✏️ *Ajustar nome*\n\n" +
                "Atual: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                "Agora envie apenas o *novo nome*.";

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

        validarSessao(idSessao);

        if (!StringUtils.hasText(novoNome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novoNome é obrigatório");
        }
        if (!sessaoAdminProdutoService.isAguardandoNovoNomeProduto(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando novo nome do produto");
        }

        Long idProduto = sessaoAdminProdutoService.getIdProdutoNovoNome(idSessao);
        Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovoNome(idSessao);
        int safeOffset = sessaoAdminProdutoService.getOffsetListaNovoNome(idSessao);

        buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        String nomeLimpo = novoNome.trim();
        produtoService.atualizarNome(idProduto, nomeLimpo);
        sessaoAdminProdutoService.limparAguardandoNovoNomeProduto(idSessao);

        String corpo =
            "✅ Nome atualizado.\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(nomeLimpo), 80) + "*";

        return montarRetornoProdutoLista(
            whatsappAdmin,
            "admin_prod_nome_ok",
            corpo,
            idProduto,
            idCategoria,
            safeOffset
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoDescricaoProdutoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        validarSessao(idSessao);
        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        sessaoAdminProdutoService.marcarAguardandoNovaDescricaoProduto(idSessao, idProduto, idCategoria, safeOffset);

        String corpo =
            "📝 *Ajustar descrição*\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                "Agora envie apenas a *nova descrição*.";

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

        validarSessao(idSessao);

        if (!StringUtils.hasText(novaDesc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novaDesc é obrigatória");
        }
        if (!sessaoAdminProdutoService.isAguardandoNovaDescricaoProduto(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando nova descrição do produto");
        }

        Long idProduto = sessaoAdminProdutoService.getIdProdutoNovaDescricao(idSessao);
        Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovaDescricao(idSessao);
        int safeOffset = sessaoAdminProdutoService.getOffsetListaNovaDescricao(idSessao);

        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        produtoService.atualizarDescricao(idProduto, novaDesc.trim());
        sessaoAdminProdutoService.limparAguardandoNovaDescricaoProduto(idSessao);

        String corpo =
            "✅ Descrição atualizada.\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*";

        return montarRetornoProdutoLista(
            whatsappAdmin,
            "admin_prod_desc_ok",
            corpo,
            idProduto,
            idCategoria,
            safeOffset
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarAlteracaoFotoProdutoPorEnvioImagem(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        validarSessao(idSessao);
        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        sessaoAdminProdutoService.marcarAguardandoNovaFotoProduto(idSessao, idProduto, idCategoria, safeOffset);

        String corpo =
            "🖼️ *Atualizar foto do produto*\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                "Agora envie *uma foto* do produto nesta conversa.\n\n" +
                "Regras:\n" +
                "- envie apenas 1 imagem\n" +
                "- prefira foto nítida e bem iluminada\n" +
                "- após o envio, a foto atual será substituída";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_foto_aguardando_envio",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirAlteracaoFotoProdutoPorImagem(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String idMidia,
        String mimeTypeMidia
    ) {

        validarSessao(idSessao);

        if (!StringUtils.hasText(idMidia)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idMidia é obrigatório");
        }
        if (!sessaoAdminProdutoService.isAguardandoNovaFotoProduto(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando nova foto do produto");
        }

        Long idProduto = sessaoAdminProdutoService.getIdProdutoNovaFoto(idSessao);
        Long idCategoria = sessaoAdminProdutoService.getIdCategoriaNovaFoto(idSessao);
        int safeOffset = sessaoAdminProdutoService.getOffsetListaNovaFoto(idSessao);

        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        WhatsappCloudMediaClient.MediaMetadata metadata = whatsappCloudMediaClient.buscarMetadata(idMidia);

        String mimeTypeFinal = StringUtils.hasText(metadata.mimeType())
            ? metadata.mimeType()
            : mimeTypeMidia;

        byte[] bytesImagem = whatsappCloudMediaClient.baixarMidia(metadata.url());
        String urlFotoAnterior = produto.getUrlFoto();

        String novaUrlFoto = hostingerClient.uploadFotoProduto(
            estabelecimento.getId(),
            produto.getId(),
            bytesImagem,
            mimeTypeFinal
        );

        produtoService.atualizarUrlFoto(produto.getId(), novaUrlFoto);
        sessaoAdminProdutoService.limparAguardandoNovaFotoProduto(idSessao);

        if (StringUtils.hasText(urlFotoAnterior) && !urlFotoAnterior.equals(novaUrlFoto)) {
            try {
                hostingerClient.deleteByUrl(urlFotoAnterior);
            } catch (Exception ignored) {
            }
        }

        String corpo =
            "✅ Foto atualizada com sucesso!\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*";

        return montarRetornoProdutoLista(
            whatsappAdmin,
            "admin_prod_foto_ok",
            corpo,
            idProduto,
            idCategoria,
            safeOffset
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin confirmarRemocaoFotoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        if (!StringUtils.hasText(produto.getUrlFoto())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto não possui foto cadastrada");
        }

        String corpo =
            "🗑️ *Remover foto do produto*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n\n" +
                "Tem certeza que deseja remover a foto atual?";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_foto_remover_confirm",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_PROD_FOTO_REMOVER|" + idProduto + "|" + idCategoria + "|" + safeOffset, "🗑️ Remover"),
                    sup.btn(montarComandoVoltarProduto(idProduto, idCategoria, safeOffset), "⬅️ Cancelar")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin removerFotoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        String urlFotoAtual = produto.getUrlFoto();

        if (!StringUtils.hasText(urlFotoAtual)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto não possui foto cadastrada");
        }

        try {
            hostingerClient.deleteByUrl(urlFotoAtual);
        } catch (Exception ignored) {
        }

        produtoService.atualizarUrlFoto(idProduto, null);

        String corpo =
            "✅ Foto removida com sucesso!\n\n" +
                "Produto: *" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*";

        return montarRetornoProdutoLista(
            whatsappAdmin,
            "admin_prod_foto_removida",
            corpo,
            idProduto,
            idCategoria,
            safeOffset
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin confirmarExclusaoProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        String corpo =
            "⚠️ *Excluir produto*\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*\n" +
                "Preço: " + sup.msg().formatarMoeda(produto.getPreco()) + "\n\n" +
                "Tem certeza que deseja excluir?";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_excluir_confirm",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn("COMANDO|ADMIN_PROD_EXCLUIR|" + idProduto + "|" + idCategoria + "|" + safeOffset, "🗑️ Excluir"),
                    sup.btn(montarComandoVoltarProduto(idProduto, idCategoria, safeOffset), "⬅️ Cancelar")
                )
            )
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin excluirProduto(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {

        int safeOffset = normalizarOffset(offsetLista);
        Produto produto = buscarProdutoValidado(estabelecimento, idProduto, idCategoria);

        produtoService.excluir(idProduto);

        String corpo =
            "🗑️ Produto excluído.\n\n" +
                "*" + sup.msg().trunc(sup.msg().safe(produto.getNome()), 80) + "*";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_prod_excluir_ok",
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn(montarComandoVoltarListaProdutos(idCategoria, safeOffset), "🧾 Voltar à lista"),
                    sup.btn("COMANDO|ADMIN_MENU", "🛠️ Menu admin")
                )
            )
        );
    }

    private Produto buscarProdutoValidado(Estabelecimento estabelecimento, Long idProduto, Long idCategoria) {
        sup.validarBasico(estabelecimento, "admin");

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }
        if (idCategoria == null || idCategoria <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCategoria é obrigatório");
        }

        Produto produto = produtoService.buscar(idProduto);
        validarProdutoDoEstabelecimento(estabelecimento, produto);
        validarCategoriaDoProduto(estabelecimento, produto, idCategoria);

        return produto;
    }

    private AdministradorWhatsappResultados.ResultadoAdmin montarRetornoProdutoLista(
        String whatsappAdmin,
        String chave,
        String corpo,
        Long idProduto,
        Long idCategoria,
        Integer offsetLista
    ) {
        int safeOffset = normalizarOffset(offsetLista);

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            chave,
            sup.msg().botoes(
                whatsappAdmin,
                sup.msg().trunc(corpo, 1024),
                List.of(
                    sup.btn(montarComandoVoltarProduto(idProduto, idCategoria, safeOffset), "🧾 Voltar ao produto"),
                    sup.btn(montarComandoVoltarListaProdutos(idCategoria, safeOffset), "📦 Voltar à lista")
                )
            )
        );
    }

    private void validarSessao(Long idSessao) {
        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
    }

    private int normalizarOffset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }

    private String montarComandoVoltarListaProdutos(Long idCategoria, Integer offsetLista) {
        return "COMANDO|ADMIN_CARDAPIO_CATEGORIA_PRODUTOS_MENU|" + idCategoria + "|" + normalizarOffset(offsetLista);
    }

    private String montarComandoVoltarProduto(Long idProduto, Long idCategoria, Integer offsetLista) {
        return "COMANDO|ADMIN_CARDAPIO_PRODUTO|" + idProduto + "|" + idCategoria + "|" + normalizarOffset(offsetLista);
    }

    private void validarCategoriaDoProduto(Estabelecimento estabelecimento, Produto produto, Long idCategoria) {
        CategoriaProduto categoria = categoriaProdutoService.buscar(idCategoria, estabelecimento.getId());
        validarCategoriaDoEstabelecimento(estabelecimento, categoria);

        if (produto == null || produto.getCategoria() == null || produto.getCategoria().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto não possui a categoria informada");
        }

        if (!Objects.equals(produto.getCategoria().getId(), idCategoria)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto não pertence à categoria informada");
        }
    }

    private void validarCategoriaDoEstabelecimento(Estabelecimento estabelecimento, CategoriaProduto categoria) {
        if (categoria == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada");
        }
        if (categoria.getEstabelecimento() == null || categoria.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria sem estabelecimento associado");
        }
        if (!Objects.equals(categoria.getEstabelecimento().getId(), estabelecimento.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria não pertence ao estabelecimento");
        }
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
        String valor = texto == null ? "" : texto.trim();

        if (!StringUtils.hasText(valor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }

        valor = valor.replace("R$", "")
            .replace("r$", "")
            .replace(" ", "")
            .replace(",", ".")
            .replaceAll("[^0-9.\\-+]", "");

        if (!StringUtils.hasText(valor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }

        try {
            return new BigDecimal(valor).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
        }
    }
}