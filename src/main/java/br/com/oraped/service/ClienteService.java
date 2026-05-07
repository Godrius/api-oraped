// src/main/java/br/com/oraped/service/ClienteService.java
package br.com.oraped.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.Cliente;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.pedido.Pedido;
import br.com.oraped.repository.ClienteRepository;
import br.com.oraped.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteService {

  private final ClienteRepository clienteRepository;
  private final PedidoRepository pedidoRepository;

  @Transactional
  public Cliente obterOuCriar(Estabelecimento estabelecimento, String telefone, String nome) {

    if (estabelecimento == null || estabelecimento.getId() == null) {
      throw new IllegalArgumentException("Estabelecimento inválido");
    }

    String tel = normalizarSomenteDigitos(telefone);
    if (!StringUtils.hasText(tel)) {
      throw new IllegalArgumentException("Telefone do cliente inválido");
    }

    return clienteRepository.findByEstabelecimentoAndTelefone(estabelecimento, tel)
        .map(c -> {
          if (StringUtils.hasText(nome) && !StringUtils.hasText(c.getNome())) {
            c.setNome(nome.trim());
            return clienteRepository.save(c);
          }
          return c;
        })
        .orElseGet(() -> {
          Cliente c = new Cliente();
          c.setEstabelecimento(estabelecimento);
          c.setTelefone(tel);
          c.setNome(StringUtils.hasText(nome) ? nome.trim() : null);
          return clienteRepository.save(c);
        });
  }

  
  
  @Transactional(readOnly = true)
  public Optional<Cliente> buscarPorEstabelecimentoETelefone(Estabelecimento estabelecimento, String telefone) {
    if (estabelecimento == null || !StringUtils.hasText(telefone)) return Optional.empty();
    return clienteRepository.findByEstabelecimentoAndTelefone(estabelecimento, normalizarSomenteDigitos(telefone));
  }

  @Transactional(readOnly = true)
  public Optional<String> buscarUltimoEnderecoEntrega(
      Estabelecimento estabelecimento,
      String telefone
  ) {

    if (estabelecimento == null || !StringUtils.hasText(telefone)) {
      return Optional.empty();
    }

    List<Pedido> pedidos = pedidoRepository.buscarUltimosComEndereco(
        estabelecimento,
        normalizarSomenteDigitos(telefone),
        PageRequest.of(0, 1)
    );

    if (pedidos.isEmpty()) return Optional.empty();

    String endereco = pedidos.get(0).getEnderecoEntrega();

    return StringUtils.hasText(endereco)
        ? Optional.of(endereco.trim())
        : Optional.empty();
  }

  private String normalizarSomenteDigitos(String v) {
    if (!StringUtils.hasText(v)) return null;
    String d = v.replaceAll("\\D+", "").trim();
    return StringUtils.hasText(d) ? d : null;
  }
}