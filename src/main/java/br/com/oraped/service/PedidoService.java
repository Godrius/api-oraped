// src/main/java/br/com/oraped/service/PedidoService.java
package br.com.oraped.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.Cliente;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.ItemPedido;
import br.com.oraped.domain.ItemPedidoOpcional;
import br.com.oraped.domain.Pedido;
import br.com.oraped.domain.Produto;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.dto.ItemPedidoOpcionalRequestDTO;
import br.com.oraped.dto.ItemPedidoRequestDTO;
import br.com.oraped.dto.PedidoRequestDTO;
import br.com.oraped.dto.PedidoResponseDTO;
import br.com.oraped.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    private final EstabelecimentoService estabelecimentoService;
    private final ProdutoService produtoService;
    private final ClienteService clienteService;

    @Transactional
    public PedidoResponseDTO criar(PedidoRequestDTO req) {

        validarRequest(req);

        Estabelecimento estabelecimento = estabelecimentoService.buscar(req.getIdEstabelecimento());

        // Regra WhatsApp: estabelecimento precisa estar ativo e aberto para aceitar pedido
        if (!estabelecimento.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estabelecimento inativo");
        }
        if (!estabelecimento.isAberto()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estabelecimento está fechado no momento");
        }

        // Cliente (obter ou criar por estabelecimento+telefone)
        Cliente cliente = clienteService.obterOuCriar(
            estabelecimento,
            req.getCliente().getTelefone(),
            req.getCliente().getNome()
        );

        Set<Long> idsProduto = req.getItens().stream()
            .map(ItemPedidoRequestDTO::getIdProduto)
            .collect(Collectors.toSet());

        List<Produto> produtos = produtoService.listar(idsProduto);

        if (produtos.size() != idsProduto.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Um ou mais produtos não foram encontrados");
        }

        var produtoPorId = produtos.stream()
            .collect(Collectors.toMap(Produto::getId, p -> p));

        // valida produtos
        for (Produto p : produtos) {

            if (!Objects.equals(p.getEstabelecimento().getId(), estabelecimento.getId())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Produto não pertence ao estabelecimento informado: " + p.getId()
                );
            }

            if (!p.isDisponivelParaVenda()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Produto indisponível para venda: " + p.getId()
                );
            }
        }

        Pedido pedido = new Pedido();
        pedido.setEstabelecimento(estabelecimento);

        // relacionamento
        pedido.setCliente(cliente);

        // snapshots (opcional)
        pedido.setClienteTelefone(cliente.getTelefone());
        pedido.setClienteNome(cliente.getNome());

        pedido.setStatus(definirStatusInicial());
        pedido.setTipoAtendimento(req.getTipoAtendimento());

        pedido.setNumeroMesa(req.getNumeroMesa());
        pedido.setEnderecoEntrega(req.getEnderecoEntrega());
        pedido.setObservacoes(req.getObservacoes());

        BigDecimal subtotal = BigDecimal.ZERO;

        for (ItemPedidoRequestDTO itemReq : req.getItens()) {

            Produto produto = produtoPorId.get(itemReq.getIdProduto());

            ItemPedido item = new ItemPedido();
            item.setPedido(pedido);
            item.setProduto(produto);
            item.setQuantidade(itemReq.getQuantidade());
            item.setObservacoes(itemReq.getObservacoes());

            BigDecimal precoProduto = nvl(produto.getPreco());
            item.setPrecoUnitarioProduto(precoProduto);

            BigDecimal somaOpcionaisUnitarios = BigDecimal.ZERO;

            List<ItemPedidoOpcionalRequestDTO> opcionais =
                itemReq.getOpcionais() == null ? List.of() : itemReq.getOpcionais();

            for (ItemPedidoOpcionalRequestDTO opReq : opcionais) {

                ItemPedidoOpcional op = new ItemPedidoOpcional();
                op.setItemPedido(item);
                op.setNome(opReq.getNome());
                op.setQuantidade(opReq.getQuantidade());
                op.setPrecoUnitario(opReq.getPreco());

                item.getOpcionais().add(op);

                somaOpcionaisUnitarios = somaOpcionaisUnitarios.add(
                    nvl(opReq.getPreco()).multiply(BigDecimal.valueOf(nvlInt(opReq.getQuantidade())))
                );
            }

            BigDecimal precoUnitarioTotal = precoProduto.add(somaOpcionaisUnitarios);
            BigDecimal subtotalItem =
                precoUnitarioTotal.multiply(BigDecimal.valueOf(itemReq.getQuantidade()));

            item.setSubtotalItem(subtotalItem);

            pedido.getItens().add(item);
            subtotal = subtotal.add(subtotalItem);
        }

        pedido.setSubtotal(subtotal);

        BigDecimal taxaServico = nvl(req.getTaxaServico());
        BigDecimal taxaEntrega = nvl(req.getTaxaEntrega());

        pedido.setTaxaServico(taxaServico);
        pedido.setTaxaEntrega(taxaEntrega);
        pedido.setTotal(subtotal.add(taxaServico).add(taxaEntrega));

        Pedido salvo = pedidoRepository.save(pedido);
        return new PedidoResponseDTO(salvo);
    }

    
    @Transactional
    public Pedido preparar(Long idEstabelecimento, Long idPedido) {
        // Mesma regra do "aceitar": CRIADO -> EM_PREPARO
        return aceitar(idEstabelecimento, idPedido);
    }
    
    @Transactional
    public Pedido cancelar(Long idEstabelecimento, Long idPedido) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        Pedido pedido = pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));

        if (pedido.getEstabelecimento() == null || pedido.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido sem estabelecimento associado");
        }

        if (!Objects.equals(pedido.getEstabelecimento().getId(), idEstabelecimento)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado para o estabelecimento informado");
        }

        StatusPedido st = pedido.getStatus();

        if (st != StatusPedido.CRIADO && st != StatusPedido.EM_PREPARO) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Pedido não pode ser cancelado neste status: " + st
            );
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        return pedidoRepository.save(pedido);
    }
    
    @Transactional(readOnly = true)
    public PedidoResponseDTO buscar(Long idEstabelecimento, Long idPedido) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        // Garante que o estabelecimento existe (e reaproveita sua exceção padrão 404)
        estabelecimentoService.buscar(idEstabelecimento);

        Pedido pedido = pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));

        if (pedido.getEstabelecimento() == null || pedido.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido sem estabelecimento associado");
        }

        if (!Objects.equals(pedido.getEstabelecimento().getId(), idEstabelecimento)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Pedido não encontrado para o estabelecimento informado"
            );
        }

        return new PedidoResponseDTO(pedido);
    }

    
    
    // Usado no detalhe admin (retorna entidade com itens carregados)
    @Transactional(readOnly = true)
    public Pedido buscarEntidadeComItens(Long idEstabelecimento, Long idPedido) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        estabelecimentoService.validarExiste(idEstabelecimento);

        Pedido pedido = pedidoRepository.buscarComItens(idEstabelecimento, idPedido)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));

        // Carrega opcionais sem bater no MultipleBagFetchException
        pedidoRepository.buscarItensComOpcionais(idPedido);

        // (Opcional, mas deixa explícito que está inicializado no mesmo contexto)
        if (pedido.getItens() != null) {
            for (ItemPedido it : pedido.getItens()) {
                if (it != null && it.getOpcionais() != null) {
                    it.getOpcionais().size();
                }
            }
        }

        return pedido;
    }

    
    
    @Transactional
    public Pedido aceitar(Long idEstabelecimento, Long idPedido) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        Pedido pedido = pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));

        if (pedido.getEstabelecimento() == null || pedido.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido sem estabelecimento associado");
        }

        if (!Objects.equals(pedido.getEstabelecimento().getId(), idEstabelecimento)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado para o estabelecimento informado");
        }

        if (pedido.getStatus() != StatusPedido.CRIADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pedido não pode ser aceito neste status: " + pedido.getStatus());
        }

        pedido.setStatus(StatusPedido.EM_PREPARO);
        return pedidoRepository.save(pedido);
    }

    
    
    @Transactional
    public Pedido recusar(Long idEstabelecimento, Long idPedido) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        Pedido pedido = pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));

        if (pedido.getEstabelecimento() == null || pedido.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido sem estabelecimento associado");
        }

        if (!Objects.equals(pedido.getEstabelecimento().getId(), idEstabelecimento)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado para o estabelecimento informado");
        }

        if (pedido.getStatus() != StatusPedido.CRIADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pedido não pode ser recusado neste status: " + pedido.getStatus());
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        return pedidoRepository.save(pedido);
    }

    
    
    @Transactional(readOnly = true)
    public List<Pedido> listarPorStatus(Long idEstabelecimento, StatusPedido status, int offset, int limit) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status é obrigatório");
        }

        estabelecimentoService.validarExiste(idEstabelecimento);

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(1, limit), 50);

        return pedidoRepository.listarPorStatusPaginado(idEstabelecimento, status, safeOffset, safeLimit);
    }

    
    
    @Transactional
    public Pedido iniciarEntrega(Long idEstabelecimento, Long idPedido) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPedido é obrigatório");
        }

        Pedido pedido = pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));

        if (pedido.getEstabelecimento() == null || pedido.getEstabelecimento().getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido sem estabelecimento associado");
        }

        if (!Objects.equals(pedido.getEstabelecimento().getId(), idEstabelecimento)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado para o estabelecimento informado");
        }

        if (pedido.getStatus() != StatusPedido.EM_PREPARO) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Pedido não pode iniciar entrega neste status: " + pedido.getStatus()
            );
        }

        // Não existe EM_ENTREGA no enum -> usamos PRONTO como “saiu para entrega”
        pedido.setStatus(StatusPedido.PRONTO);
        return pedidoRepository.save(pedido);
    }

    
    
    private void validarRequest(PedidoRequestDTO req) {

        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requisição inválida");
        }

        if (req.getIdEstabelecimento() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (req.getCliente() == null || req.getCliente().getTelefone() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cliente.telefone é obrigatório");
        }

        if (req.getTipoAtendimento() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipoAtendimento é obrigatório");
        }

        if (req.getItens() == null || req.getItens().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itens não pode ser vazio");
        }
    }

    
    
    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private int nvlInt(Integer v) {
        return v == null ? 0 : v;
    }

    private StatusPedido definirStatusInicial() {
        return StatusPedido.CRIADO;
    }
}