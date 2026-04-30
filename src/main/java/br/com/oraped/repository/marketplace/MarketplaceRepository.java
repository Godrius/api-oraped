package br.com.oraped.repository.marketplace;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.marketplace.Marketplace;

public interface MarketplaceRepository extends JpaRepository<Marketplace, Long> {

    boolean existsByWhatsapp(String whatsapp);

    Optional<Marketplace> findByWhatsappAndAtivoTrue(String whatsapp);
}