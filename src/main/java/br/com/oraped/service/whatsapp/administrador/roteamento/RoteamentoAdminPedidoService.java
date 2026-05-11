package br.com.oraped.service.whatsapp.administrador.roteamento;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdminPedidoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado aos pedidos.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar ações de:
 * - listagem
 * - detalhamento
 * - mudança de status
 * - fluxo operacional dos pedidos
 *
 * Utilização:
 * Este service NÃO implementa regras de negócio do pedido.
 * Ele apenas interpreta comandos e delega para AdminPedidoService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminPedidoService {

    private final AdminPedidoService adminPedidoService;

    private final OrquestradorParseService parse;
    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            // =========================================================
            // ADMIN: PEDIDOS
            // =========================================================

	        case "ADMIN_PEDIDOS_MENU": {
	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminPedidoService.montarMenuPedidos(
	                    estabelecimento,
	                    whatsappAdmin
	                );
	
	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }
	        
            case "ADMIN_VER_PEDIDOS": {
                StatusPedido status = parse.parseStatusPedidoObrigatorio(cmd.getParte(2));
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminPedidoService.listarPedidosPorStatus(
                        estabelecimento,
                        whatsappAdmin,
                        status,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_PEDIDO_DETALHE": {
                Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminPedidoService.montarDetalhePedido(
                        estabelecimento,
                        whatsappAdmin,
                        idPedido
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ACEITAR_PEDIDO":
                return executarAcaoPedidoAdmin(
                    estabelecimento,
                    whatsappAdmin,
                    cmd,
                    AdministradorWhatsappResultados.AcaoPedidoAdmin.ACEITAR
                );

            case "ADMIN_RECUSAR_PEDIDO":
                return executarAcaoPedidoAdmin(
                    estabelecimento,
                    whatsappAdmin,
                    cmd,
                    AdministradorWhatsappResultados.AcaoPedidoAdmin.RECUSAR
                );

            case "ADMIN_PREPARAR_PEDIDO":
                return executarAcaoPedidoAdmin(
                    estabelecimento,
                    whatsappAdmin,
                    cmd,
                    AdministradorWhatsappResultados.AcaoPedidoAdmin.PREPARAR
                );

            case "ADMIN_CANCELAR_PEDIDO":
                return executarAcaoPedidoAdmin(
                    estabelecimento,
                    whatsappAdmin,
                    cmd,
                    AdministradorWhatsappResultados.AcaoPedidoAdmin.CANCELAR
                );

            case "ADMIN_INICIAR_ENTREGA":
                return executarAcaoPedidoAdmin(
                    estabelecimento,
                    whatsappAdmin,
                    cmd,
                    AdministradorWhatsappResultados.AcaoPedidoAdmin.INICIAR_ENTREGA
                );

            default:
                return null;
        }
    }

    private RoteamentoResultado executarAcaoPedidoAdmin(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        ComandoWhatsapp cmd,
        AdministradorWhatsappResultados.AcaoPedidoAdmin acao
    ) {

        Long idPedido = parse.parseLongObrigatorio(cmd.getParte(2), "idPedido");

        AdministradorWhatsappResultados.ResultadoAdminAcaoPedido r =
    	    adminPedidoService.executarAcaoPedido(
    	        estabelecimento,
    	        whatsappAdmin,
    	        idPedido,
    	        acao
    	    );

        List<MensagemWhatsappSaidaDTO> extras = new ArrayList<>();

        if (r.mensagemCliente != null) {
            extras.add(r.mensagemCliente);
        } else if (
            StringUtils.hasText(r.whatsappCliente) &&
            StringUtils.hasText(r.textoCliente)
        ) {
            extras.add(msg.texto(r.whatsappCliente, r.textoCliente));
        }

        return new RoteamentoResultado(
            r.admin.chave,
            r.admin.mensagem,
            extras
        );
    }
}