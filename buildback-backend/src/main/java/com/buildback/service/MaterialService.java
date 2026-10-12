package com.buildback.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buildback.dto.MaterialDetailResponse;
import com.buildback.dto.MaterialRequest;
import com.buildback.dto.MaterialSummaryResponse;
import com.buildback.dto.PageResponse;
import com.buildback.dto.SellerMaterialResponse;
import com.buildback.entity.Material;
import com.buildback.entity.MaterialCategory;
import com.buildback.entity.MaterialStatus;
import com.buildback.entity.User;
import com.buildback.exception.ConflictException;
import com.buildback.exception.InvalidRequestException;
import com.buildback.exception.ResourceNotFoundException;
import com.buildback.repository.MaterialRepository;
import com.buildback.repository.UserRepository;

/**
 * Business rules for material listings.
 *
 * Every seller method receives the seller's id from the logged-in session
 * (never from the request body) and looks materials up by id AND seller id,
 * so one seller can never read or change another seller's listing.
 */
@Service
public class MaterialService {

    static final String NOT_FOUND = "Material not found";
    static final String STALE = "This listing was changed after you opened it. Reload it to see the latest version, then try again.";
    static final String READ_ONLY = "Archived listings are read-only and cannot be edited.";

    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorage;

    public MaterialService(MaterialRepository materialRepository, UserRepository userRepository,
            ImageStorageService imageStorage) {
        this.materialRepository = materialRepository;
        this.userRepository = userRepository;
        this.imageStorage = imageStorage;
    }

    // ---------------- Public (buyers and visitors) ----------------

    @Transactional(readOnly = true)
    public PageResponse<MaterialSummaryResponse> search(MaterialSearchCriteria criteria) {
        return PageResponse.from(materialRepository
                .findAll(MaterialSpecifications.publicSearch(criteria), criteria.pageable())
                .map(MaterialSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public MaterialDetailResponse getPublic(Long id) {
        return materialRepository.findWithSellerByIdAndStatus(id, MaterialStatus.ACTIVE)
                .map(MaterialDetailResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    // ---------------- Seller (own listings only) ----------------

    @Transactional(readOnly = true)
    public PageResponse<SellerMaterialResponse> listOwn(Long sellerId, String status, String page, String size) {
        Map<String, String> errors = new LinkedHashMap<>();
        MaterialStatus statusFilter = QueryParams.enumValue(MaterialStatus.class, status, "status", errors);
        int cleanPage = QueryParams.page(page, errors);
        int cleanSize = QueryParams.size(size, 10, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        PageRequest pageable = PageRequest.of(cleanPage, cleanSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(materialRepository
                .findAll(MaterialSpecifications.ownedBy(sellerId, statusFilter), pageable)
                .map(SellerMaterialResponse::from));
    }

    @Transactional(readOnly = true)
    public SellerMaterialResponse getOwn(Long sellerId, Long id) {
        return SellerMaterialResponse.from(findOwned(sellerId, id));
    }

    @Transactional
    public SellerMaterialResponse create(Long sellerId, MaterialRequest request) {
        checkCrossFieldRules(request, true);

        User seller = userRepository.getReferenceById(sellerId);
        Material material = new Material(seller);
        copyFields(request, material);
        return SellerMaterialResponse.from(materialRepository.saveAndFlush(material));
    }

    @Transactional
    public SellerMaterialResponse update(Long sellerId, Long id, MaterialRequest request) {
        checkCrossFieldRules(request, false);

        Material material = findOwned(sellerId, id);
        if (material.isArchived()) {
            throw new ConflictException(READ_ONLY);
        }
        // The form was based on an older version: someone saved in between
        if (!material.getVersion().equals(request.version())) {
            throw new ConflictException(STALE);
        }

        copyFields(request, material);
        // Write now. If another request saved between our read and this write,
        // @Version makes JPA throw ObjectOptimisticLockingFailureException (409).
        materialRepository.flush();
        return SellerMaterialResponse.from(material);
    }

    /** "Delete" = archive. The row stays for future order history. */
    @Transactional
    public void archive(Long sellerId, Long id, Long version) {
        Material material = findOwned(sellerId, id);
        if (material.isArchived()) {
            return; // already archived: repeating the request is harmless
        }
        if (!material.getVersion().equals(version)) {
            throw new ConflictException(STALE);
        }
        material.archive();
        materialRepository.flush();
    }

    // ---------------- helpers ----------------

    /** Missing and "belongs to someone else" both give the same 404. */
    private Material findOwned(Long sellerId, Long id) {
        return materialRepository.findByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private static void copyFields(MaterialRequest r, Material m) {
        m.updateDetails(r.title(), r.description(), r.category(), r.unitPrice(), r.availableQuantity(), r.unit(),
                r.city(), r.imageUrls(), r.tileLengthFeet(), r.tileWidthFeet());
    }

    /**
     * Rules that need more than one field, so simple annotations cannot express them:
     *  1. the unit must be allowed for the category (Tiles -> PIECE, Paint -> CAN, POP -> SHEET/BAG/PIECE ...)
     *  2. Tiles need both tile sizes; other categories must not send any
     *  3. a new listing needs at least 1 item (an edit may set 0 = out of stock)
     *  4. an edit must say which version it was based on
     *  5. every uploaded photo must really exist on this server
     */
    private void checkCrossFieldRules(MaterialRequest r, boolean creating) {
        Map<String, String> errors = new LinkedHashMap<>();
        MaterialCategory category = r.category();

        if (category != null && r.unit() != null && !category.allows(r.unit())) {
            errors.put("unit", "Unit for " + category + " must be one of " + category.allowedUnits());
        }

        if (category == MaterialCategory.TILES) {
            if (r.tileLengthFeet() == null) {
                errors.put("tileLengthFeet", "Tile length (feet) is required for tiles");
            }
            if (r.tileWidthFeet() == null) {
                errors.put("tileWidthFeet", "Tile width (feet) is required for tiles");
            }
        } else if (category != null) {
            if (r.tileLengthFeet() != null) {
                errors.put("tileLengthFeet", "Tile sizes are only allowed for the Tiles category");
            }
            if (r.tileWidthFeet() != null) {
                errors.put("tileWidthFeet", "Tile sizes are only allowed for the Tiles category");
            }
        }

        if (creating && r.availableQuantity() != null && r.availableQuantity() < 1) {
            errors.put("availableQuantity", "A new listing needs a quantity of at least 1");
        }
        if (!creating && r.version() == null) {
            errors.put("version", "Version is missing. Reload the listing and try again.");
        }
        if (r.imageUrls() != null && r.imageUrls().stream()
                .anyMatch(url -> ImageStorageService.isUploadedImageUrl(url) && !imageStorage.exists(url))) {
            errors.put("imageUrls", "A photo could not be found. Remove it and upload it again.");
        }

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
    }
}
