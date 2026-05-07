package br.com.oraped.service.whatsapp.administrador.roteamento;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.dto.whatsapp.saida.MensagemInterativaBotaoReplyWhatsappDTO;
import br.com.oraped.dto.whatsapp.saida.MensagemWhatsappSaidaDTO;
import br.com.oraped.service.whatsapp.WhatsappMensagemFactory;
import br.com.oraped.service.whatsapp.administrador.AdminEntregaService;
import br.com.oraped.service.whatsapp.administrador.MenuAdminService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado às entregas.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar ações de CEP da loja,
 * taxa padrão, taxas por bairro e bairros atendidos.
 *
 * Utilização:
 * Este service interpreta comandos administrativos de entrega e delega
 * as regras operacionais para AdminEntregaService e MenuAdminService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminEntregaService {

    private final AdminEntregaService adminEntregaService;
    private final MenuAdminService menuAdminService;

    private final OrquestradorParseService parse;
    private final WhatsappMensagemFactory msg;

    public RoteamentoResultado rotear(
        Estabelecimento estabelecimento,
        String whatsappAdmin,
        Long idSessao,
        ComandoWhatsapp cmd
    ) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

            case "ADMIN_ENTREGAS_MENU": {
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    menuAdminService.montarMenuEntregas(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_CEP_MENU":
            case "ADMIN_ENTREGAS_CEP_DIGITAR": {
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.iniciarCadastroCepLojaPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_TAXAS_MENU": {
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.montarMenuTaxasEntrega(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_TAXA_PADRAO_MENU": {
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.montarMenuTaxaPadrao(estabelecimento, whatsappAdmin);

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_BAIRROS_MENU": {
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.montarMenuTaxaPorBairros(
                        estabelecimento,
                        whatsappAdmin,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_BAIRRO_SELECIONAR": {
                Long idBairro = parse.parseLongObrigatorio(cmd.getParte(2), "idBairro");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                // Ao selecionar o bairro, mostramos primeiro o menu de ações do bairro.
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.montarMenuBairroEntregaSelecionado(
                        estabelecimento,
                        whatsappAdmin,
                        idBairro,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_TAXA_BAIRRO_DIGITAR": {
                Long idBairro = parse.parseLongObrigatorio(cmd.getParte(2), "idBairro");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                // A digitação manual fica explícita para não confundir com botões rápidos.
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.iniciarCadastroTaxaEntregaBairroPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        idBairro,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_BAIRRO_ISENTO": {
                Long idBairro = parse.parseLongObrigatorio(cmd.getParte(2), "idBairro");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                // Frete grátis precisa ser explícito para não virar ausência de configuração.
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.marcarBairroComoEntregaGratuita(
                        estabelecimento,
                        whatsappAdmin,
                        idBairro,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_BAIRRO_REMOVER": {
                Long idBairro = parse.parseLongObrigatorio(cmd.getParte(2), "idBairro");
                Integer offset = parse.parseIntDefaultZero(cmd.getParte(3));

                // Remove a regra específica para o bairro voltar ao comportamento padrão.
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.removerConfiguracaoBairroEntrega(
                        estabelecimento,
                        whatsappAdmin,
                        idBairro,
                        offset
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_ENTREGAS_BAIRROS_ATENDIDOS": {
                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.iniciarConfiguracaoBairrosAtendidosPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao
                    );

                MensagemWhatsappSaidaDTO navegacao = msg.botoes(
                    whatsappAdmin,
                    "Digite os códigos dos bairros atendidos ou use os botões abaixo para voltar ao menu de opções",
                    List.of(
                        new MensagemInterativaBotaoReplyWhatsappDTO(
                            "COMANDO|ADMIN_ENTREGAS_MENU",
                            "⬅️ Voltar"
                        ),
                        new MensagemInterativaBotaoReplyWhatsappDTO(
                            "COMANDO|ADMIN_MENU",
                            "🛠️ Menu admin"
                        )
                    )
                );

                return new RoteamentoResultado(r.chave, r.mensagem, List.of(navegacao));
            }

            case "ADMIN_ENTREGAS_TAXA_PADRAO_DIGITAR": {
                Integer offsetVoltar = parse.parseIntDefaultZero(cmd.getParte(2));

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminEntregaService.iniciarCadastroTaxaEntregaPadraoPorDigitacao(
                        estabelecimento,
                        whatsappAdmin,
                        idSessao,
                        offsetVoltar
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return null;
        }
    }
}