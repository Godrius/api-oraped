package br.com.oraped.repository.marketplace;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.oraped.domain.enums.AbrangenciaEntrega;
import br.com.oraped.domain.marketplace.CategoriaMarketplace;

/**
 * Finalidade:
 * Centralizar as consultas de categorias do marketplace considerando a disponibilidade
 * real dos estabelecimentos para a região do cliente.
 *
 * Aplicação:
 * Utilizado no discovery do marketplace, após a resolução da localização do cliente,
 * para montar apenas o menu de categorias que efetivamente podem atendê-lo.
 *
 * Utilização:
 * Deve ser consumido pelos services de marketplace, evitando espalhar regra regional
 * diretamente no orquestrador do WhatsApp.
 */
public interface CategoriaMarketplaceRepository extends JpaRepository<CategoriaMarketplace, Long> {

    @Query("""
        select distinct c
        from Estabelecimento e
        join e.categoriaMarketplace c
        left join e.bairro bairroBase
        left join e.bairrosAtendidos bairrosAtendidos
        left join bairrosAtendidos.bairro bairroAtendido
        where c.ativa = true
          and e.ativo = true
          and e.aberto = true
          and (
                e.abrangenciaEntrega = :abrangenciaNacional
                or (
                    e.abrangenciaEntrega = :abrangenciaEstado
                    and upper(bairroBase.uf) = upper(:uf)
                )
                or (
                    e.abrangenciaEntrega = :abrangenciaBairro
                    and bairroAtendido.nomeNormalizado = :bairroNormalizado
                    and upper(bairroAtendido.uf) = upper(:uf)
                )
          )
        order by
            case when c.ordem is null then 1 else 0 end,
            c.ordem asc,
            c.nome asc,
            c.id asc
    """)
    List<CategoriaMarketplace> listarCategoriasDisponiveisPorRegiao(
        @Param("abrangenciaNacional") AbrangenciaEntrega abrangenciaNacional,
        @Param("abrangenciaEstado") AbrangenciaEntrega abrangenciaEstado,
        @Param("abrangenciaBairro") AbrangenciaEntrega abrangenciaBairro,
        @Param("uf") String uf,
        @Param("bairroNormalizado") String bairroNormalizado
    );

    @Query("""
        select distinct c
        from Estabelecimento e
        join e.categoriaMarketplace c
        left join e.bairro bairroBase
        left join e.bairrosAtendidos bairrosAtendidos
        left join bairrosAtendidos.bairro bairroAtendido
        where c.ativa = true
          and c.id = :idCategoria
          and e.ativo = true
          and e.aberto = true
          and (
                e.abrangenciaEntrega = :abrangenciaNacional
                or (
                    e.abrangenciaEntrega = :abrangenciaEstado
                    and upper(bairroBase.uf) = upper(:uf)
                )
                or (
                    e.abrangenciaEntrega = :abrangenciaBairro
                    and bairroAtendido.nomeNormalizado = :bairroNormalizado
                    and upper(bairroAtendido.uf) = upper(:uf)
                )
          )
    """)
    Optional<CategoriaMarketplace> buscarCategoriaDisponivelPorIdERegiao(
        @Param("idCategoria") Long idCategoria,
        @Param("abrangenciaNacional") AbrangenciaEntrega abrangenciaNacional,
        @Param("abrangenciaEstado") AbrangenciaEntrega abrangenciaEstado,
        @Param("abrangenciaBairro") AbrangenciaEntrega abrangenciaBairro,
        @Param("uf") String uf,
        @Param("bairroNormalizado") String bairroNormalizado
    );
}