package com.buildback.entity;

/** How a material is counted and priced. Stored as text in MySQL. */
public enum MaterialUnit {
    PIECE,
    CAN,
    SHEET,
    /** e.g. a 25 kg bag of POP or waterproofing compound */
    BAG,
    /** e.g. a roll of wallpaper or waterproofing membrane */
    ROLL
}
