package com.buildback.entity;

import java.util.List;

/**
 * The kinds of surplus material BuildBack supports, and the unit(s) each is
 * sold in. Most categories have exactly one unit (one tile, one can, one sheet);
 * the newer ones need a few, e.g. POP is sold per bag but gypsum boards per sheet.
 * The FIRST unit in the list is the usual one (the form preselects it).
 */
public enum MaterialCategory {
    TILES(MaterialUnit.PIECE),
    PAINT(MaterialUnit.CAN),
    PLYWOOD(MaterialUnit.SHEET),
    FITTINGS(MaterialUnit.PIECE),
    /** POP & false ceiling: gypsum boards, POP bags, channels and ceiling tiles */
    FALSE_CEILING(MaterialUnit.SHEET, MaterialUnit.BAG, MaterialUnit.PIECE),
    /** Waterproofing: liquid coatings, cement-based compounds, membrane rolls */
    WATERPROOFING(MaterialUnit.CAN, MaterialUnit.BAG, MaterialUnit.ROLL),
    /** Interior & decor: lights, wallpaper, wall panels, laminates */
    INTERIOR(MaterialUnit.PIECE, MaterialUnit.ROLL, MaterialUnit.SHEET);

    private final List<MaterialUnit> allowedUnits;

    MaterialCategory(MaterialUnit... allowedUnits) {
        this.allowedUnits = List.of(allowedUnits);
    }

    public List<MaterialUnit> allowedUnits() {
        return allowedUnits;
    }

    public boolean allows(MaterialUnit unit) {
        return allowedUnits.contains(unit);
    }
}
