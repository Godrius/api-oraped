package br.com.oraped.service.whatsapp.sessao;

import java.time.OffsetDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import br.com.oraped.domain.whatsapp.SessaoEncerradaEvent;
import br.com.oraped.repository.whatsapp.SessaoAtendimentoWhatsappRepository;
import br.com.oraped.service.carrinho.CarrinhoService;
import lombok.RequiredArgsConstructor;

/**
 * Serviço principal de ciclo de vida da sessão WhatsApp.
 * Deve concentrar criação, recuperação, encerramento e operações globais da sessão.
 */
@Service
@RequiredArgsConstructor
public class SessaoAtendimentoWhatsappService {

    private final SessaoAtendimentoWhatsappRepository sessaoRepo;
    private final SessaoWhatsappStore sessaoStore;
    private final SessaoWhatsappCleaner sessaoCleaner;
    private final ApplicationEventPublisher eventPublisher;
    private final CarrinhoService carrinhoService;
    
    @Transactional
    public SessaoAtendimentoWhatsapp obterOuCriar(
        String whatsappCliente,
        String whatsappReceptor,
        Long idEstabelecimento,
        Long idMarketplace
    ) {

        validarObterOuCriar(whatsappCliente, whatsappReceptor, idEstabelecimento, idMarketplace);

        String wCliente = whatsappCliente.trim();
        String wReceptor = whatsappReceptor.trim();

        return sessaoRepo.buscarSessaoAtiva(wCliente, wReceptor)
            .map(sessao -> atualizarSessaoExistente(sessao, idEstabelecimento, idMarketplace))
            .orElseGet(() -> criarNovaSessao(wCliente, wReceptor, idEstabelecimento, idMarketplace));
    }

    @Transactional
    public SessaoAtendimentoWhatsapp criarNovaSessao(
        String whatsappCliente,
        String whatsappReceptor,
        Long idEstabelecimento,
        Long idMarketplace
    ) {

        validarObterOuCriar(whatsappCliente, whatsappReceptor, idEstabelecimento, idMarketplace);

        SessaoAtendimentoWhatsapp sessao = new SessaoAtendimentoWhatsapp();
        sessao.setWhatsappCliente(whatsappCliente.trim());
        sessao.setWhatsappReceptor(whatsappReceptor.trim());
        sessao.setIdEstabelecimento(idEstabelecimento);
        sessao.setIdMarketplace(idMarketplace);
        sessao.setEncerradaEm(null);

        garantirDefaults(sessao);

        return sessaoStore.salvar(sessao);
    }

    @Transactional
    public void encerrarSessao(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        sessaoCleaner.limparPedidoEmAndamento(sessao);
        sessaoCleaner.limparContextoEstabelecimento(sessao);
        sessaoCleaner.limparContextoMarketplace(sessao);

        sessao.setEncerradaEm(OffsetDateTime.now());
        sessaoStore.salvar(sessao);

        // A limpeza das mensagens fica assíncrona para não atrasar a resposta ao WhatsApp.
        eventPublisher.publishEvent(new SessaoEncerradaEvent(idSessao));
    }

    @Transactional
    public void atualizarInteracao(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessaoStore.salvar(sessao);
    }

    @Transactional(readOnly = true)
    public SessaoAtendimentoWhatsapp buscarPorId(Long idSessao) {
        return sessaoStore.buscarPorId(idSessao);
    }

    @Transactional
    public void limparAguardando(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);
        sessaoCleaner.limparAguardando(sessao);
        sessaoStore.salvar(sessao);
    }

    @Transactional
    public void limparPedidoEmAndamento(Long idSessao) {

        SessaoAtendimentoWhatsapp sessao = sessaoStore.buscarPorId(idSessao);

        // O carrinho agora é persistido em tabelas próprias, não mais reconstruído pelo histórico.
        carrinhoService.limparCarrinho(idSessao);

        sessaoCleaner.limparPedidoEmAndamento(sessao);
        sessaoStore.salvar(sessao);
    }

    private SessaoAtendimentoWhatsapp atualizarSessaoExistente(
        SessaoAtendimentoWhatsapp sessao,
        Long idEstabelecimento,
        Long idMarketplace
    ) {

        /*
         * Quando o receptor é marketplace, preservamos esse contexto mesmo que
         * a sessão já esteja operando dentro de uma loja escolhida.
         */
        if (idMarketplace != null) {
            sessao.setIdMarketplace(idMarketplace);
        }

        if (idEstabelecimento != null) {
            sessao.setIdEstabelecimento(idEstabelecimento);

            /*
             * Se não há marketplace na sessão, trata-se de atendimento direto
             * da loja; nesse caso, removemos qualquer resíduo de discovery.
             */
            if (sessao.getIdMarketplace() == null) {
                sessaoCleaner.limparCategoriaMarketplace(sessao);
                sessao.setLatitudeOrigemCliente(null);
                sessao.setLongitudeOrigemCliente(null);
            }
        }

        garantirDefaults(sessao);

        return sessaoStore.salvar(sessao);
    }

    private void validarObterOuCriar(
        String whatsappCliente,
        String whatsappReceptor,
        Long idEstabelecimento,
        Long idMarketplace
    ) {

        if (!StringUtils.hasText(whatsappCliente)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappCliente é obrigatório");
        }

        if (!StringUtils.hasText(whatsappReceptor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "whatsappReceptor é obrigatório");
        }

        boolean temEstabelecimento = idEstabelecimento != null;
        boolean temMarketplace = idMarketplace != null;

        if (!temEstabelecimento && !temMarketplace) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "idEstabelecimento ou idMarketplace é obrigatório"
            );
        }

        if (temEstabelecimento && temMarketplace) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A sessão deve estar vinculada a um estabelecimento ou a um marketplace, nunca aos dois"
            );
        }
    }

    private void garantirDefaults(SessaoAtendimentoWhatsapp sessao) {

        if (sessao.getAguardandoNovoPreco() == null) sessao.setAguardandoNovoPreco(false);

        if (sessao.getAguardandoNovaMarca() == null) sessao.setAguardandoNovaMarca(false);
        if (sessao.getAguardandoEditarMarcaNome() == null) sessao.setAguardandoEditarMarcaNome(false);

        if (sessao.getAguardandoNovoNomeProduto() == null) sessao.setAguardandoNovoNomeProduto(false);
        if (sessao.getAguardandoNovaDescricaoProduto() == null) sessao.setAguardandoNovaDescricaoProduto(false);
        if (sessao.getAguardandoNovaFotoProduto() == null) sessao.setAguardandoNovaFotoProduto(false);

        if (sessao.getAguardandoTaxaEntregaBairro() == null) sessao.setAguardandoTaxaEntregaBairro(false);
        if (sessao.getAguardandoTaxaEntregaPadrao() == null) sessao.setAguardandoTaxaEntregaPadrao(false);

        if (sessao.getAguardandoBairrosAtendidos() == null) sessao.setAguardandoBairrosAtendidos(false);

        if (sessao.getAguardandoQuantidadeManual() == null) sessao.setAguardandoQuantidadeManual(false);
    }
}