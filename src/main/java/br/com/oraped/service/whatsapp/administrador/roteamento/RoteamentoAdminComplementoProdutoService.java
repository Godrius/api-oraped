package br.com.oraped.service.whatsapp.administrador.roteamento;

import org.springframework.stereotype.Service;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.whatsapp.ComandoWhatsapp;
import br.com.oraped.domain.whatsapp.RoteamentoResultado;
import br.com.oraped.service.whatsapp.administrador.AdminComplementoProdutoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdministradorWhatsappResultados;
import br.com.oraped.service.whatsapp.orquestrador.OrquestradorParseService;
import lombok.RequiredArgsConstructor;

/**
 * Finalidade:
 * Centralizar o roteamento administrativo relacionado aos complementos por produto.
 *
 * Aplicação:
 * Utilizado pelo RoteamentoAdminService para delegar associação,
 * desassociação, listagem, detalhe e configuração dos grupos
 * de complementos vinculados aos produtos.
 *
 * Utilização:
 * Este service atua apenas como camada de roteamento,
 * mantendo as regras operacionais em AdminComplementoProdutoService.
 */
@Service
@RequiredArgsConstructor
public class RoteamentoAdminComplementoProdutoService {

    private final AdminComplementoProdutoService adminComplementoProdutoService;

    private final OrquestradorParseService parse;

    public RoteamentoResultado rotear(
	    Estabelecimento estabelecimento,
	    String whatsappAdmin,
	    Long idSessao,
	    ComandoWhatsapp cmd
	){

	    String acao = cmd == null ? null : cmd.getAcao();

	    switch (acao) {

		    case "ADMIN_PROD_COMPLEMENTOS_MENU": {
		        Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
		        Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
		        Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
		        Integer offsetComplementos = parse.parseIntDefaultZero(cmd.getParte(5));
	
		        AdministradorWhatsappResultados.ResultadoAdmin r =
		            adminComplementoProdutoService.montarMenuComplementosProduto(
		                estabelecimento,
		                whatsappAdmin,
		                idProduto,
		                idCategoria,
		                offsetListaProduto,
		                offsetComplementos
		            );
	
		        return new RoteamentoResultado(r.chave, r.mensagem);
		    }

	        case "ADMIN_PROD_COMP_GRUPOS": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Integer offsetGrupos = parse.parseIntDefaultZero(cmd.getParte(5));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.listarGruposProduto(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    offsetGrupos
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_GRUPO_NOVO": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.iniciarCadastroGuiadoComplementoProduto(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idSessao,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_GRUPO_DETALHE": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.montarDetalheGrupoProduto(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTOS": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Integer offsetComplementos = parse.parseIntDefaultZero(cmd.getParte(6));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.listarComplementosDoGrupo(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    offsetComplementos
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTO_DETALHE": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(6), "idComplemento");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.montarDetalheComplemento(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    idComplemento
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTO_STATUS": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(6), "idComplemento");
	            boolean ativo = parse.parseIntDefaultZero(cmd.getParte(7)) == 1;

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.alterarStatusComplemento(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    idComplemento,
	                    ativo
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTO_PRECO_MENU": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(6), "idComplemento");

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.montarMenuPrecoComplemento(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    idComplemento
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        case "ADMIN_PROD_COMP_COMPLEMENTO_PRECO_APLICAR": {
	            Long idProduto = parse.parseLongObrigatorio(cmd.getParte(2), "idProduto");
	            Long idCategoria = parse.parseLongObrigatorio(cmd.getParte(3), "idCategoria");
	            Integer offsetListaProduto = parse.parseIntDefaultZero(cmd.getParte(4));
	            Long idGrupo = parse.parseLongObrigatorio(cmd.getParte(5), "idGrupo");
	            Long idComplemento = parse.parseLongObrigatorio(cmd.getParte(6), "idComplemento");
	            Integer deltaCentavos = parse.parseIntDefaultZeroAllowNegative(cmd.getParte(7));

	            AdministradorWhatsappResultados.ResultadoAdmin r =
	                adminComplementoProdutoService.aplicarDeltaPrecoComplemento(
	                    estabelecimento,
	                    whatsappAdmin,
	                    idProduto,
	                    idCategoria,
	                    offsetListaProduto,
	                    idGrupo,
	                    idComplemento,
	                    deltaCentavos
	                );

	            return new RoteamentoResultado(r.chave, r.mensagem);
	        }

	        default:
	            return null;
	    }
	}
}