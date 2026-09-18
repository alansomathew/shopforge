package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for OAuthProvider entity.
 */
@Repository
public interface OAuthProviderRepository extends JpaRepository<OAuthProvider, UUID> {
    
    Optional<OAuthProvider> findByProviderAndProviderUserId(String provider, String providerUserId);
}
