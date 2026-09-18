package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.UserRole;
import com.codewithalanso.shopforge.entities.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for UserRole entity.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
