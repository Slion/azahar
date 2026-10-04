// Copyright 2025-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the android-free dual-screen topology + swap decision
 * ([@link classifyTopology] / [effectiveSwap] / [DisplayPanel]). They encode the on-device
 * signatures of the three supported hosts (LG G8X clamshell, Retroid Pocket Nova add-on,
 * Surface Duo 2 partitioned display) so the swap correction is guarded in CI, not only on the
 * bench. No device, no Android runtime.
 */
class DisplayTopologyTest {
    // --- fixtures --------------------------------------------------------

    // LG G8X: two equal-size non-presentation logical displays (a clamshell).
    private fun g8xPrimary() = DisplayPanel(0, "Built-in Screen", false, 2340, 1080, false, true, true, true)
    private fun g8xSecondary() = DisplayPanel(1, "Cover Screen", false, 2340, 1080, false, true, true, false)

    // Retroid Pocket Nova: built-in (not presentation) + a DP add-on that IS a
    // presentation display. The add-on is the larger, external, top panel.
    private fun novaPrimary() = DisplayPanel(0, "Built-in Screen", false, 960, 1280, false, true, true, true)
    private fun novaSecondary() = DisplayPanel(51, "DP Screen", false, 1920, 1080, true, true, true, false)

    // Surface Duo 2: one logical display (1892x2754); at rotation 1/3 the app window is
    // confined to one capture-space half. physicalWidth is the capture-space height.
    private fun duoPrimary() = DisplayPanel(0, "Built-in Screen", false, 1892, 2754, false, true, true, true)

    private fun topologyFor(vararg panels: DisplayPanel): DisplayTopology {
        val primary = panels.first()
        val candidates = panels.drop(1).filter { it.isUsableSecondary(primary.displayId) }
        // Pass the window bounds in; the real/presentation cases ignore them, and the
        // partitioned cases below call classifyTopology with their own bounds directly.
        return classifyTopology(primary, candidates, 0, 0, primary.physicalWidth, primary.physicalHeight, 1)
    }

    // --- candidate filter (A1: single source of truth) --------------------

    @Test
    fun usableSecondaryExcludesPrimaryHiddenAndOff() {
        val primary = g8xPrimary()
        assertTrue(g8xSecondary().isUsableSecondary(primary.displayId))
        // The primary itself is never a usable secondary.
        assertTrue(!primary.isUsableSecondary(primary.displayId))
        // The hidden virtual display and an off/invalid display are excluded.
        assertTrue(!DisplayPanel(9, "HiddenDisplay", false, 1920, 1080, false, true, true, false)
            .isUsableSecondary(primary.displayId))
        assertTrue(!DisplayPanel(2, "Cover Screen", true, 2340, 1080, false, true, true, false)
            .isUsableSecondary(primary.displayId))
        assertTrue(!DisplayPanel(3, "Cover Screen", false, 2340, 1080, false, false, true, false)
            .isUsableSecondary(primary.displayId))
    }

    // --- topology classification -----------------------------------------

    @Test
    fun classifiesRealPairWhenASecondDisplayExists() {
        val t = topologyFor(g8xPrimary(), g8xSecondary())
        assertTrue(t is DisplayTopology.RealPair)
    }

    @Test
    fun classifiesPartitionedWhenConfinedToTheBottomHalf() {
        // Rotation 1, window on the capture bottom half (top > 0, half height).
        val t = classifyTopology(duoPrimary(), emptyList(), 0, 1410, 1892, 1344, 1)
        assertTrue(t is DisplayTopology.Partitioned)
    }

    @Test
    fun classifiesSingleWhenOnTheTopHalfOrPortrait() {
        // Window on the top half (top == 0) is not a partitioned confinement.
        val t = classifyTopology(duoPrimary(), emptyList(), 0, 0, 1892, 1344, 1)
        assertTrue(t is DisplayTopology.Single)
        // Portrait: no partitioned correction.
        val tp = classifyTopology(duoPrimary(), emptyList(), 0, 1410, 1892, 1344, 0)
        assertTrue(tp is DisplayTopology.Single)
    }

    // --- swap decision: LG G8X clamshell (equal, non-presentation) --------

    @Test
    fun g8xEqualPanelsTopOnUpperPanelAtRotation1() {
        // userSwap=false: top 3DS screen on the upper panel -> no correction at rotation 1.
        assertEquals(false, effectiveSwap(false, topologyFor(g8xPrimary(), g8xSecondary()), 1))
    }

    @Test
    fun g8xEqualPanelsFlippedAtRotation3() {
        // The 180-degree flip puts the other panel on top -> correct to true.
        assertEquals(true, effectiveSwap(false, topologyFor(g8xPrimary(), g8xSecondary()), 3))
    }

    @Test
    fun g8xUserSwapInvertsTheCorrection() {
        // userSwap=true undoes the rotation-based correction.
        assertEquals(true, effectiveSwap(true, topologyFor(g8xPrimary(), g8xSecondary()), 1))
        assertEquals(false, effectiveSwap(true, topologyFor(g8xPrimary(), g8xSecondary()), 3))
    }

    // --- swap decision: G8X launched on the NON-default display ------------
    // The bug: the correction used `rotation == 3` (a device-wide value) and assumed the
    // primary was on display 0. Launched on the cover (display 1) at rot 3, the cover is the
    // UPPER panel, so the correction must be false -- but the old code produced true and put
    // the 3DS top on the wrong (main/bottom) panel.

    private fun coverHostedTopology(): DisplayTopology =
        classifyTopology(g8xSecondary(), listOf(g8xPrimary()), 0, 0, 2340, 1080, 3)

    @Test
    fun g8xCoverHostedAtRotation3NeedsNoCorrection() {
        // host = display 1 (cover), rot 3 -> cover is the UPPER panel -> effective false.
        assertEquals(false, effectiveSwap(false, coverHostedTopology(), 3))
    }

    @Test
    fun g8xCoverHostedAtRotation1IsLower() {
        // host = display 1 (cover), rot 1 -> cover is the LOWER panel -> effective true.
        val t = classifyTopology(g8xSecondary(), listOf(g8xPrimary()), 0, 0, 2340, 1080, 1)
        assertEquals(true, effectiveSwap(false, t, 1))
    }

    @Test
    fun equalPanelPositionIsDisplayAware() {
        // display 0 (default)
        assertEquals(PanelPosition.UPPER, equalPanelPosition(g8xPrimary(), 1))
        assertEquals(PanelPosition.LOWER, equalPanelPosition(g8xPrimary(), 3))
        // display 1 (cover)
        assertEquals(PanelPosition.LOWER, equalPanelPosition(g8xSecondary(), 1))
        assertEquals(PanelPosition.UPPER, equalPanelPosition(g8xSecondary(), 3))
    }

    // --- swap decision: Nova presentation add-on --------------------------

    @Test
    fun novaPresentationAddOnIsTheTopPanel() {
        // The presentation panel is mounted above the built-in one, so with userSwap off the
        // primary (built-in) is the bottom screen: the effective swap must be true. This is the
        // signature that was previously inverted (isPresentation of the wrong display).
        assertEquals(true, effectiveSwap(false, topologyFor(novaPrimary(), novaSecondary()), 1))
    }

    @Test
    fun novaUserSwapInvertsThePresentationCorrection() {
        assertEquals(false, effectiveSwap(true, topologyFor(novaPrimary(), novaSecondary()), 1))
    }

    // --- swap decision: Surface Duo 2 partitioned display -----------------

    @Test
    fun duoConfinedToBottomHalfCorrectsAtRotation1() {
        // Window on the capture bottom half (the lower physical panel): the top 3DS screen
        // must be corrected onto the upper panel -> effective true at rotation 1.
        val t = classifyTopology(duoPrimary(), emptyList(), 0, 1410, 1892, 1344, 1)
        assertEquals(true, effectiveSwap(false, t, 1))
    }

    @Test
    fun duoOnTopHalfNeedsNoCorrection() {
        // Window on the capture top half: the top 3DS screen is already on the upper panel.
        val t = classifyTopology(duoPrimary(), emptyList(), 0, 0, 1892, 1344, 3)
        assertEquals(false, effectiveSwap(false, t, 3))
    }

    @Test
    fun duoUserSwapInvertsThePartitionCorrection() {
        val t = classifyTopology(duoPrimary(), emptyList(), 0, 1410, 1892, 1344, 1)
        assertEquals(false, effectiveSwap(true, t, 1))
    }

    // --- single display: pass-through ------------------------------------

    @Test
    fun singleDisplayPassesUserSwapThrough() {
        val t = DisplayTopology.Single
        assertEquals(false, effectiveSwap(false, t, 0))
        assertEquals(true, effectiveSwap(true, t, 3))
    }
}
