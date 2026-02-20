// src/main/java/br/com/oraped/dto/EstabelecimentoResponseDTO.java
package br.com.oraped.dto.estabelecimento;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.oraped.domain.AdministradorEstabelecimento;
import br.com.oraped.domain.CategoriaProduto;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.MarcaProduto;
import br.com.oraped.domain.Produto;
import br.com.oraped.dto.AdministradorResumoDTO;
import lombok.Getter;

@Getter
public class EstabelecimentoMenuResponseDTO {

  private Long idEstabelecimento;
  private String nome;
  private String whatsapp;
  private String endereco;
  private String timezone;

  private boolean ativo;
  private boolean aberto;

  private List<CategoriaMenuDTO> categorias;

  private List<AdministradorResumoDTO> administradores;

  
  public EstabelecimentoMenuResponseDTO(Estabelecimento e) {

	    this.idEstabelecimento = e.getId();
	    this.nome = e.getNome();
	    this.whatsapp = e.getWhatsapp();
	    this.endereco = e.getEndereco();
	    this.timezone = e.getTimezone();
	    
	    this.ativo = e.isAtivo();
	    this.aberto = e.isAberto();

	    // 1) Filtra produtos válidos para o menu
	    List<Produto> produtos = e.getProdutos().stream()
	      .filter(Produto::isDisponivelParaVenda)
	      .filter(p -> p.getCategoria() != null && p.getCategoria().isAtiva())
	      .filter(p -> p.getMarca() != null && p.getMarca().isAtiva())
	      .toList();

	    // 2) Agrupa Categoria -> Marca -> Produtos
	    // Estrutura: catId -> (marcaId -> listaProdutos)
	    Map<Long, Map<Long, List<Produto>>> agrupado = new LinkedHashMap<>();

	    for (Produto p : produtos) {
	      Long idCategoria = p.getCategoria().getId();
	      Long idMarca = p.getMarca().getId();

	      agrupado.putIfAbsent(idCategoria, new LinkedHashMap<>());
	      agrupado.get(idCategoria).putIfAbsent(idMarca, new ArrayList<>());
	      agrupado.get(idCategoria).get(idMarca).add(p);
	    }

	    // 3) Converte para DTO ordenando por nome (categoria, marca, produto)
	    this.categorias = agrupado.entrySet().stream()
	      .map(entryCategoria -> {

	        Long idCategoria = entryCategoria.getKey();
	        Map<Long, List<Produto>> marcasMap = entryCategoria.getValue();

	        // Pega dados da categoria a partir do primeiro produto
	        Produto p0 = marcasMap.values().iterator().next().get(0);
	        CategoriaProduto categoriaEnt = p0.getCategoria();

	        CategoriaMenuDTO catDto = new CategoriaMenuDTO();
	        catDto.setIdCategoria(idCategoria);
	        catDto.setNome(categoriaEnt.getNome());
	        catDto.setQuantidadeMultipla(categoriaEnt.getQuantidadeMultipla());
	        
	        List<MarcaMenuDTO> marcas = marcasMap.entrySet().stream()
	          .map(entryMarca -> {

	            Long idMarca = entryMarca.getKey();
	            List<Produto> prods = entryMarca.getValue();

	            MarcaProduto marcaEnt = prods.get(0).getMarca();

	            MarcaMenuDTO marcaDto = new MarcaMenuDTO();
	            marcaDto.setIdMarca(idMarca);
	            marcaDto.setNome(marcaEnt.getNome());

	            List<ProdutoMenuDTO> produtosDto = prods.stream()
	              .sorted(Comparator.comparing(Produto::getNome, String.CASE_INSENSITIVE_ORDER))
	              .map(px -> new ProdutoMenuDTO(px))
	              .toList();

	            marcaDto.setProdutos(produtosDto);
	            return marcaDto;
	          })
	          .sorted(Comparator.comparing(MarcaMenuDTO::getNome, String.CASE_INSENSITIVE_ORDER))
	          .toList();

	        catDto.setMarcas(marcas);
	        return catDto;
	      })
	      .sorted(Comparator.comparing(CategoriaMenuDTO::getNome, String.CASE_INSENSITIVE_ORDER))
	      .toList();
	    
	    
	    // 4) Admins ativos (para fallback humano / notificações)
	    this.administradores = e.getAdministradores().stream()
	      .filter(AdministradorEstabelecimento::isAtivo)
	      .map(AdministradorResumoDTO::new)
	      .toList();
	  }
  
  
  
}
