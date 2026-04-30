package br.com.oraped.service.whatsapp.administrador.utils;

import java.math.BigDecimal;

import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;

public final class AdministradorWhatsappResultados {

    private AdministradorWhatsappResultados() {}

    public static class ResultadoAdmin {
        public final String chave;
        public final MensagemWhatsappSaidaDTO mensagem;

        public ResultadoAdmin(String chave, MensagemWhatsappSaidaDTO mensagem) {
            this.chave = chave;
            this.mensagem = mensagem;
        }
    }

    public static class ResultadoAdminPreco {
        public final ResultadoAdmin admin;
        public final BigDecimal novoPreco;
        public final String nomeProduto;
        public final String descricaoProduto;

        public ResultadoAdminPreco(
            ResultadoAdmin admin,
            BigDecimal novoPreco,
            String nomeProduto,
            String descricaoProduto
        ) {
            this.admin = admin;
            this.novoPreco = novoPreco;
            this.nomeProduto = nomeProduto;
            this.descricaoProduto = descricaoProduto;
        }
    }

    public static class ResultadoAdminMarca {
        public final ResultadoAdmin admin;
        public final Long idMarca;
        public final String nomeMarca;

        public ResultadoAdminMarca(ResultadoAdmin admin, Long idMarca, String nomeMarca) {
            this.admin = admin;
            this.idMarca = idMarca;
            this.nomeMarca = nomeMarca;
        }
    }

    public enum AcaoPedidoAdmin {
        ACEITAR,
        RECUSAR,
        PREPARAR,
        CANCELAR,
        INICIAR_ENTREGA
    }

    public static class ResultadoAdminAcaoPedido {
        public final ResultadoAdmin admin;
        public final String whatsappCliente;
        public final String textoCliente;
        public final MensagemWhatsappSaidaDTO mensagemCliente;

        public ResultadoAdminAcaoPedido(
            ResultadoAdmin admin,
            String whatsappCliente,
            String textoCliente,
            MensagemWhatsappSaidaDTO mensagemCliente
        ) {
            this.admin = admin;
            this.whatsappCliente = whatsappCliente;
            this.textoCliente = textoCliente;
            this.mensagemCliente = mensagemCliente;
        }
    }
}