package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * A Spring Data JPA repository. This is one of the more "magic-looking" parts of Spring, so
 * here's what's actually happening: you write an *interface* -- there is no CartRepositoryImpl
 * class anywhere -- and at startup Spring Data generates a real implementation class for you at
 * runtime and registers it as a bean. `extends JpaRepository<Cart, UUID>` alone already gives
 * you save(), findById(), findAll(), delete(), etc. for free.
 *
 * findByUserId below is a "derived query method": Spring Data parses the method NAME itself
 * ("find" + "By" + "UserId") and, because Cart has a `user` field of type User whose `id` field
 * is a UUID, it infers the query `WHERE user_id = ?` without you writing any SQL or JPQL at all.
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUserId(UUID userId);
}
