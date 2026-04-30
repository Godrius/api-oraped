package br.com.oraped.dto.marktplace;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Finalidade:
 * Representar uma categoria de marketplace pronta para exibição no fluxo conversacional.
 *
 * Aplicação:
 * Utilizado como DTO enxuto entre a camada de serviço do marketplace e a camada
 * de montagem das mensagens do WhatsApp.
 *
 * Utilização:
 * Deve transportar apenas os dados necessários para exibição e seleção da categoria,
 * evitando expor entities diretamente no fluxo do orquestrador.
 */
@Getter
@AllArgsConstructor
public class CategoriaMarketplaceDisponivelDTO {

    private final Long id;
    private final String nome;
}