package br.com.oraped.repository.relatorios;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import br.com.oraped.domain.enums.StatusPedido;
import br.com.oraped.dto.relatorios.RelatorioBairroAtendidoDTO;
import br.com.oraped.dto.relatorios.RelatorioProdutoVendidoDTO;

@Repository
public class RelatorioEstabelecimentoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public RelatorioEstabelecimentoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long buscarTotalPedidosAtendidos(Long idEstabelecimento, LocalDateTime inicio, LocalDateTime fim) {

        validarPeriodo(idEstabelecimento, inicio, fim);

        String sql =
            "SELECT COUNT(*) AS total " +
            "FROM pedido p " +
            "WHERE p.estabelecimento_id = :idEstabelecimento " +
            "  AND p.criado_em BETWEEN :inicio AND :fim " +
            "  AND p.status IN (:statusesAtendidos)";

        Map<String, Object> params = Map.of(
            "idEstabelecimento", idEstabelecimento,
            "inicio", inicio,
            "fim", fim,
            "statusesAtendidos", statusesAtendidos()
        );

        return queryLong(sql, params);
    }

    public BigDecimal buscarVolumeFinanceiroTotal(Long idEstabelecimento, LocalDateTime inicio, LocalDateTime fim) {

        validarPeriodo(idEstabelecimento, inicio, fim);

        String sql =
            "SELECT COALESCE(SUM(p.total), 0) AS total " +
            "FROM pedido p " +
            "WHERE p.estabelecimento_id = :idEstabelecimento " +
            "  AND p.criado_em BETWEEN :inicio AND :fim " +
            "  AND p.status IN (:statusesAtendidos)";

        Map<String, Object> params = Map.of(
            "idEstabelecimento", idEstabelecimento,
            "inicio", inicio,
            "fim", fim,
            "statusesAtendidos", statusesAtendidos()
        );

        return queryBigDecimal(sql, params);
    }

    /**
     * Cliente novo = cliente cujo PRIMEIRO pedido (PRONTO ou ENTREGUE) foi dentro do período.
     */
    public long buscarTotalClientesNovos(Long idEstabelecimento, LocalDateTime inicio, LocalDateTime fim) {

        validarPeriodo(idEstabelecimento, inicio, fim);

        String sql =
            "SELECT COUNT(*) AS total " +
            "FROM ( " +
            "    SELECT p.cliente_id AS cliente_id, MIN(p.criado_em) AS primeira_data " +
            "    FROM pedido p " +
            "    WHERE p.estabelecimento_id = :idEstabelecimento " +
            "      AND p.status IN (:statusesAtendidos) " +
            "    GROUP BY p.cliente_id " +
            ") t " +
            "WHERE t.primeira_data BETWEEN :inicio AND :fim";

        Map<String, Object> params = Map.of(
            "idEstabelecimento", idEstabelecimento,
            "inicio", inicio,
            "fim", fim,
            "statusesAtendidos", statusesAtendidos()
        );

        return queryLong(sql, params);
    }

    public List<RelatorioProdutoVendidoDTO> buscarTop3ProdutosMaisVendidos(Long idEstabelecimento, LocalDateTime inicio, LocalDateTime fim) {

        validarPeriodo(idEstabelecimento, inicio, fim);

        String sql =
            "SELECT pr.id AS id_produto, " +
            "       pr.nome AS nome_produto, " +
            "       COALESCE(SUM(ip.quantidade), 0) AS quantidade_vendida " +
            "FROM item_pedido ip " +
            "JOIN pedido p ON p.id = ip.pedido_id " +
            "JOIN produto pr ON pr.id = ip.produto_id " +
            "WHERE p.estabelecimento_id = :idEstabelecimento " +
            "  AND p.criado_em BETWEEN :inicio AND :fim " +
            "  AND p.status IN (:statusesAtendidos) " +
            "GROUP BY pr.id, pr.nome " +
            "ORDER BY quantidade_vendida DESC, nome_produto ASC " +
            "LIMIT 3";

        Map<String, Object> params = Map.of(
            "idEstabelecimento", idEstabelecimento,
            "inicio", inicio,
            "fim", fim,
            "statusesAtendidos", statusesAtendidos()
        );

        return jdbc.query(sql, params, new RowMapper<RelatorioProdutoVendidoDTO>() {
            @Override
            public RelatorioProdutoVendidoDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
                Long idProduto = rs.getLong("id_produto");
                String nomeProduto = rs.getString("nome_produto");
                Long quantidadeVendida = rs.getLong("quantidade_vendida");
                return new RelatorioProdutoVendidoDTO(idProduto, nomeProduto, quantidadeVendida);
            }
        });
    }

    public List<RelatorioBairroAtendidoDTO> buscarBairrosAtendidos(Long idEstabelecimento, LocalDateTime inicio, LocalDateTime fim) {

        validarPeriodo(idEstabelecimento, inicio, fim);

        String sql =
            "SELECT COALESCE(NULLIF(TRIM(p.bairro_entrega), ''), '(não informado)') AS bairro, " +
            "       COUNT(*) AS total_pedidos " +
            "FROM pedido p " +
            "WHERE p.estabelecimento_id = :idEstabelecimento " +
            "  AND p.criado_em BETWEEN :inicio AND :fim " +
            "  AND p.status IN (:statusesAtendidos) " +
            "GROUP BY bairro " +
            "ORDER BY total_pedidos DESC, bairro ASC";

        Map<String, Object> params = Map.of(
            "idEstabelecimento", idEstabelecimento,
            "inicio", inicio,
            "fim", fim,
            "statusesAtendidos", statusesAtendidos()
        );

        return jdbc.query(sql, params, new RowMapper<RelatorioBairroAtendidoDTO>() {
            @Override
            public RelatorioBairroAtendidoDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
                String bairro = rs.getString("bairro");
                Long totalPedidos = rs.getLong("total_pedidos");
                return new RelatorioBairroAtendidoDTO(bairro, totalPedidos);
            }
        });
    }

    private List<String> statusesAtendidos() {
        return List.of(StatusPedido.PRONTO.name(), StatusPedido.ENTREGUE.name());
    }

    private void validarPeriodo(Long idEstabelecimento, LocalDateTime inicio, LocalDateTime fim) {
        Assert.notNull(idEstabelecimento, "idEstabelecimento é obrigatório");
        Assert.notNull(inicio, "inicio é obrigatório");
        Assert.notNull(fim, "fim é obrigatório");
        Assert.isTrue(!fim.isBefore(inicio), "fim não pode ser menor que inicio");
    }

    private long queryLong(String sql, Map<String, Object> params) {
        try {
            Long value = jdbc.queryForObject(sql, params, Long.class);
            return value == null ? 0L : value;
        } catch (EmptyResultDataAccessException ex) {
            return 0L;
        }
    }

    private BigDecimal queryBigDecimal(String sql, Map<String, Object> params) {
        try {
            BigDecimal value = jdbc.queryForObject(sql, params, BigDecimal.class);
            return value == null ? BigDecimal.ZERO : value;
        } catch (EmptyResultDataAccessException ex) {
            return BigDecimal.ZERO;
        }
    }
}