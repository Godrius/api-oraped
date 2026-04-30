package br.com.oraped.domain.carrinho;

import java.util.ArrayList;
import java.util.List;

import br.com.oraped.domain.BaseEntity;
import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Finalidade:
 * Representar o carrinho temporário de uma sessão antes da criação do pedido.
 *
 * Aplicação:
 * Usado para manter os itens escolhidos pelo cliente durante o fluxo de compra.
 *
 * Utilização:
 * Cada sessão ativa deve possuir no máximo um carrinho em andamento.
 */
@Getter
@Setter
@Entity
@Table(
    name = "carrinho",
    indexes = {
        @Index(name = "idx_carrinho_sessao", columnList = "sessao_id")
    }
)
public class Carrinho extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_id", nullable = false)
    private SessaoAtendimentoWhatsapp sessao;

    @OneToMany(mappedBy = "carrinho", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCarrinho> itens = new ArrayList<>();
}