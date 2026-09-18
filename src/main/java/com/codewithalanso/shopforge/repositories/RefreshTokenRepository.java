package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.RefreshToken;
import com.codewithalanso.shopforge.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for RefreshToken entity.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    
    void deleteByUser(User user);
}
