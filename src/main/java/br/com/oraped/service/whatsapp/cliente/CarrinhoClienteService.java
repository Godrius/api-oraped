package br.com.oraped.service.whatsapp.cliente;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.carrinho.Carrinho;
import br.com.oraped.domain.carrinho.ComplementoItemCarrinho;
import br.com.oraped.domain.carrinho.ItemCarrinho;
import br.com.oraped.domain.produto.Produto;
import br.com.oraped.service.carrinho.CarrinhoService;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Montar leituras textuais do carrinho usado no fluxo WhatsApp do cliente.
 *
 * Aplicação:
 * Usado pelas telas de carrinho, confirmação, pagamento e envio do pedido.
 *
 * Utilização:
 * A fonte oficial agora é o carrinho persistido, não mais o histórico textual de comandos.
 */
@Service
@RequiredArgsConstructor
public class CarrinhoClienteService {

    private final CarrinhoService carrinhoService;
    private final WhatsappMensagemFactory msg;

    public Carrinho buscarCarrinhoAtual(Long idSessao) {
        return carrinhoService.buscarCarrinhoCompleto(idSessao);
    }

    public boolean isCarrinhoVazio(Long idSessao) {
        return carrinhoService.isCarrinhoVazio(idSessao);
    }

    public String montarResumoItensDoCarrinho(
	    Estabelecimento estabelecimento,
	    Carrinho carrinho
	) {

	    if (carrinho == null || carrinho.getItens() == null || carrinho.getItens().isEmpty()) {
	        return "(sem itens)";
	    }

	    StringBuilder sb = new StringBuilder();

	    for (ItemCarrinho item : carrinho.getItens()) {

	        if (item == null) {
	            continue;
	        }

	        Produto produto = item.getProduto();
	        String nome = produto == null ? "(produto)" : msg.safe(produto.getNome());
	        String descricaoProduto = msg.safe(produto.getDescricao());
	        
	        int quantidade = item.getQuantidade() == null ? 0 : item.getQuantidade();

	        BigDecimal precoProduto = item.getPrecoUnitario() == null
        	    ? BigDecimal.ZERO
        	    : item.getPrecoUnitario();

	        BigDecimal subtotalItem = calcularSubtotalItem(item);

	        sb.append("*")
	        .append(nome)
	        .append("*\n");

		    if (StringUtils.hasText(descricaoProduto)) {
		        sb.append(msg.trunc(descricaoProduto, 120)).append("\n");
		    }
	    
		    if (StringUtils.hasText(item.getNomeTamanho())) {
		        sb.append("Tamanho: ")
		            .append(msg.safe(item.getNomeTamanho()))
		            .append("\n");
		    }
		    
	        sb.append("Preço: ")
	          .append(msg.formatarMoeda(precoProduto))
	          .append("\n")
	          .append("Quantidade: ")
	          .append(quantidade)
	          .append("\n");

	        if (item.getComplementos() != null && !item.getComplementos().isEmpty()) {
	            sb.append("\n*Complementos:*\n");

	            for (ComplementoItemCarrinho complemento : item.getComplementos()) {

	                if (complemento == null || complemento.getQuantidade() == null || complemento.getQuantidade() < 1) {
	                    continue;
	                }

	                sb.append("• ")
	                    .append(complemento.getQuantidade())
	                    .append("x ")
	                    .append(msg.safe(complemento.getNome()))
	                    .append(": ")
	                    .append(msg.formatarMoeda(calcularSubtotalComplemento(complemento)))
	                    .append("\n");
	            }
	        }

	        sb.append("\n*Valor total do item:* ")
	            .append(msg.formatarMoeda(subtotalItem))
	            .append("\n\n");
	    }

	    return sb.toString().trim();
	}

    public BigDecimal calcularSubtotalCarrinho(Carrinho carrinho) {

        if (carrinho == null || carrinho.getItens() == null || carrinho.getItens().isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;

        for (ItemCarrinho item : carrinho.getItens()) {
            total = total.add(calcularSubtotalItem(item));
        }

        return total;
    }

    public BigDecimal calcularSubtotalItem(ItemCarrinho item) {

        if (item == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal precoProduto = item.getPrecoUnitario() == null
    	    ? BigDecimal.ZERO
    	    : item.getPrecoUnitario();

        BigDecimal somaComplementos = BigDecimal.ZERO;

        if (item.getComplementos() != null) {
            for (ComplementoItemCarrinho complemento : item.getComplementos()) {
                somaComplementos = somaComplementos.add(calcularSubtotalComplemento(complemento));
            }
        }

        int quantidadeProduto = item.getQuantidade() == null ? 0 : item.getQuantidade();

        // Os complementos configuram o valor unitário do item; depois o total é multiplicado pela quantidade do produto.
        BigDecimal precoUnitarioComComplementos = precoProduto.add(somaComplementos);

        return precoUnitarioComComplementos.multiply(BigDecimal.valueOf(quantidadeProduto));
    }

    public BigDecimal calcularSubtotalComplemento(ComplementoItemCarrinho complemento) {

        if (complemento == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal precoUnitario = complemento.getPrecoUnitario() == null
            ? BigDecimal.ZERO
            : complemento.getPrecoUnitario();

        int quantidade = complemento.getQuantidade() == null ? 0 : complemento.getQuantidade();

        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}