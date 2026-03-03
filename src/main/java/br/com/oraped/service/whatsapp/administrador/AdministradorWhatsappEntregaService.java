package br.com.oraped.service.whatsapp.administrador;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.geolocalizacao.TaxaEntregaBairro;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.repository.geolocalizacao.TaxaEntregaBairroRepository;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.geolocalizacao.BairroService;
import br.com.oraped.service.whatsapp.SessaoAtendimentoWhatsappService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdministradorWhatsappEntregaService {

    private final EstabelecimentoService estabelecimentoService;
    private final SessaoAtendimentoWhatsappService sessaoService;
    private final BairroService bairroService;
    private final AdministradorWhatsappSupport sup;
    private final TaxaEntregaBairroRepository taxaEntregaBairroRepository;
    
    
    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuCepLoja(Estabelecimento estabelecimento, String whatsappAdmin) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        boolean temCep = StringUtils.hasText(estabelecimento.getCep());

        String cabecalho =
            "📍 *CEP do estabelecimento*\n\n" +
                (temCep
                    ? ("CEP atual: *" + sup.formatarCepParaExibicao(estabelecimento.getCep()) + "*\n\n")
                    : ""
                ) +
                "Para configurar *taxas por bairro*, precisamos do CEP da loja.\n\n" +
                "Escolha uma opção:";

        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

        itens.add(sup.row(
            "COMANDO|ADMIN_ENTREGAS_CEP_DIGITAR",
            temCep ? "Alterar CEP" : "Informar CEP agora",
            "Enviar 1 mensagem com o CEP (somente números)"
        ));

        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_MENU", "⬅️ Voltar", "Administrar entregas"));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_entregas_cep_menu",
            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "CEP", "CEP", itens)
        );
    }

    
    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroCepLojaPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }

        sessaoService.marcarAguardandoCepEstabelecimento(idSessao);

        String atual = StringUtils.hasText(estabelecimento.getCep())
            ? ("CEP atual: *" + sup.formatarCepParaExibicao(estabelecimento.getCep()) + "*\n\n")
            : "";

        String corpo =
            "📍 *CEP da Loja*\n\n" +
                atual +
                "Agora envie apenas o *CEP da loja* (somente números).\n\n" +
                "Exemplos:\n" +
                "- 01001000\n" +
                "- 22775033";

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_entregas_cep_digitacao",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroCepLojaPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String textoDigitado
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    if (idSessao == null) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
	    }

	    if (!sessaoService.isAguardandoCepEstabelecimento(idSessao)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando CEP do estabelecimento");
	    }

	    String cep8 = sup.normalizarCepDigitado(textoDigitado);

	    if (!StringUtils.hasText(cep8)) {
	        sessaoService.marcarAguardandoCepEstabelecimento(idSessao);

	        String corpo =
	            "Não consegui identificar um CEP válido 😕\n\n" +
	            "Envie o CEP com 8 dígitos, podendo usar traço.\n\n" +
	            "Exemplos:\n" +
	            "- 01001000\n" +
	            "- 01001-000";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_entregas_cep_invalido",
	            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
	        );
	    }

	    Bairro bairroBase = bairroService.setupBairroBasePorCep(cep8);

	    estabelecimentoService.atualizarCepEBairroBase(
	        estabelecimento.getId(),
	        cep8,
	        bairroBase
	    );

	    sessaoService.limparAguardandoCepEstabelecimento(idSessao);

	    List<Bairro> vizinhos = bairroService.listarVizinhosOrdenados(bairroBase.getId());
	    int qtdVizinhos = (vizinhos == null ? 0 : vizinhos.size());

	    if (qtdVizinhos <= 0) {

	        String aviso =
	            "✅ CEP salvo: *" + sup.formatarCepParaExibicao(cep8) + "*\n\n" +
	            "⚠️ Não consegui montar automaticamente os *bairros próximos* agora.\n" +
	            "Isso pode acontecer quando o serviço externo não retorna coordenadas.\n\n" +
	            "Tente novamente informando o CEP mais tarde.";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_entregas_cep_salvo_sem_vizinhos",
	            sup.msg().texto(whatsappAdmin, sup.msg().trunc(aviso, 1024))
	        );
	    }

	    String ok =
	        "✅ CEP salvo: *" + sup.formatarCepParaExibicao(cep8) + "*\n\n" +
	        "✅ Lista de bairros vizinhos criada: *" + qtdVizinhos + "* bairros.";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_cep_salvo",
	        sup.msg().texto(whatsappAdmin, sup.msg().trunc(ok, 1024))
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuTaxasEntrega(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    // Recarrega para garantir dados atuais (CEP/bairro/taxa padrão)
	    Estabelecimento atual = estabelecimentoService.buscar(estabelecimento.getId());

	    boolean temCep = StringUtils.hasText(atual.getCep());
	    boolean temBairroBase = (atual.getBairro() != null && atual.getBairro().getId() != null);

	    if (!temCep || !temBairroBase) {

	        String cabecalho =
	            "🚚 *Taxas de entrega*\n\n" +
	            "⚠️ Para configurar taxa por bairro, primeiro informe o *CEP do estabelecimento*.";

	        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();
	        itens.add(sup.row(
	            "COMANDO|ADMIN_ENTREGAS_CEP_MENU",
	            "Configurar CEP da loja",
	            "Informar o CEP para montar bairros próximos"
	        ));
	        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_MENU", "⬅️ Voltar", "Administrar entregas"));

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_entregas_taxas_sem_cep",
	            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Taxas", "Taxas", itens)
	        );
	    }

	    // ⚠️ Ajuste aqui se o nome do getter/campo for outro
	    BigDecimal taxaPadrao = atual.getTaxaEntregaPadrao();
	    BigDecimal taxaParaExibir = (taxaPadrao == null ? BigDecimal.ZERO : taxaPadrao);
	    String taxaStr = sup.msg().formatarMoeda(taxaParaExibir);

	    String cabecalho =
	        "🚚 *Taxas de entrega*\n\n" +
	        "Taxa padrão atual: *" + taxaStr + "*\n\n" +
	        "Configure a taxa padrão e/ou por bairro:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_TAXA_PADRAO_MENU",
	        "Taxa padrão - " + taxaStr,
	        "Valor aplicado quando não houver taxa específica para o bairro"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|0",
	        "Taxa por bairros",
	        "Selecionar bairro e definir o valor do frete"
	    ));

	    itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_MENU", "⬅️ Voltar", "Administrar entregas"));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_taxas_menu",
	        sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Taxas", "Taxas", itens)
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuTaxaPadrao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    // Recarrega para garantir valor atual (caso o objeto venha “velho”)
	    Estabelecimento atual = estabelecimentoService.buscar(estabelecimento.getId());

	    // ⚠️ Ajuste aqui se o nome do getter/campo for outro
	    BigDecimal taxaPadrao = atual.getTaxaEntregaPadrao();

	    BigDecimal taxaParaExibir = (taxaPadrao == null ? BigDecimal.ZERO : taxaPadrao);
	    String taxaStr = sup.msg().formatarMoeda(taxaParaExibir);

	    String cabecalho =
	        "💲 *Taxa padrão*\n\n" +
	        "Taxa atual: *" + taxaStr + "*\n\n" +
	        "Esse valor será usado quando não houver taxa específica para o bairro.\n\n" +
	        "Escolha uma opção:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_TAXA_PADRAO_DIGITAR",
	        "Definir / alterar taxa padrão",
	        "Atual: " + taxaStr + " (envie 0 para remover)"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_TAXAS_MENU",
	        "⬅️ Voltar",
	        "Taxas de entrega"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_taxa_padrao_menu",
	        sup.msg().lista(
	            whatsappAdmin,
	            sup.msg().truncWord(cabecalho, 1024),
	            "Taxa",
	            "Taxa",
	            itens
	        )
	    );
	}

    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuTaxaPorBairros(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Integer offset
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    boolean temCep = StringUtils.hasText(estabelecimento.getCep());
	    Long idBairroBase = (estabelecimento.getBairro() == null ? null : estabelecimento.getBairro().getId());
	    boolean temBairroBase = (idBairroBase != null);

	    if (!temCep || !temBairroBase) {
	        String cabecalho =
	            "🏘️ *Taxa por bairros*\n\n" +
	            "⚠️ A lista de bairros ainda não foi criada.\n\n" +
	            "Primeiro, informe o *CEP do estabelecimento*.";

	        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();
	        itens.add(sup.row(
	            "COMANDO|ADMIN_ENTREGAS_CEP_MENU",
	            "Configurar CEP da loja",
	            "Informar o CEP para montar bairros próximos"
	        ));
	        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_TAXAS_MENU", "⬅️ Voltar", "Taxas de entrega"));

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_entregas_bairros_sem_cep",
	            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Bairros", "Bairros", itens)
	        );
	    }

	    Bairro bairroBase = bairroService.buscar(idBairroBase);

	    List<Bairro> vizinhos = bairroService.listarVizinhosOrdenados(bairroBase.getId());

	    int total = vizinhos.size();
	    int safeOffset = (offset == null || offset < 0) ? 0 : offset;
	    if (safeOffset >= total) safeOffset = 0;

	    final int MAX_ROWS = 10;

	    boolean temAnterior = safeOffset > 0;

	    int pageSize = temAnterior ? 7 : 8;
	    int endExclusive = Math.min(safeOffset + pageSize, total);
	    boolean temProxima = endExclusive < total;

	    if (!temProxima) {
	        pageSize = temAnterior ? 8 : 9;
	        endExclusive = Math.min(safeOffset + pageSize, total);
	        temProxima = endExclusive < total;
	    }

	    List<Bairro> page = vizinhos.subList(safeOffset, endExclusive);

	    // ========================
	    // 🔹 Busca taxas em batch
	    // ========================

	    List<Long> idsPage = page.stream()
	        .map(Bairro::getId)
	        .toList();

	    var taxas = taxaEntregaBairroRepository
	        .findByEstabelecimentoAndBairros(estabelecimento.getId(), idsPage);

	    Map<Long, BigDecimal> mapaTaxas = taxas.stream()
	        .collect(Collectors.toMap(
	            t -> t.getBairro().getId(),
	            t -> t.getValor()
	        ));

	    int paginaAtual = (safeOffset / Math.max(1, pageSize)) + 1;
	    int paginasTotal = Math.max(1, (int) Math.ceil(total / (double) pageSize));

	    String cabecalho =
	        "🏘️ *Taxa por bairros*\n\n" +
	        "Loja em: *" + sup.msg().trunc(sup.msg().safe(bairroBase.getNome()), 60) + "*\n" +
	        "Página " + paginaAtual + " de " + paginasTotal + "\n\n" +
	        "Selecione um bairro:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    for (Bairro b : page) {

	        BigDecimal taxa = mapaTaxas.get(b.getId());

	        String nomeExibicao = sup.msg().trunc(
	            sup.msg().safe(b.getNome()),
	            18
	        );

	        if (taxa != null) {
	            nomeExibicao += " - " + sup.msg().formatarMoeda(taxa);
	        }

	        itens.add(sup.row(
	            "COMANDO|ADMIN_ENTREGAS_BAIRRO_SELECIONAR|" + b.getId() + "|" + safeOffset,
	            nomeExibicao,
	            "Definir taxa de entrega"
	        ));
	    }

	    if (temProxima) {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|" + endExclusive,
	            "➡️ Próxima página",
	            "Ver mais bairros"
	        ));
	    }

	    if (temAnterior) {
	        int prevOffset = Math.max(0, safeOffset - pageSize);
	        itens.add(sup.row(
	            "COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|" + prevOffset,
	            "⬅️ Página anterior",
	            "Voltar"
	        ));
	    }

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_TAXAS_MENU",
	        "⬅️ Voltar",
	        "Taxas de entrega"
	    ));

	    if (itens.size() > MAX_ROWS) {
	        itens = itens.subList(0, MAX_ROWS);
	    }

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_bairros_menu",
	        sup.msg().lista(
	            whatsappAdmin,
	            sup.msg().truncWord(cabecalho, 1024),
	            "Bairros",
	            "Bairros",
	            itens
	        )
	    );
	}
    
    
    public AdministradorWhatsappResultados.ResultadoAdmin montarMenuBairroEntregaSelecionado(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idBairro,
	    Integer offsetLista
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    if (idBairro == null) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
	    }

	    String cabecalho =
	        "📍 *Configurar taxa por bairro*\n\n" +
	            "Bairro selecionado: *#" + idBairro + "*\n\n" +
	            "Neste próximo passo vamos:\n" +
	            "- mostrar a taxa atual (se houver)\n" +
	            "- permitir digitar a nova taxa\n\n" +
	            "Escolha uma opção:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|" + (offsetLista == null ? 0 : Math.max(0, offsetLista)),
	        "⬅️ Voltar para bairros",
	        "Retornar à lista"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_TAXAS_MENU",
	        "⬅️ Voltar para taxas",
	        "Taxas de entrega"
	    ));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_bairro_selecionado",
	        sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Bairro", "Bairro", itens)
	    );
	}
    
    
 // ======================================================================
    // TAXA POR BAIRRO (DIGITAÇÃO)
    // ======================================================================

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroTaxaEntregaBairroPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Long idBairro,
        Integer offsetLista
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }
        if (idBairro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
        }

        Bairro b = bairroService.buscar(idBairro);

        Optional<TaxaEntregaBairro> atualOpt =
            taxaEntregaBairroRepository.findByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro);

        String atualStr = atualOpt
            .map(TaxaEntregaBairro::getValor)
            .map(sup.msg()::formatarMoeda)
            .orElse(null);

        sessaoService.marcarAguardandoTaxaEntregaBairro(idSessao, idBairro, offsetLista);

        StringBuilder sb = new StringBuilder();
        sb.append("🚚 *Taxa de entrega por bairro*\n\n");
        sb.append("Bairro: *").append(sup.msg().trunc(sup.msg().safe(b.getNome()), 60)).append("*\n");

        if (StringUtils.hasText(atualStr)) {
            sb.append("Taxa atual: *").append(atualStr).append("*\n");
        } else {
            sb.append("Taxa atual: *(não definida)*\n");
        }

        sb.append("\n");
        sb.append("Agora envie o *valor do frete*.\n");
        sb.append("Exemplos:\n");
        sb.append("- 5,00\n");
        sb.append("- 10\n");
        sb.append("- 12.50\n\n");
        sb.append("Para *remover* a taxa do bairro, envie *0*.");

        MensagemWhatsappSaidaDTO msg = sup.msg().texto(whatsappAdmin, sup.msg().trunc(sb.toString(), 1024));

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_entregas_taxa_bairro_digitacao",
            msg
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroTaxaEntregaBairroPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        String textoDigitado
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }

        if (!sessaoService.isAguardandoTaxaEntregaBairro(idSessao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando taxa por bairro");
        }

        Long idBairro = sessaoService.getIdBairroTaxaEntrega(idSessao);
        Integer offsetLista = sessaoService.getOffsetListaTaxaEntregaBairro(idSessao);

        BigDecimal valor = parseValorMonetario(textoDigitado);

        if (valor == null) {

            String corpo =
                "Não consegui identificar um valor válido 😕\n\n" +
                "Envie um número, por exemplo:\n" +
                "- 5,00\n" +
                "- 10\n" +
                "- 12.50\n\n" +
                "Para remover a taxa, envie *0*.";

            return new AdministradorWhatsappResultados.ResultadoAdmin(
                "admin_entregas_taxa_bairro_invalida",
                sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
            );
        }

        if (valor.compareTo(BigDecimal.ZERO) == 0) {
            taxaEntregaBairroRepository.deleteByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro);
        } else {

            TaxaEntregaBairro t = taxaEntregaBairroRepository
                .findByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro)
                .orElseGet(TaxaEntregaBairro::new);

            t.setEstabelecimento(estabelecimento);
            t.setBairro(bairroService.buscar(idBairro));
            t.setValor(valor);

            taxaEntregaBairroRepository.save(t);
        }

        sessaoService.limparAguardandoTaxaEntregaBairro(idSessao);

        //Depois de salvar, voltar direto pra lista de bairros (menu de taxa por bairros)
        return montarMenuTaxaPorBairros(estabelecimento, whatsappAdmin, offsetLista);
    }

    
    // ======================================================================
    // TAXA PADRÃO (DIGITAÇÃO)
    // ======================================================================

    public AdministradorWhatsappResultados.ResultadoAdmin iniciarCadastroTaxaEntregaPadraoPorDigitacao(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        Integer offsetVoltar
    ) {

        sup.validarBasico(estabelecimento, whatsappAdmin);

        if (idSessao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
        }

        sessaoService.marcarAguardandoTaxaEntregaPadrao(idSessao, offsetVoltar);

        String atualStr = (estabelecimento.getTaxaEntregaPadrao() == null)
            ? null
            : sup.msg().formatarMoeda(estabelecimento.getTaxaEntregaPadrao());

        StringBuilder sb = new StringBuilder();
        sb.append("💲 *Taxa padrão de entrega*\n\n");

        if (StringUtils.hasText(atualStr)) {
            sb.append("Taxa atual: *").append(atualStr).append("*\n");
        } else {
            sb.append("Taxa atual: *(não definida)*\n");
        }

        sb.append("\n");
        sb.append("Agora envie o *valor da taxa padrão*.\n");
        sb.append("Exemplos:\n");
        sb.append("- 5,00\n");
        sb.append("- 10\n");
        sb.append("- 12.50\n\n");
        sb.append("Para *remover* a taxa padrão, envie *0*.");

        return new AdministradorWhatsappResultados.ResultadoAdmin(
            "admin_entregas_taxa_padrao_digitacao",
            sup.msg().texto(whatsappAdmin, sup.msg().trunc(sb.toString(), 1024))
        );
    }

    public AdministradorWhatsappResultados.ResultadoAdmin concluirCadastroTaxaEntregaPadraoPorDigitacao(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    String textoDigitado
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    if (idSessao == null) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
	    }

	    if (!sessaoService.isAguardandoTaxaEntregaPadrao(idSessao)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando taxa padrão");
	    }

	    BigDecimal valor = parseValorMonetario(textoDigitado);

	    if (valor == null) {

	        // mantém aguardando para o admin tentar novamente
	        sessaoService.marcarAguardandoTaxaEntregaPadrao(
	            idSessao,
	            sessaoService.getOffsetListaTaxaPadraoVoltar(idSessao)
	        );

	        String corpo =
	            "Não consegui identificar um valor válido 😕\n\n" +
	            "Envie um número, por exemplo:\n" +
	            "- 5,00\n" +
	            "- 10\n" +
	            "- 12.50\n\n" +
	            "Para remover a taxa padrão, envie *0*.";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_entregas_taxa_padrao_invalida",
	            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
	        );
	    }

	    // 0 => remove
	    BigDecimal novoValor = (valor.compareTo(BigDecimal.ZERO) == 0) ? null : valor;

	    estabelecimentoService.atualizarTaxaEntregaPadrao(estabelecimento.getId(), novoValor);

	    sessaoService.limparAguardandoTaxaEntregaPadrao(idSessao);

	    String ok = (novoValor == null)
	        ? "✅ Taxa padrão removida com sucesso."
	        : ("✅ Taxa padrão salva: *" + sup.msg().formatarMoeda(novoValor) + "*");

	    MensagemWhatsappSaidaDTO confirmacao = sup.msg().texto(whatsappAdmin, sup.msg().trunc(ok, 1024));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_taxa_padrao_salva",
	        confirmacao
	    );
	}
    
    private BigDecimal parseValorMonetario(String texto) {

        if (!StringUtils.hasText(texto)) return null;

        String v = texto.trim();

        // remove "R$", espaços etc
        v = v.replace("R$", "").replace("r$", "").trim();

        // mantém só dígitos, vírgula e ponto
        v = v.replaceAll("[^0-9,\\.]", "");

        if (!StringUtils.hasText(v)) return null;

        // Se tem vírgula e ponto, assume ponto como milhar e vírgula como decimal
        if (v.contains(",") && v.contains(".")) {
            v = v.replace(".", "");
            v = v.replace(",", ".");
        } else if (v.contains(",")) {
            v = v.replace(",", ".");
        }

        try {
            BigDecimal bd = new BigDecimal(v);

            // regra simples: não permitir negativo
            if (bd.compareTo(BigDecimal.ZERO) < 0) return null;

            // normaliza para 2 casas (sem forçar arredondamento complexo)
            return bd.setScale(2, java.math.RoundingMode.HALF_UP);

        } catch (Exception ex) {
            return null;
        }
    }
}