package com.buildback.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.buildback.entity.Material;
import com.buildback.entity.MaterialStatus;

import jakarta.persistence.LockModeType;

/**
 * Database access for materials.
 * JpaSpecificationExecutor lets the service build search filters that the
 * database runs (WHERE ... ORDER BY ... LIMIT), instead of filtering in Java.
 */
public interface MaterialRepository extends JpaRepository<Material, Long>, JpaSpecificationExecutor<Material> {

    /** Ownership check in the query itself: another seller's id simply finds nothing. */
    Optional<Material> findByIdAndSellerId(Long id, Long sellerId);

    /** Public detail page: only ACTIVE rows, and the seller is loaded in the same query. */
    @Query("select m from Material m join fetch m.seller where m.id = :id and m.status = :status")
    Optional<Material> findWithSellerByIdAndStatus(@Param("id") Long id, @Param("status") MaterialStatus status);

    /**
     * SELECT ... FOR UPDATE: locks this row until the transaction ends, so two
     * buyers ordering the last tiles at the same moment are handled one after
     * the other and stock can never go below zero.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Material m where m.id = :id")
    Optional<Material> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(MaterialStatus status);

    long countBySellerIdAndStatus(Long sellerId, MaterialStatus status);
}
