// src/main/java/br/com/oraped/repository/PedidoRepository.java
package br.com.oraped.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.domain.pedido.ItemPedido;
import br.com.oraped.domain.pedido.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("""
        select p
        from Pedido p
        where p.estabelecimento = :estabelecimento
          and p.clienteTelefone = :telefone
          and p.enderecoEntrega is not null
        order by p.id desc
    """)
    List<Pedido> buscarUltimosComEndereco(
        @Param("estabelecimento") Estabelecimento estabelecimento,
        @Param("telefone") String telefone,
        Pageable pageable
    );

    @Query(
        value = """
            SELECT *
            FROM pedido
            WHERE estabelecimento_id = :idEstabelecimento
              AND status = :status
            ORDER BY id DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    List<Pedido> listarPorStatusPaginado(
        @Param("idEstabelecimento") Long idEstabelecimento,
        @Param("status") String status,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    default List<Pedido> listarPorStatusPaginado(Long idEstabelecimento, StatusPedido status, int offset, int limit) {
        return listarPorStatusPaginado(idEstabelecimento, status.name(), offset, limit);
    }

    // 1) Busca Pedido + itens + produto (SEM opcionais) -> permitido
    @Query("""
        select distinct p
        from Pedido p
        left join fetch p.itens i
        left join fetch i.produto pr
        where p.id = :idPedido
          and p.estabelecimento.id = :idEstabelecimento
    """)
    Optional<Pedido> buscarComItens(
        @Param("idEstabelecimento") Long idEstabelecimento,
        @Param("idPedido") Long idPedido
    );

    // 2) Busca opcionais dos itens (SEM buscar Pedido.itens junto) -> permitido
    @Query("""
        select distinct i
        from ItemPedido i
        left join fetch i.opcionais op
        where i.pedido.id = :idPedido
    """)
    List<ItemPedido> buscarItensComOpcionais(
        @Param("idPedido") Long idPedido
    );
}