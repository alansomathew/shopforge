package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "roles")
    Optional<User> findByEmailIgnoreCase(String email);
    
    boolean existsByEmailIgnoreCase(String email);
    
    boolean existsByPhone(String phone);
}
