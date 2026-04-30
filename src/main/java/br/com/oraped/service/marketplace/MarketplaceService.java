package br.com.oraped.service.marketplace;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.marketplace.Marketplace;
import br.com.oraped.repository.marketplace.MarketplaceRepository;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Responsável por centralizar as operações de consulta e validação de Marketplaces
 * no contexto da aplicação.
 *
 * Aplicação:
 * Utilizado principalmente no fluxo de entrada do WhatsApp para identificar se o número
 * receptor pertence a um marketplace válido e ativo.
 *
 * Utilização:
 * Deve ser utilizado por orquestradores e serviços de entrada para recuperar o marketplace
 * a partir do número de WhatsApp, garantindo que apenas registros ativos sejam considerados.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final MarketplaceRepository repo;

    /**
     * Busca um marketplace ativo a partir do número de WhatsApp.
     *
     * Regras:
     * - O número deve ser informado (não nulo e não vazio)
     * - Apenas marketplaces com ativo = true são considerados
     * - Caso não encontrado, retorna erro 404 (NOT_FOUND)
     *
     * @param whatsapp número do WhatsApp (somente dígitos ou formatado)
     * @return Marketplace ativo correspondente
     */
    @Transactional(readOnly = true)
    public Marketplace buscarPorWhatsapp(String whatsapp) {

        // Validação básica de entrada para evitar consultas desnecessárias
        if (!StringUtils.hasText(whatsapp)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Marketplace não encontrado"
            );
        }

        // Normalização simples para evitar erro por espaços
        String whatsappNormalizado = whatsapp.trim();

        // Busca apenas marketplaces ativos vinculados ao número informado
        return repo.findByWhatsappAndAtivoTrue(whatsappNormalizado)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Marketplace não encontrado"
            ));
    }
    
    
    
    
    @Transactional(readOnly = true)
    public Marketplace buscarPorId(Long idMarketplace) {
        if (idMarketplace == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace não encontrado");
        }

        return repo.findById(idMarketplace)
            .filter(Marketplace::isAtivo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace não encontrado"));
    }
}