package com.buildback.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.buildback.entity.OrderStatus;
import com.buildback.entity.PurchaseOrder;

import jakarta.persistence.LockModeType;

/**
 * Orders. @EntityGraph loads the material, its seller and the buyer in the same
 * query as the page of orders, so showing a list never runs one query per row.
 */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /** Locks the order row (SELECT ... FOR UPDATE) while its status changes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = { "material", "material.seller", "buyer" })
    Optional<PurchaseOrder> findWithDetailsById(Long id);

    // ----- buyer: own orders -----
    @EntityGraph(attributePaths = { "material", "material.seller", "buyer" })
    Page<PurchaseOrder> findByBuyerId(Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = { "material", "material.seller", "buyer" })
    Page<PurchaseOrder> findByBuyerIdAndStatus(Long buyerId, OrderStatus status, Pageable pageable);

    // ----- seller: orders for listings they own -----
    @EntityGraph(attributePaths = { "material", "material.seller", "buyer" })
    Page<PurchaseOrder> findByMaterialSellerId(Long sellerId, Pageable pageable);

    @EntityGraph(attributePaths = { "material", "material.seller", "buyer" })
    Page<PurchaseOrder> findByMaterialSellerIdAndStatus(Long sellerId, OrderStatus status, Pageable pageable);

    // ----- admin: every order -----
    @EntityGraph(attributePaths = { "material", "material.seller", "buyer" })
    Page<PurchaseOrder> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = { "material", "material.seller", "buyer" })
    Page<PurchaseOrder> findByStatus(OrderStatus status, Pageable pageable);

    /** Unpaid orders whose reservation time is over. */
    @Query("select o.id from PurchaseOrder o where o.status = :status and o.expiresAt < :now")
    List<Long> findIdsByStatusAndExpiresAtBefore(@Param("status") OrderStatus status, @Param("now") Instant now);

    // ----- dashboard totals -----
    long countByStatus(OrderStatus status);

    long countByBuyerIdAndStatus(Long buyerId, OrderStatus status);

    long countByMaterialSellerIdAndStatus(Long sellerId, OrderStatus status);

    @Query("select coalesce(sum(o.totalAmount), 0) from PurchaseOrder o where o.status in :statuses")
    BigDecimal sumTotalByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.totalAmount), 0) from PurchaseOrder o "
            + "where o.material.seller.id = :sellerId and o.status in :statuses")
    BigDecimal sumSellerTotalByStatusIn(@Param("sellerId") Long sellerId,
            @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.totalAmount), 0) from PurchaseOrder o "
            + "where o.buyer.id = :buyerId and o.status in :statuses")
    BigDecimal sumBuyerTotalByStatusIn(@Param("buyerId") Long buyerId,
            @Param("statuses") Collection<OrderStatus> statuses);
}
