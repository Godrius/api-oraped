package br.com.oraped.service.whatsapp.administrador;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.AbrangenciaEntrega;
import br.com.oraped.domain.geolocalizacao.Bairro;
import br.com.oraped.domain.geolocalizacao.TaxaEntregaBairro;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaItemListaWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.repository.geolocalizacao.TaxaEntregaBairroRepository;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.geolocalizacao.BairroService;
import br.com.oraped.service.geolocalizacao.EstabelecimentoBairroAtendidoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappAdminEntregaService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminEntregaService {

    private final EstabelecimentoService estabelecimentoService;
    private final SessaoWhatsappAdminEntregaService sessaoAdminEntregaService;
    private final BairroService bairroService;
    private final AdminWhatsappUiHelper sup;
    private final TaxaEntregaBairroRepository taxaEntregaBairroRepository;
    
    private final EstabelecimentoBairroAtendidoService estabelecimentoBairroAtendidoService;
    
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

        sessaoAdminEntregaService.marcarAguardandoCepEstabelecimento(idSessao);

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

	    if (!sessaoAdminEntregaService.isAguardandoCepEstabelecimento(idSessao)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando CEP do estabelecimento");
	    }

	    String cep8 = sup.normalizarCepDigitado(textoDigitado);

	    if (!StringUtils.hasText(cep8)) {
	        sessaoAdminEntregaService.marcarAguardandoCepEstabelecimento(idSessao);

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

	    System.out.println("[BAIRRO] base.id=" + (bairroBase == null ? null : bairroBase.getId()));
	    System.out.println("[BAIRRO] base.nome=" + (bairroBase == null ? null : bairroBase.getNome()));
	    System.out.println("[BAIRRO] base.cidade=" + (bairroBase == null ? null : bairroBase.getCidade()));
	    System.out.println("[BAIRRO] base.uf=" + (bairroBase == null ? null : bairroBase.getUf()));

	    estabelecimentoService.atualizarCepEBairroBase(
	        estabelecimento.getId(),
	        cep8,
	        bairroBase
	    );

	    sessaoAdminEntregaService.limparAguardandoCepEstabelecimento(idSessao);

	    List<Bairro> vizinhos = bairroService.listarVizinhosOrdenados(bairroBase.getId());
	    System.out.println("[BAIRRO] qtdVizinhos=" + (vizinhos == null ? 0 : vizinhos.size()));

	    if (vizinhos != null) {
	        for (Bairro b : vizinhos) {
	            System.out.println("[BAIRRO] vizinho.id=" + b.getId() + " nome=" + b.getNome());
	        }
	    }
	    
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

	    String labelBairros = qtdVizinhos == 1 ? "bairro" : "bairros";

	    String ok =
	        "✅ CEP salvo: *" + sup.formatarCepParaExibicao(cep8) + "*\n\n" +
	        "✅ Lista de bairros vizinhos criada: *" + qtdVizinhos + "* " + labelBairros + ".";

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
	        itens.add(sup.row("COMANDO|ADMIN_ENTREGAS_MENU", "⬅️ Voltar", "Taxas de entrega"));

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_entregas_bairros_sem_cep",
	            sup.msg().lista(whatsappAdmin, sup.msg().truncWord(cabecalho, 1024), "Bairros", "Bairros", itens)
	        );
	    }

	    Bairro bairroBase = bairroService.buscar(idBairroBase);
	    List<Bairro> vizinhos = bairroService.listarVizinhosOrdenados(bairroBase.getId());

	    // A taxa por bairro só faz sentido para bairros que o estabelecimento realmente atende.
	    List<Long> idsVizinhos = vizinhos.stream()
	        .map(Bairro::getId)
	        .toList();

	    Set<Long> idsAtendidos = estabelecimentoBairroAtendidoService.listarIdsAtendidos(
	        estabelecimento.getId(),
	        idsVizinhos
	    );

	    List<Bairro> bairrosAtendidos = vizinhos.stream()
	        .filter(b -> idsAtendidos.contains(b.getId()))
	        .toList();

	    if (bairrosAtendidos.isEmpty()) {
	        String cabecalho =
	            "🏘️ *Taxa por bairros*\n\n" +
	            "⚠️ Este estabelecimento ainda não possui *bairros atendidos* configurados.\n\n" +
	            "Primeiro, marque os bairros cobertos pelo serviço para depois definir as taxas.";

	        List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();
	        itens.add(sup.row(
	            "COMANDO|ADMIN_ENTREGAS_BAIRROS_ATENDIDOS",
	            "✅ Configurar bairros atendidos",
	            "Selecionar quais bairros da vizinhança são cobertos"
	        ));
	        itens.add(sup.row(
	            "COMANDO|ADMIN_ENTREGAS_MENU",
	            "⬅️ Voltar",
	            "Administrar entregas"
	        ));

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_entregas_bairros_sem_cobertura",
	            sup.msg().lista(
	                whatsappAdmin,
	                sup.msg().truncWord(cabecalho, 1024),
	                "Bairros",
	                "Bairros",
	                itens
	            )
	        );
	    }

	    int total = bairrosAtendidos.size();
	    int safeOffset = (offset == null || offset < 0) ? 0 : offset;
	    if (safeOffset >= total) {
	        safeOffset = 0;
	    }

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

	    List<Bairro> page = bairrosAtendidos.subList(safeOffset, endExclusive);

	    List<Long> idsPage = page.stream()
	        .map(Bairro::getId)
	        .toList();

	    var taxas = taxaEntregaBairroRepository
	        .findByEstabelecimentoAndBairros(estabelecimento.getId(), idsPage);

	    Map<Long, TaxaEntregaBairro> mapaTaxas = taxas.stream()
	        .collect(Collectors.toMap(
	            t -> t.getBairro().getId(),
	            t -> t
	        ));

	    int paginaAtual = (safeOffset / Math.max(1, pageSize)) + 1;
	    int paginasTotal = Math.max(1, (int) Math.ceil(total / (double) pageSize));

	    String cabecalho =
	        "🏘️ *Taxa por bairros*\n\n" +
	        "Loja em: *" + sup.msg().trunc(sup.msg().safe(bairroBase.getNome()), 60) + "*\n" +
	        "Bairros atendidos: *" + total + "*\n" +
	        "Página " + paginaAtual + " de " + paginasTotal + "\n\n" +
	        "Selecione um bairro atendido:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    for (Bairro b : page) {

	        TaxaEntregaBairro taxa = mapaTaxas.get(b.getId());

	        String nomeExibicao = sup.msg().trunc(
	            sup.msg().safe(b.getNome()),
	            24
	        );

	        String descricao;
	        if (taxa == null) {
	            descricao = "Sem taxa específica • usa taxa padrão";
	        } else if (Boolean.TRUE.equals(taxa.getIsento())) {
	            descricao = "Entrega gratuita";
	        } else {
	            descricao = "Taxa específica: " + sup.msg().formatarMoeda(taxa.getValor());
	        }

	        itens.add(sup.row(
	            "COMANDO|ADMIN_ENTREGAS_BAIRRO_SELECIONAR|" + b.getId() + "|" + safeOffset,
	            nomeExibicao,
	            descricao
	        ));
	    }

	    if (temProxima) {
	        itens.add(sup.row(
	            "COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|" + endExclusive,
	            "➡️ Próxima página",
	            "Ver mais bairros atendidos"
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
	        "COMANDO|ADMIN_ENTREGAS_MENU",
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

	    Bairro bairro = bairroService.buscar(idBairro);

	    TaxaEntregaBairro config = taxaEntregaBairroRepository
	        .findByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro)
	        .orElse(null);

	    String situacaoAtual;
	    if (config == null) {
	        situacaoAtual = "Usando taxa padrão";
	    } else if (Boolean.TRUE.equals(config.getIsento())) {
	        situacaoAtual = "Entrega gratuita";
	    } else {
	        situacaoAtual = "Taxa específica: " + sup.msg().formatarMoeda(config.getValor());
	    }

	    String cabecalho =
	        "📍 *Configurar taxa por bairro*\n\n" +
	            "Bairro: *" + sup.msg().trunc(sup.msg().safe(bairro.getNome()), 60) + "*\n" +
	            "Situação atual: *" + situacaoAtual + "*\n\n" +
	            "Escolha uma opção:";

	    List<MensagemInterativaItemListaWhatsappDTO> itens = new ArrayList<>();

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_TAXA_BAIRRO_DIGITAR|" + idBairro + "|" + (offsetLista == null ? 0 : Math.max(0, offsetLista)),
	        "Definir / alterar taxa",
	        "Informar um valor específico para esse bairro"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_BAIRRO_ISENTO|" + idBairro + "|" + (offsetLista == null ? 0 : Math.max(0, offsetLista)),
	        "Marcar entrega gratuita",
	        "Frete grátis para este bairro"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_BAIRRO_REMOVER|" + idBairro + "|" + (offsetLista == null ? 0 : Math.max(0, offsetLista)),
	        "Remover configuração",
	        "Voltar a usar somente a taxa padrão"
	    ));

	    itens.add(sup.row(
	        "COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|" + (offsetLista == null ? 0 : Math.max(0, offsetLista)),
	        "⬅️ Voltar para bairros",
	        "Retornar à lista"
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

	    String atualStr;
	    if (atualOpt.isEmpty()) {
	        atualStr = "Usando taxa padrão";
	    } else if (Boolean.TRUE.equals(atualOpt.get().getIsento())) {
	        atualStr = "Entrega gratuita";
	    } else {
	        atualStr = sup.msg().formatarMoeda(atualOpt.get().getValor());
	    }

	    sessaoAdminEntregaService.marcarAguardandoTaxaEntregaBairro(idSessao, idBairro, offsetLista);

	    StringBuilder sb = new StringBuilder();
	    sb.append("🚚 *Taxa de entrega por bairro*\n\n");
	    sb.append("Bairro: *").append(sup.msg().trunc(sup.msg().safe(b.getNome()), 60)).append("*\n");
	    sb.append("Situação atual: *").append(atualStr).append("*\n\n");
	    sb.append("Agora envie o *valor do frete*.\n");
	    sb.append("Exemplos:\n");
	    sb.append("- 5,00\n");
	    sb.append("- 10\n");
	    sb.append("- 12.50\n\n");
	    sb.append("Para *frete grátis* ou *remover a configuração*, use as opções do menu do bairro.");

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

	    if (!sessaoAdminEntregaService.isAguardandoTaxaEntregaBairro(idSessao)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando taxa por bairro");
	    }

	    Long idBairro = sessaoAdminEntregaService.getIdBairroTaxaEntrega(idSessao);

	    BigDecimal valor = parseValorMonetario(textoDigitado);

	    if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {

	        String corpo =
	            "Não consegui identificar um valor válido 😕\n\n" +
	            "Envie um valor *maior que zero*, por exemplo:\n" +
	            "- 5,00\n" +
	            "- 10\n" +
	            "- 12.50\n\n" +
	            "Para *frete grátis* ou *remover a configuração do bairro*, volte ao menu do bairro.";

	        return new AdministradorWhatsappResultados.ResultadoAdmin(
	            "admin_entregas_taxa_bairro_invalida",
	            sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
	        );
	    }

	    Bairro bairro = bairroService.buscar(idBairro);

	    TaxaEntregaBairro t = taxaEntregaBairroRepository
	        .findByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro)
	        .orElseGet(TaxaEntregaBairro::new);

	    t.setEstabelecimento(estabelecimento);
	    t.setBairro(bairro);
	    t.setValor(valor);
	    t.setIsento(false);

	    taxaEntregaBairroRepository.save(t);

	    sessaoAdminEntregaService.limparAguardandoTaxaEntregaBairro(idSessao);

	    String corpo =
	        "✅ Bairro *" + sup.msg().trunc(sup.msg().safe(bairro.getNome()), 60) +
	        "* atualizado com taxa de entrega de *" + sup.msg().formatarMoeda(valor) + "*.";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_taxa_bairro_salva",
	        sup.msg().texto(whatsappAdmin, sup.msg().trunc(corpo, 1024))
	    );
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

        sessaoAdminEntregaService.marcarAguardandoTaxaEntregaPadrao(idSessao, offsetVoltar);

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

	    if (!sessaoAdminEntregaService.isAguardandoTaxaEntregaPadrao(idSessao)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando taxa padrão");
	    }

	    BigDecimal valor = parseValorMonetario(textoDigitado);

	    if (valor == null) {

	        // mantém aguardando para o admin tentar novamente
	        sessaoAdminEntregaService.marcarAguardandoTaxaEntregaPadrao(
	            idSessao,
	            sessaoAdminEntregaService.getOffsetListaTaxaPadraoVoltar(idSessao)
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

	    sessaoAdminEntregaService.limparAguardandoTaxaEntregaPadrao(idSessao);

	    String ok = (novoValor == null)
	        ? "✅ Taxa padrão removida com sucesso."
	        : ("✅ Taxa padrão salva: *" + sup.msg().formatarMoeda(novoValor) + "*");

	    MensagemWhatsappSaidaDTO confirmacao = sup.msg().texto(whatsappAdmin, sup.msg().trunc(ok, 1024));

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_taxa_padrao_salva",
	        confirmacao
	    );
	}
    
    
    
    // ======================================================================
    // ISENÇÃO DE TAXA 
    // ======================================================================
    public AdministradorWhatsappResultados.ResultadoAdmin marcarBairroComoEntregaGratuita(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idBairro,
	    Integer offsetLista
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    if (idBairro == null) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
	    }

	    Bairro bairro = bairroService.buscar(idBairro);

	    TaxaEntregaBairro taxa = taxaEntregaBairroRepository
	        .findByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro)
	        .orElseGet(TaxaEntregaBairro::new);

	    taxa.setEstabelecimento(estabelecimento);
	    taxa.setBairro(bairro);
	    taxa.setValor(BigDecimal.ZERO.setScale(2));
	    taxa.setIsento(true);

	    taxaEntregaBairroRepository.save(taxa);

	    String corpo =
	        "✅ Bairro *" + sup.msg().trunc(sup.msg().safe(bairro.getNome()), 60) + "* marcado como *entrega gratuita*.";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_bairro_isento_salvo",
	        sup.msg().botoes(
	            whatsappAdmin,
	            sup.msg().trunc(corpo, 1024),
	            List.of(
	                sup.btn("COMANDO|ADMIN_ENTREGAS_BAIRRO_SELECIONAR|" + idBairro + "|" + (offsetLista == null ? 0 : Math.max(0, offsetLista)), "🔙 Voltar ao bairro"),
	                sup.btn("COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|" + (offsetLista == null ? 0 : Math.max(0, offsetLista)), "🏘️ Ver bairros")
	            )
	        )
	    );
	}

	public AdministradorWhatsappResultados.ResultadoAdmin removerConfiguracaoBairroEntrega(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idBairro,
	    Integer offsetLista
	) {

	    sup.validarBasico(estabelecimento, whatsappAdmin);

	    if (idBairro == null) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idBairro é obrigatório");
	    }

	    Bairro bairro = bairroService.buscar(idBairro);

	    taxaEntregaBairroRepository.deleteByEstabelecimentoIdAndBairroId(estabelecimento.getId(), idBairro);

	    String corpo =
	        "✅ Configuração específica removida para o bairro *" +
	            sup.msg().trunc(sup.msg().safe(bairro.getNome()), 60) +
	            "*.\n\nAgora esse bairro voltará a usar a *taxa padrão*.";

	    return new AdministradorWhatsappResultados.ResultadoAdmin(
	        "admin_entregas_bairro_config_removida",
	        sup.msg().botoes(
	            whatsappAdmin,
	            sup.msg().trunc(corpo, 1024),
	            List.of(
	                sup.btn("COMANDO|ADMIN_ENTREGAS_BAIRRO_SELECIONAR|" + idBairro + "|" + (offsetLista == null ? 0 : Math.max(0, offsetLista)), "🔙 Voltar ao bairro"),
	                sup.btn("COMANDO|ADMIN_ENTREGAS_BAIRROS_MENU|" + (offsetLista == null ? 0 : Math.max(0, offsetLista)), "🏘️ Ver bairros")
	            )
	        )
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
    
    
	 // ======================================================================
	 // BAIRROS ATENDIDOS (DIGITAÇÃO)
	 // ======================================================================
	
	 public AdministradorWhatsappResultados.ResultadoAdmin iniciarConfiguracaoBairrosAtendidosPorDigitacao(
	     Estabelecimento estabelecimento,
	     String whatsappAdmin,
	     Long idSessao
	 ) {
	
	     sup.validarBasico(estabelecimento, whatsappAdmin);
	
	     if (idSessao == null) {
	         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
	     }
	
	     if (estabelecimento.getAbrangenciaEntrega() != AbrangenciaEntrega.BAIRRO) {
	         throw new ResponseStatusException(
	             HttpStatus.BAD_REQUEST,
	             "Bairros atendidos só podem ser configurados para abrangência BAIRRO"
	         );
	     }
	
	     boolean temCep = StringUtils.hasText(estabelecimento.getCep());
	     boolean temBairroBase = estabelecimento.getBairro() != null && estabelecimento.getBairro().getId() != null;
	
	     if (!temCep || !temBairroBase) {
	         return new AdministradorWhatsappResultados.ResultadoAdmin(
	             "admin_bairros_atendidos_sem_cep",
	             sup.msg().texto(
	                 whatsappAdmin,
	                 sup.msg().trunc(
	                     "⚠️ Primeiro informe o *CEP da loja* para montar a vizinhança e liberar a configuração dos bairros atendidos.",
	                     1024
	                 )
	             )
	         );
	     }
	
	     sessaoAdminEntregaService.marcarAguardandoBairrosAtendidos(idSessao);
	
	     return new AdministradorWhatsappResultados.ResultadoAdmin(
	         "admin_bairros_atendidos_digitacao",
	         montarMensagemBairrosAtendidos(estabelecimento, whatsappAdmin)
	     );
	 }
	
	 public AdministradorWhatsappResultados.ResultadoAdmin concluirConfiguracaoBairrosAtendidosPorDigitacao(
	     Estabelecimento estabelecimento,
	     String whatsappAdmin,
	     Long idSessao,
	     String textoDigitado
	 ) {
	
	     sup.validarBasico(estabelecimento, whatsappAdmin);
	
	     if (idSessao == null) {
	         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idSessao é obrigatório");
	     }
	
	     if (!sessaoAdminEntregaService.isAguardandoBairrosAtendidos(idSessao)) {
	         throw new ResponseStatusException(HttpStatus.CONFLICT, "Sessão não está aguardando bairros atendidos");
	     }
	
	     if (estabelecimento.getAbrangenciaEntrega() != AbrangenciaEntrega.BAIRRO) {
	         throw new ResponseStatusException(
	             HttpStatus.BAD_REQUEST,
	             "Bairros atendidos só podem ser configurados para abrangência BAIRRO"
	         );
	     }
	
	     List<Bairro> vizinhanca = bairroService.listarVizinhosOrdenados(estabelecimento.getBairro().getId());
	
	     if (vizinhanca == null || vizinhanca.isEmpty()) {
	         return new AdministradorWhatsappResultados.ResultadoAdmin(
	             "admin_bairros_atendidos_sem_vizinhanca",
	             sup.msg().texto(
	                 whatsappAdmin,
	                 sup.msg().trunc(
	                     "⚠️ Não encontrei bairros na vizinhança da loja.\n\nInforme novamente o CEP para recalcular a lista.",
	                     1024
	                 )
	             )
	         );
	     }
	
	     List<AcaoCodigoBairroDTO> acoes = parseAcoesBairros(textoDigitado);
	
	     if (acoes.isEmpty()) {
	         return new AdministradorWhatsappResultados.ResultadoAdmin(
	             "admin_bairros_atendidos_entrada_invalida",
	             sup.msg().texto(
	                 whatsappAdmin,
	                 sup.msg().trunc(
	                     "Não consegui identificar códigos válidos 😕\n\n" +
	                     "Envie códigos separados por vírgula.\n" +
	                     "Exemplos:\n" +
	                     "- 1,3,5\n" +
	                     "- -2,-4\n" +
	                     "- 1,6,-3",
	                     1024
	                 )
	             )
	         );
	     }
	
	     List<String> adicionados = new ArrayList<>();
	     List<String> removidos = new ArrayList<>();
	     List<String> ignorados = new ArrayList<>();
	
	     for (AcaoCodigoBairroDTO acao : acoes) {
	
	         int codigo = acao.codigo();
	         int indice = codigo - 1;
	
	         if (indice < 0 || indice >= vizinhanca.size()) {
	             ignorados.add(String.valueOf(codigo));
	             continue;
	         }
	
	         Bairro bairro = vizinhanca.get(indice);
	
	         if (acao.remover()) {
	             boolean removeu = estabelecimentoBairroAtendidoService.removerBairroAtendido(estabelecimento, bairro.getId());
	             if (removeu) {
	                 removidos.add(bairro.getNome());
	             }
	             continue;
	         }
	
	         boolean adicionou = estabelecimentoBairroAtendidoService.adicionarBairroAtendido(estabelecimento, bairro.getId());
	         if (adicionou) {
	             adicionados.add(bairro.getNome());
	         }
	     }
	
	     StringBuilder sb = new StringBuilder();
	     sb.append("✅ Atualização concluída.\n\n");
	
	     if (!adicionados.isEmpty()) {
	         sb.append("*Adicionados:* ").append(String.join(", ", truncarListaNomes(adicionados))).append("\n");
	     }
	
	     if (!removidos.isEmpty()) {
	         sb.append("*Removidos:* ").append(String.join(", ", truncarListaNomes(removidos))).append("\n");
	     }
	
	     if (!ignorados.isEmpty()) {
	         sb.append("*Ignorados:* ").append(String.join(", ", ignorados)).append("\n");
	     }
	
	     if (adicionados.isEmpty() && removidos.isEmpty() && ignorados.isEmpty()) {
	         sb.append("Nenhuma alteração foi aplicada.\n");
	     }
	
	     sb.append("\n");
	     sb.append("Lista atualizada logo abaixo 👇");
	
	     MensagemWhatsappSaidaDTO confirmacao = sup.msg().texto(
	         whatsappAdmin,
	         sup.msg().trunc(sb.toString(), 1024)
	     );
	
	     
	     return new AdministradorWhatsappResultados.ResultadoAdmin(
    		    "admin_bairros_atendidos_atualizados",
    		    confirmacao
	      );
	 }
 
 
 
	 private MensagemWhatsappSaidaDTO montarMensagemBairrosAtendidos(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin
	 ) {

	    List<Bairro> vizinhanca = bairroService.listarVizinhosOrdenados(estabelecimento.getBairro().getId());

	    List<Long> idsVizinhos = vizinhanca.stream()
	        .map(Bairro::getId)
	        .toList();

	    Set<Long> idsAtendidos = estabelecimentoBairroAtendidoService.listarIdsAtendidos(
	        estabelecimento.getId(),
	        idsVizinhos
	    );

	    StringBuilder sb = new StringBuilder();
	    sb.append("✅ *Bairros atendidos*\n\n");
	    sb.append("Envie os códigos separados por vírgula para *adicionar* bairros.\n");
	    sb.append("Exemplo: *1,3,5*\n\n");
	    sb.append("Para *remover* bairros, use sinal de menos antes do código.\n");
	    sb.append("Exemplo: *-2,-4*\n\n");
	    sb.append("Você pode adicionar e remover bairros na mesma mensagem.\n");
	    sb.append("Exemplo: *1,6,-3*\n\n");
	    sb.append("Bairros da vizinhança:\n");

	    for (int i = 0; i < vizinhanca.size(); i++) {
	        Bairro bairro = vizinhanca.get(i);
	        boolean atendido = idsAtendidos.contains(bairro.getId());

	        sb.append(i + 1)
	            .append(" - ")
	            .append(sup.msg().safe(bairro.getNome()));

	        if (atendido) {
	            sb.append(" ✅");
	        }

	        sb.append("\n");
	    }

	    return sup.msg().texto(whatsappAdmin, sup.msg().trunc(sb.toString(), 4096));
	}

	private List<AcaoCodigoBairroDTO> parseAcoesBairros(String textoDigitado) {

	    if (!StringUtils.hasText(textoDigitado)) {
	        return List.of();
	    }

	    String[] partes = textoDigitado.split(",");
	    List<AcaoCodigoBairroDTO> acoes = new ArrayList<>();
	    Set<String> tokensJaProcessados = new HashSet<>();

	    for (String parte : partes) {

	        if (parte == null) {
	            continue;
	        }

	        String token = parte.trim().replaceAll("\\s+", "");
	        if (!StringUtils.hasText(token)) {
	            continue;
	        }

	        // Evita repetir a mesma operação na mesma mensagem.
	        if (!tokensJaProcessados.add(token)) {
	            continue;
	        }

	        Matcher matcher = Pattern.compile("^(-?)(\\d+)$").matcher(token);
	        if (!matcher.matches()) {
	            continue;
	        }

	        boolean remover = "-".equals(matcher.group(1));

	        try {
	            int codigo = Integer.parseInt(matcher.group(2));
	            if (codigo > 0) {
	                acoes.add(new AcaoCodigoBairroDTO(codigo, remover));
	            }
	        } catch (Exception e) {
	            // Ignora tokens inválidos sem interromper o restante da atualização.
	        }
	    }

	    return acoes;
	}

	private List<String> truncarListaNomes(List<String> nomes) {

	    if (nomes == null || nomes.isEmpty()) {
	        return List.of();
	    }

	    List<String> resultado = new ArrayList<>();
	    int limite = Math.min(nomes.size(), 6);

	    for (int i = 0; i < limite; i++) {
	        resultado.add(sup.msg().trunc(sup.msg().safe(nomes.get(i)), 40));
	    }

	    if (nomes.size() > limite) {
	        resultado.add("+" + (nomes.size() - limite) + " mais");
	    }

	    return resultado;
	}

	
	private record AcaoCodigoBairroDTO(int codigo, boolean remover) {}
}