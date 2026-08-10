package com.lifeos.auth.repo;

import com.lifeos.auth.domain.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    Optional<OAuthAccount> findByProviderAndProviderSubject(String provider, String providerSubject);

    List<OAuthAccount> findByUserId(UUID userId);

    long countByProvider(String provider);
}
