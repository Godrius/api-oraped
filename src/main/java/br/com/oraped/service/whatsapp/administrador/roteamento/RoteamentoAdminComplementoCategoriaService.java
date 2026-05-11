package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.AdminComplementoCategoriaService;
import br.com.oraped.service.whatsapp.administrador.AdminGrupoComplementoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado aos grupos de complementos
 * das categorias de produtos.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar:
 * - listagem de categorias
 * - listagem de grupos da categoria
 * - criação de novos grupos de complementos
 * - abertura do menu administrativo da categoria
 *
 * Utilização:
 * Este service apenas interpreta comandos administrativos e delega
 * as regras operacionais para AdminComplementoCategoriaService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminComplementoCategoriaService {

    private final AdminComplementoCategoriaService adminComplementoCategoriaService;
    private final AdminGrupoComplementoService adminGrupoComplementoService;
    
    private final OrquestradorParseService parse;

    public RoteamentoResultado rotear(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    ComandoWhatsapp cmd
	) {

        String acao = cmd == null ? null : cmd.getAcao();

        switch (acao) {

	        case "ADMIN_CAT_COMPLEMENTOS_MENU": {
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
	            Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));
	            Integer offsetComplementos = parse.parseIntDefaultZero(cmd.getParte(4));
	
	            var r = adminComplementoCategoriaService.montarMenuComplementosCategoria(
	                estabelecimento,
	                whatsappAdmin,
	                idCategoria,
	                offsetCategorias,
	                offsetComplementos
	            );
	
	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

            case "ADMIN_CAT_COMP_CATEGORIAS": {
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(2));

                var r = adminComplementoCategoriaService.listarCategoriasParaComplementos(
                    estabelecimento,
                    whatsappAdmin,
                    offsetCategorias
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_COMP_GRUPOS": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));
                Integer offsetComplementos = parse.parseIntDefaultZero(cmd.getParte(4));

                var r = adminComplementoCategoriaService.montarMenuComplementosCategoria(
                    estabelecimento,
                    whatsappAdmin,
                    idCategoria,
                    offsetCategorias,
                    offsetComplementos
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            case "ADMIN_CAT_COMP_GRUPO_NOVO": {
                Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(2), "idCategoria");
                Integer offsetCategorias = parse.parseIntDefaultZero(cmd.getParte(3));

                var r = adminComplementoCategoriaService.iniciarCadastroGuiadoComplementoCategoria(
                    estabelecimento,
                    whatsappAdmin,
                    idSessao,
                    idCategoria,
                    offsetCategorias
                );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }
            
            case "ADMIN_COMP_COMPLEMENTO_DETALHE": {
                Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(2), "idGrupo");
                Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(3));
                Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(4), "idComplemento");

                AdministradorWhatsappResultados.ResultadoAdmin r =
                    adminGrupoComplementoService.montarDetalheComplementoGlobal(
                        estabelecimento,
                        whatsappAdmin,
                        idGrupo,
                        offsetGrupos,
                        idComplemento
                    );

                return new RoteamentoResultado(r.chave, r.mensagem);
            }

            default:
                return null;
        }
        
        
    }
}