package br.com.oraped.service.whatsapp.orquestrador;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.OrquestradorContexto;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.roteamento.RoteamentoAdminService;
import br.com.oraped.service.whatsapp.cliente.roteamento.RoteamentoClienteService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrquestradorRoteamentoService {

    private final RoteamentoAdminService rotearAdmin;
    private final RoteamentoClienteService rotearCliente;

    public RoteamentoResultado rotearComando(
	    OrquestradorContexto ctx,
	    ComandoWhatsapp cmd,
	    String idCorrelacao,
	    String wamidEntrada
	) {


        Estabelecimento estabelecimento = ctx.getEstabelecimento();

        String whatsappCliente = ctx.getWhatsappCliente();
        String whatsappReceptor = ctx.getWhatsappReceptor();
        String phoneNumberId = ctx.getPhoneNumberId();
        Long idSessao = ctx.getSessao() == null ? null : ctx.getSessao().getId();

        String acao = cmd == null ? null : cmd.getAcao();

        if (acao != null && acao.startsWith("ADMIN_")) {
            return rotearAdmin.rotearAdmin(estabelecimento, whatsappCliente, idSessao, cmd);
        }

        return rotearCliente.rotearCliente(
            estabelecimento,
            whatsappCliente,
            whatsappReceptor,
            phoneNumberId,
            idCorrelacao,
            wamidEntrada,
            idSessao,
            cmd
        );
    }
}