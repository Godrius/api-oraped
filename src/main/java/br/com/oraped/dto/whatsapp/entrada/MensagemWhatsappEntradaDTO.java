// src/main/java/br/com/oraped/dto/whatsapp/MensagemWhatsappEntradaDTO.java
package br.com.oraped.dto.whatsapp.entrada;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MensagemWhatsappEntradaDTO {

	private String phoneNumberId;
	
	// número do cliente (remetente)
	@NotBlank
	@Size(max = 30)
	private String whatsappCliente;

	// número que o cliente acionou (receptor) -> usado para identificar o estabelecimento
	@NotBlank
	@Size(max = 30)
	private String whatsappReceptor;

	// texto "livre" (quando usuário digita)
	@Size(max = 5000)
	private String texto;

	// quando vier clique em lista/botão, normalmente o provider manda o "id" do item clicado.
	// no nosso padrão esse id já será o COMANDO|...
	@Size(max = 5000)
	private String comando;

	@Size(max = 120)
	private String idMensagem;

	@Size(max = 120)
	private String idCorrelacao;

  	private Object payloadOriginal;

  	@JsonIgnore
  	public String getTextoOuComando() {

  	    if (StringUtils.hasText(this.comando)) {
  	        return this.comando.trim();
  	    }

  	    if (StringUtils.hasText(this.texto)) {
  	        return this.texto.trim();
  	    }

  	    return null;
  	}

  	@JsonIgnore
  	public String getTextoSeguro(int max) {
  		String v = texto == null ? "" : texto;
  		return v.length() <= max ? v : v.substring(0, max);
  	}
}