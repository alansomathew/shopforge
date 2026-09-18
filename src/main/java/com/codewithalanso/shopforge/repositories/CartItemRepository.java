package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * CartItem rows are normally reached through their parent Cart (cart.getItems()) -- see
 * CartService, where cascade=ALL + orphanRemoval=true on Cart.items means adding/removing from
 * that Set and saving the Cart is enough to insert/delete the underlying rows. This repository
 * exists for one specific job: looking up a single item directly by its own ID when a request
 * comes in as PATCH/DELETE /api/v1/cart/items/{itemId}, with no Cart ID in the URL at all.
 *
 * findByIdAndCart_User_Id does two things in one query: finds the item AND checks it belongs to
 * a cart owned by this exact user. That matters for security -- without the ownership check, a
 * logged-in user could pass any random item ID and edit/delete someone else's cart line. The
 * underscores (Cart_User_Id) are how you tell Spring Data "walk id -> cart -> user -> id" when
 * the path crosses more than one entity and the plain camelCase parse could be ambiguous.
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    Optional<CartItem> findByIdAndCart_User_Id(UUID id, UUID userId);
}
