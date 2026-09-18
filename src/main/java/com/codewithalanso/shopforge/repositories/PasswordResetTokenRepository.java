package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.PasswordResetToken;
import com.codewithalanso.shopforge.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PasswordResetToken entity.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    
    void deleteByUser(User user);
}
