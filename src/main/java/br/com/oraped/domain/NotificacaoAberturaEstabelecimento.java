package br.com.oraped.domain;

import java.time.OffsetDateTime;

import br.com.oraped.domain.enums.StatusNotificacaoAberturaEstabelecimento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;


/**
 * -----------------------------------------------------------------------
 * REGRA DE NEGÓCIO — NOTIFICAÇÃO DE ABERTURA
 * -----------------------------------------------------------------------
 *
 * Regra funcional:
 *
 * - Um cliente pode solicitar ser avisado quando um estabelecimento abrir.
 * - Para cada (estabelecimento + cliente) só pode existir UMA notificação
 *   com status PENDENTE ao mesmo tempo.
 * - Após o envio, a notificação passa para ENVIADA.
 * - É permitido manter histórico, ou seja:
 *   podemos ter várias ENVIADA para o mesmo cliente/estabelecimento.
 *
 * Problema técnico:
 *
 * MySQL não possui "partial index" (ex: UNIQUE WHERE status = 'PENDENTE').
 * Portanto, não é possível criar uma constraint que restrinja apenas
 * registros com determinado status.
 *
 * Solução adotada:
 *
 * Foi criada a coluna auxiliar "pendenteKey".
 *
 * Funcionamento:
 *
 * - Quando status = PENDENTE  → pendenteKey = 1
 * - Quando status != PENDENTE → pendenteKey = NULL
 *
 * Como UNIQUE em MySQL permite múltiplos NULL,
 * a constraint abaixo garante que:
 *
 * - Só pode existir UM registro com:
 *   (id_estabelecimento, whatsapp_cliente, pendente_key = 1)
 *
 * Ou seja:
 * - Apenas UMA PENDENTE por cliente/estabelecimento
 * - Múltiplas ENVIADA são permitidas (pois pendente_key é NULL)
 *
 * Onde isso é utilizado:
 *
 * - Método: EstabelecimentoService.solicitarNotificacaoQuandoAbrir(...)
 *   → cria registro com status PENDENTE e pendenteKey = 1
 *
 * - Método: EstabelecimentoService.consumirFilaNotificacoesAbertura(...)
 *   → após envio, altera status para ENVIADA e define pendenteKey = null
 *
 * Essa abordagem garante:
 * - Integridade em ambiente concorrente (evita race condition)
 * - Histórico de envios preservado
 * - Simplicidade na consulta por PENDENTE
 *
 * NÃO remover essa constraint sem reavaliar controle de concorrência.
 * -----------------------------------------------------------------------
 */


@Getter
@Setter
@Entity
@Table(
    name = "notificacao_abertura_estabelecimento",
    indexes = {
        @Index(name = "ix_notif_abertura_estab_status", columnList = "status"),
        @Index(name = "ix_notif_abertura_estab_estab", columnList = "id_estabelecimento"),
        @Index(name = "ix_notif_abertura_estab_cli", columnList = "whatsapp_cliente")
    },
	uniqueConstraints = {
		    @UniqueConstraint(
		        name = "uk_notif_abertura_estab_pendente",
		        columnNames = {"id_estabelecimento", "whatsapp_cliente", "pendente_key"}
		    )
		}
)
public class NotificacaoAberturaEstabelecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pendente_key")
    private Integer pendenteKey;
    
    @Column(name = "id_estabelecimento", nullable = false)
    private Long idEstabelecimento;

    @Column(name = "whatsapp_cliente", nullable = false, length = 30)
    private String whatsappCliente;
    
    @Column(name = "phone_number_id", length = 50)
    private String phoneNumberId;

    @Column(name = "wamid_entrada", length = 100)
    private String wamidEntrada;

    @Column(name = "id_correlacao", length = 100)
    private String idCorrelacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusNotificacaoAberturaEstabelecimento status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "enviado_em")
    private OffsetDateTime enviadoEm;
}