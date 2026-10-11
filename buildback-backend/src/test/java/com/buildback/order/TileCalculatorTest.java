package com.buildback.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.buildback.service.TileCalculatorService;

/** The calculator formula on its own (no database, no Spring). */
class TileCalculatorTest {

    private static long tiles(String area, int allowance, String length, String width) {
        return TileCalculatorService.requiredTiles(new BigDecimal(area), allowance, new BigDecimal(length),
                new BigDecimal(width));
    }

    @Test
    void proposalExample() {
        // 60 x 1.10 = 66 sq ft; 66 / 4 = 16.5 -> 17
        assertThat(tiles("60", 10, "2.00", "2.00")).isEqualTo(17);
    }

    @Test
    void exactFitIsNotRoundedUp() {
        assertThat(tiles("40", 0, "2", "2")).isEqualTo(10);
        assertThat(tiles("100", 0, "1", "1")).isEqualTo(100);
    }

    @Test
    void anyPartialTileNeedsAWholeTile() {
        assertThat(tiles("10", 10, "1.50", "1.00")).isEqualTo(8); // 11 / 1.5 = 7.33 -> 8
        assertThat(tiles("4.01", 0, "2", "2")).isEqualTo(2);      // 1.0025 -> 2
    }

    @Test
    void decimalsStayExact() {
        // 0.1-style floating point errors would give 3 here; BigDecimal gives exactly 3.0 -> 3
        assertThat(tiles("0.3", 0, "0.1", "1")).isEqualTo(3);
    }
}
