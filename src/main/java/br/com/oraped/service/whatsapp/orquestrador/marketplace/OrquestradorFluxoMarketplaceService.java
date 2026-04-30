package br.com.oraped.service.whatsapp.orquestrador.marketplace;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.marketplace.Marketplace;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.dto.geolocalizacao.EnderecoResolvidoDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.geolocalizacao.GeolocalizacaoProvider;
import br.com.oraped.service.marketplace.MarketplaceCategoriaService;
import br.com.oraped.service.marketplace.MarketplaceService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.sessao.SessaoAtendimentoWhatsappService;
import br.com.oraped.service.whatsapp.sessao.SessaoWhatsappMarketplaceService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Tratar os passos conversacionais específicos do marketplace fora do roteamento principal,
 * incluindo o refinamento da localização do cliente por CEP.
 *
 * Aplicação:
 * Utilizado quando o cliente precisa ajustar a região de atendimento após não encontrar
 * opções disponíveis com base na localização atual.
 *
 * Utilização:
 * Deve ser acionado pelo fluxo de texto livre quando a sessão estiver aguardando
 * um CEP para refinar a localização do marketplace.
 */
@Service
@RequiredArgsConstructor
public class OrquestradorFluxoMarketplaceService {

	private final SessaoAtendimentoWhatsappService sessaoService;
	private final SessaoWhatsappMarketplaceService sessaoMarketplaceService;
	
    private final MarketplaceService marketplaceService;
    private final MarketplaceCategoriaService marketplaceCategoriaService;
    private final OrquestradorMarketplaceMensagemService marketplaceMensagemService;
    private final GeolocalizacaoProvider geolocalizacaoProvider;
    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado tratarRefinamentoLocalizacaoPorCep(
        String whatsappCliente,
        Long idSessao,
        String textoDigitado
    ) {

        String cep = normalizarCep(textoDigitado);

        if (!StringUtils.hasText(cep) || cep.length() != 8) {
            return new RoteamentoResultado(
                "marketplace_cep_refinamento_invalido",
                msg.texto(
                    whatsappCliente,
                    "Não consegui entender o CEP informado.\n\n" +
                        "Por favor, digite um CEP com 8 números.\n\n" +
                        "Exemplo: 24220110"
                )
            );
        }

        EnderecoResolvidoDTO enderecoResolvido = geolocalizacaoProvider.resolverCep(cep);

        if (enderecoResolvido == null
            || enderecoResolvido.getLatitude() == null
            || enderecoResolvido.getLongitude() == null
        ) {
            return new RoteamentoResultado(
                "marketplace_cep_refinamento_nao_resolvido",
                msg.texto(
                    whatsappCliente,
                    "Não consegui localizar esse CEP com precisão.\n\n" +
                        "Por favor, tente outro CEP."
                )
            );
        }

        SessaoAtendimentoWhatsapp sessao = sessaoService.buscarPorId(idSessao);

        if (sessao.getIdMarketplace() == null) {
            return new RoteamentoResultado(
                "marketplace_sessao_invalida",
                msg.texto(
                    whatsappCliente,
                    "⚠️ Não consegui identificar o marketplace desta conversa.\n\nTente iniciar novamente."
                )
            );
        }

        Marketplace marketplace = marketplaceService.buscarPorId(sessao.getIdMarketplace());

        // O CEP refinado passa a ser a nova referência geográfica da sessão.
        sessaoMarketplaceService.salvarLocalizacaoOrigemMarketplace(
            idSessao,
            enderecoResolvido.getLatitude(),
            enderecoResolvido.getLongitude()
        );

        sessaoMarketplaceService.limparAguardandoCepRefinarMarketplace(idSessao);

        SessaoAtendimentoWhatsapp sessaoAtualizada = sessaoService.buscarPorId(idSessao);

        var categorias = marketplaceCategoriaService.listarCategoriasDisponiveis(
            marketplace,
            sessaoAtualizada
        );

        MensagemWhatsappSaidaDTO mensagemSaida = marketplaceMensagemService
    	    .montarMenuCategoriasAposReceberCep(
    	        whatsappCliente,
    	        marketplace,
    	        enderecoResolvido,
    	        categorias
    	    );

        return new RoteamentoResultado(
            "marketplace_localizacao_refinada_por_cep",
            mensagemSaida
        );
    }

    private String normalizarCep(String textoDigitado) {

        if (textoDigitado == null) {
            return null;
        }

        String cep = textoDigitado.replaceAll("\\D", "");
        return StringUtils.hasText(cep) ? cep.trim() : null;
    }
}