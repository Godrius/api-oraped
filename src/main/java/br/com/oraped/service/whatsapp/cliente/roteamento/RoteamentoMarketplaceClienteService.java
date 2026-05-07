package br.com.oraped.service.whatsapp.cliente.roteamento;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.marketplace.Marketplace;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.dto.marktplace.CategoriaMarketplaceDisponivelDTO;
import br.com.oraped.dto.marktplace.EstabelecimentoDisponivelMarketplaceDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.EstabelecimentoService;
import br.com.oraped.service.geolocalizacao.GeolocalizacaoOrigemMarketplaceService;
import br.com.oraped.service.marketplace.MarketplaceCategoriaService;
import br.com.oraped.service.marketplace.MarketplaceEstabelecimentoService;
import br.com.oraped.service.marketplace.MarketplaceService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.cliente.MenuClienteService;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import br.com.oraped.service.whatsapp.orquestrador.marketplace.OrquestradorMarketplaceMensagemService;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappMarketplaceService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Rotear os comandos de marketplace executados pelo cliente no WhatsApp.
 *
 * Aplicação:
 * Usado pelo RoteamentoClienteService para delegar ações de discovery,
 * troca de estabelecimento, troca de categoria e troca de localização.
 *
 * Utilização:
 * Mantém o fluxo de marketplace isolado do fluxo tradicional de pedido,
 * reduzindo o tamanho e a responsabilidade do roteador principal do cliente.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoMarketplaceClienteService {

    private final SessaoAtendimentoWhatsappService sessaoService;
    private final SessaoWhatsappMarketplaceService sessaoMarketplaceService;

    private final EstabelecimentoService estabelecimentoService;
    private final MarketplaceService marketplaceService;
    private final MarketplaceCategoriaService marketplaceCategoriaService;
    private final MarketplaceEstabelecimentoService marketplaceEstabelecimentoService;
    private final GeolocalizacaoOrigemMarketplaceService geolocalizacaoOrigemMarketplaceService;

    private final OrquestradorParseService parse;
    private final OrquestradorMarketplaceMensagemService marketplaceMensagens;
    private final MenuClienteService menus;

    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotear(
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "MARKETPLACE_CATEGORIA":
                return tratarSelecaoCategoriaMarketplace(whatsappCliente, idSessao, cmd);

            case "MARKETPLACE_ESTABELECIMENTO":
                return tratarSelecaoEstabelecimentoMarketplace(whatsappCliente, idSessao, cmd);

            case "TROCAR_ESTABELECIMENTO_MARKETPLACE":
                return tratarTrocarEstabelecimentoMarketplace(whatsappCliente, idSessao);

            case "TROCAR_CATEGORIA_MARKETPLACE":
                return tratarTrocarCategoriaMarketplace(whatsappCliente, idSessao);

            case "TROCAR_LOCALIZACAO_MARKETPLACE":
                return tratarTrocarLocalizacaoMarketplace(whatsappCliente, idSessao);

            default:
                return new RoteamentoResultado(
                    "marketplace_comando_desconhecido",
                    msg.texto(
                        whatsappCliente,
                        "⚠️ Não consegui identificar a ação do marketplace.\n\nTente novamente."
                    )
                );
        }
    }

    private RoteamentoResultado tratarSelecaoCategoriaMarketplace(
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        Long idCategoriaMarketplace = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoriaMarketplace");

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        if (sessao == null || sessao.getIdMarketplace() == null) {
            return new RoteamentoResultado(
                "marketplace_sessao_invalida",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar o marketplace desta conversa.\n\nTente iniciar novamente."
                )
            );
        }

        Marketplace marketplace = marketplaceService.buscarPorId(sessao.getIdMarketplace());

        CategoriaMarketplaceDisponivelDTO categoria = marketplaceCategoriaService.buscarCategoriaDisponivel(
            idCategoriaMarketplace,
            marketplace,
            sessao
        );

        sessaoMarketplaceService.salvarCategoriaMarketplaceSelecionada(idSessao, categoria.getId());

        SessaoAtendimentoWhatsapp sessaoAtualizada = sessaoService.buscarPorId(idSessao);

        List<EstabelecimentoDisponivelMarketplaceDTO> estabelecimentos =
            marketplaceEstabelecimentoService.listarEstabelecimentosDisponiveis(
                marketplace,
                sessaoAtualizada
            );

        MensagemWhatsappSaidaDTO mensagem = marketplaceMensagens.montarMenuEstabelecimentos(
            whatsappCliente,
            categoria,
            estabelecimentos
        );

        return new RoteamentoResultado(
            "marketplace_lista_estabelecimentos",
            mensagem
        );
    }

    private RoteamentoResultado tratarSelecaoEstabelecimentoMarketplace(
        String whatsappCliente,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        Long idEstabelecimento = parse.parseLongObrigatorio(cmd.getParte(2), "idEstabelecimento");

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        if (sessao == null || sessao.getIdMarketplace() == null) {
            return new RoteamentoResultado(
                "marketplace_sessao_invalida",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar o marketplace desta conversa.\n\nTente iniciar novamente."
                )
            );
        }

        Estabelecimento estabelecimentoSelecionado = estabelecimentoService.buscar(idEstabelecimento);

        if (estabelecimentoSelecionado == null || !estabelecimentoSelecionado.isAtivo()) {
            return new RoteamentoResultado(
                "marketplace_estabelecimento_invalido",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar um estabelecimento disponível para continuar.\n\nTente escolher outro."
                )
            );
        }

        // Ao escolher uma loja, a sessão passa a operar no fluxo tradicional do estabelecimento.
        sessaoMarketplaceService.vincularEstabelecimentoAoAtendimentoMarketplace(
            idSessao,
            estabelecimentoSelecionado.getId()
        );

        Estabelecimento estabelecimentoAtualizado = estabelecimentoService.buscar(
            estabelecimentoSelecionado.getId()
        );

        MensagemWhatsappSaidaDTO avisoConexao = msg.texto(
            whatsappCliente,
            "✅ Agora você está falando com *" + msg.safe(estabelecimentoAtualizado.getNome()) + "*."
        );

        MensagemWhatsappSaidaDTO menuPrincipal = menus.montarMenuPrincipalSemSaudacao(
            estabelecimentoAtualizado,
            whatsappCliente,
            idSessao
        );

        return new RoteamentoResultado(
            "marketplace_estabelecimento_selecionado",
            avisoConexao,
            List.of(menuPrincipal)
        );
    }

    private RoteamentoResultado tratarTrocarEstabelecimentoMarketplace(
        String whatsappCliente,
        Long idSessao
    ) {

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        if (sessao == null || sessao.getIdMarketplace() == null || sessao.getIdCategoriaMarketplace() == null) {
            return new RoteamentoResultado(
                "marketplace_troca_estabelecimento_invalida",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar a categoria atual para trocar de loja.\n\nVolte ao marketplace e escolha uma categoria novamente."
                )
            );
        }

        Long idMarketplace = sessao.getIdMarketplace();
        Long idCategoriaMarketplace = sessao.getIdCategoriaMarketplace();

        // Mantém localização e categoria, limpando apenas loja/pedido atual.
        sessaoMarketplaceService.trocarEstabelecimentoMarketplace(idSessao, idMarketplace);

        SessaoAtendimentoWhatsapp sessaoAtualizada = sessaoService.buscarPorId(idSessao);
        Marketplace marketplace = marketplaceService.buscarPorId(idMarketplace);

        CategoriaMarketplaceDisponivelDTO categoria = marketplaceCategoriaService.buscarCategoriaDisponivel(
            idCategoriaMarketplace,
            marketplace,
            sessaoAtualizada
        );

        List<EstabelecimentoDisponivelMarketplaceDTO> estabelecimentos =
            marketplaceEstabelecimentoService.listarEstabelecimentosDisponiveis(
                marketplace,
                sessaoAtualizada
            );

        MensagemWhatsappSaidaDTO mensagem = marketplaceMensagens.montarMenuEstabelecimentos(
            whatsappCliente,
            categoria,
            estabelecimentos
        );

        return new RoteamentoResultado(
            "marketplace_trocar_estabelecimento",
            mensagem
        );
    }

    private RoteamentoResultado tratarTrocarCategoriaMarketplace(
        String whatsappCliente,
        Long idSessao
    ) {

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        if (sessao == null || sessao.getIdMarketplace() == null) {
            return new RoteamentoResultado(
                "marketplace_troca_categoria_invalida",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar o marketplace desta conversa.\n\nTente iniciar novamente."
                )
            );
        }

        Long idMarketplace = sessao.getIdMarketplace();

        // Mantém localização e remove loja/categoria para reabrir a árvore de discovery.
        sessaoMarketplaceService.trocarCategoriaMarketplace(idSessao, idMarketplace);

        SessaoAtendimentoWhatsapp sessaoAtualizada = sessaoService.buscarPorId(idSessao);
        Marketplace marketplace = marketplaceService.buscarPorId(idMarketplace);

        EnderecoResolvidoDTO enderecoResolvido = geolocalizacaoOrigemMarketplaceService.resolverOrigemCliente(
            sessaoAtualizada.getLatitudeOrigemCliente(),
            sessaoAtualizada.getLongitudeOrigemCliente()
        );

        List<CategoriaMarketplaceDisponivelDTO> categorias = marketplaceCategoriaService.listarCategoriasDisponiveis(
            marketplace,
            sessaoAtualizada
        );

        MensagemWhatsappSaidaDTO mensagem = marketplaceMensagens.montarMenuCategoriasComLocalizacaoExistente(
            whatsappCliente,
            marketplace,
            enderecoResolvido,
            categorias
        );

        return new RoteamentoResultado(
            "marketplace_trocar_categoria",
            mensagem
        );
    }

    private RoteamentoResultado tratarTrocarLocalizacaoMarketplace(
        String whatsappCliente,
        Long idSessao
    ) {

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        if (sessao == null || sessao.getIdMarketplace() == null) {
            return new RoteamentoResultado(
                "marketplace_troca_localizacao_invalida",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar o marketplace desta conversa.\n\nTente iniciar novamente."
                )
            );
        }

        Long idMarketplace = sessao.getIdMarketplace();
        Marketplace marketplace = marketplaceService.buscarPorId(idMarketplace);

        // Reinicia o discovery para exigir nova localização ou CEP.
        sessaoMarketplaceService.trocarLocalizacaoMarketplace(idSessao, idMarketplace);

        MensagemWhatsappSaidaDTO mensagem = msg.texto(
            whatsappCliente,
            "📍 Tudo bem! Vamos trocar sua localização.\n\n" +
                "Você pode:\n" +
                "- Compartilhar sua localização atual pelo WhatsApp 📍\n" +
                "ou\n" +
                "- Digitar o novo CEP\n\n" +
                "Assim eu atualizo as opções disponíveis no marketplace " + msg.safe(marketplace.getNome()) + "."
        );

        return new RoteamentoResultado(
            "marketplace_trocar_localizacao",
            mensagem
        );
    }
}