package com.buildback.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One surplus-material listing = one row in the "materials" table.
 *
 * Money and sizes use BigDecimal + DECIMAL columns (exact), never float/double.
 * The seller, status, timestamps and version are set by the server only;
 * the browser can never change them directly (see MaterialRequest).
 */
@Entity
@Table(name = "materials", indexes = {
        // Public catalogue: "ACTIVE, newest first", "ACTIVE + category", "ACTIVE sorted by price"
        @Index(name = "idx_materials_status_created", columnList = "status, created_at"),
        @Index(name = "idx_materials_status_category", columnList = "status, category"),
        @Index(name = "idx_materials_status_price", columnList = "status, unit_price"),
        // Seller inventory: "my listings"
        @Index(name = "idx_materials_seller_status", columnList = "seller_id, status")
})
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LAZY: the seller row is only loaded when we really need it (detail page). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, foreignKey = @ForeignKey(name = "fk_materials_seller"))
    private User seller;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    /** VARCHAR (not a MySQL ENUM column), so adding a category never needs a table change. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MaterialCategory category;

    /** Price in INR for ONE unit. DECIMAL(10,2): up to 99,999,999.99 */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** Stock buyers can still order (does NOT include reserved stock). */
    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    /**
     * Stock held for unpaid (pending) orders. Paid -> removed for good;
     * cancelled/expired -> moved back to availableQuantity.
     */
    @ColumnDefault("0")
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private MaterialUnit unit;

    @Column(nullable = false, length = 100)
    private String city;

    /** The cover photo (= the first of imageUrls). Kept in this table so catalogue pages need no extra query. */
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    /**
     * All photos, in the seller's order (1 to 5). Stored in a separate table
     * "material_images" (material_id, sort_order, url). LAZY: only loaded when needed.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "material_images", joinColumns = @JoinColumn(name = "material_id"),
            foreignKey = @ForeignKey(name = "fk_material_images_material"))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", nullable = false, length = 2048)
    private List<String> imageUrls = new ArrayList<>();

    /** Only for TILES (needed later by the tile calculator). DECIMAL(5,2) feet. */
    @Column(name = "tile_length_feet", precision = 5, scale = 2)
    private BigDecimal tileLengthFeet;

    @Column(name = "tile_width_feet", precision = 5, scale = 2)
    private BigDecimal tileWidthFeet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MaterialStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking: JPA adds "WHERE version = ?" to every UPDATE and
     * increases the number. If someone else saved first, nothing matches and
     * JPA throws an error instead of silently overwriting their change.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /** Required by JPA. */
    protected Material() {
    }

    public Material(User seller) {
        this.seller = seller;
        this.status = MaterialStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Copies every seller-editable field. Seller, status, timestamps and id are untouched. */
    public void updateDetails(String title, String description, MaterialCategory category, BigDecimal unitPrice,
            Integer availableQuantity, MaterialUnit unit, String city, List<String> imageUrls,
            BigDecimal tileLengthFeet, BigDecimal tileWidthFeet) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.unitPrice = unitPrice;
        this.availableQuantity = availableQuantity;
        this.unit = unit;
        this.city = city;
        this.imageUrls.clear();
        this.imageUrls.addAll(imageUrls);
        this.imageUrl = imageUrls.isEmpty() ? null : imageUrls.get(0);
        this.tileLengthFeet = tileLengthFeet;
        this.tileWidthFeet = tileWidthFeet;
    }

    /** "Delete" for a seller: keep the row, hide it from buyers. */
    public void archive() {
        this.status = MaterialStatus.ARCHIVED;
    }

    public boolean isArchived() {
        return status == MaterialStatus.ARCHIVED;
    }

    // ----- stock movements (always called while this row is locked, see MaterialRepository) -----

    /** A buyer created an order: move stock from available to reserved. */
    public void reserve(int quantity) {
        if (quantity < 1 || quantity > availableQuantity) {
            throw new IllegalStateException("Not enough stock to reserve");
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    /** The order was paid: the reserved stock is sold and leaves the listing. */
    public void completeSale(int quantity) {
        if (quantity > reservedQuantity) {
            throw new IllegalStateException("Reserved stock is lower than the sold quantity");
        }
        reservedQuantity -= quantity;
    }

    /** The order was cancelled or expired: give the stock back to buyers. */
    public void releaseReservation(int quantity) {
        if (quantity > reservedQuantity) {
            throw new IllegalStateException("Reserved stock is lower than the released quantity");
        }
        reservedQuantity -= quantity;
        availableQuantity += quantity;
    }

    public Long getId() {
        return id;
    }

    public User getSeller() {
        return seller;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public MaterialCategory getCategory() {
        return category;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public MaterialUnit getUnit() {
        return unit;
    }

    public String getCity() {
        return city;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    /**
     * Every photo of the listing, cover first. Listings saved before photo
     * uploads existed only have the single image_url column, so fall back to it.
     */
    public List<String> getPhotos() {
        if (!imageUrls.isEmpty()) {
            return List.copyOf(imageUrls);
        }
        return imageUrl == null ? List.of() : List.of(imageUrl);
    }

    public BigDecimal getTileLengthFeet() {
        return tileLengthFeet;
    }

    public BigDecimal getTileWidthFeet() {
        return tileWidthFeet;
    }

    public MaterialStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
