package com.buildback.entity;

/**
 * The four kinds of surplus material BuildBack supports, and the one unit
 * each is sold in (one tile, one can, one sheet, one fitting).
 */
public enum MaterialCategory {
    TILES(MaterialUnit.PIECE),
    PAINT(MaterialUnit.CAN),
    PLYWOOD(MaterialUnit.SHEET),
    FITTINGS(MaterialUnit.PIECE);

    private final MaterialUnit allowedUnit;

    MaterialCategory(MaterialUnit allowedUnit) {
        this.allowedUnit = allowedUnit;
    }

    public MaterialUnit allowedUnit() {
        return allowedUnit;
    }
}
