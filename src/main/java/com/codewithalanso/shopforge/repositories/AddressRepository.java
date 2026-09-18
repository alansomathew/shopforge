package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.Address;
import com.codewithalanso.shopforge.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Address entity.
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {
    
    List<Address> findByUser(User user);
    
    Optional<Address> findByIdAndUser(UUID id, User user);
}
