package br.com.oraped.dto.marktplace;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Finalidade:
 * Representar um estabelecimento disponível para exibição no fluxo conversacional do marketplace.
 *
 * Aplicação:
 * Utilizado entre a camada de serviço de discovery e a camada de montagem das mensagens
 * do WhatsApp, sem expor a entity diretamente.
 *
 * Utilização:
 * Deve transportar apenas os dados necessários para exibição e seleção do estabelecimento,
 * mantendo o fluxo desacoplado da modelagem JPA.
 */
@Getter
@AllArgsConstructor
public class EstabelecimentoDisponivelMarketplaceDTO {

    private final Long id;
    private final String nome;
    private final String bairro;
    private final String cidade;
    private final String uf;
    private final BigDecimal valorPedidoMinimo;
}