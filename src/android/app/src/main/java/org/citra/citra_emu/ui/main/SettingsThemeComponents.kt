// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.slions.compose.toolkit.Item
import net.slions.compose.toolkit.ItemSlider

/**
 * A host-controlled slider preference that shows the current value as a right-aligned
 * **title postfix** (e.g. "Tint        30%") and optionally commits while dragging
 * ([live] = true), so the effect applies in real time.
 *
 * The drag position is its own stable state (not keyed on [value]); keying it on [value]
 * would re-init it on every commit, which — with a live slider — makes the thumb fight
 * the finger and the effect never settles.
 *
 * @param title The title of the row (e.g. `"Tint"`).
 * @param value The committed value.
 * @param onValueChange Called with the new value on release (or while dragging if [live]).
 * @param valueRange The range of values the slider can take.
 * @param valueSteps The number of discrete intermediate stops; 0 for continuous.
 * @param valueText Formats the value for display (e.g. `{ "${it.toInt()}%" }`).
 * @param live Whether to commit while dragging (true) or only on release (false).
 * @param icon Optional leading icon.
 * @param modifier Modifier applied to the row.
 */
@Composable
internal fun LiveItemSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueSteps: Int,
    valueText: (Float) -> String,
    live: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    ItemSlider(
        value = value,
        onValueChange = onValueChange,
        sliderValue = sliderValue,
        onSliderValueChange = {
            sliderValue = it
            if (live) onValueChange(it)
        },
        title = title,
        titlePostfix = { Text(valueText(sliderValue)) },
        valueRange = valueRange,
        valueSteps = valueSteps,
        icon = icon,
        modifier = modifier,
    )
}

/**
 * A color-picker preference row: shows a swatch right-aligned on the row and opens an
 * alert dialog of radio-button swatch rows when clicked.
 *
 * @param value The selected color as a hex string (`"#RRGGBB"`); empty string = platform
 *   default (dynamic colors).
 * @param onValueChange Called with the selected hex (or `""` for the default).
 * @param options The selectable colors, in display order. The first entry is typically
 *   the "default" (null hex).
 * @param title The title of the row.
 * @param summary The summary shown below the title.
 * @param icon Optional leading icon.
 * @param dialogCancelLabel Text for the dialog's cancel button.
 * @param defaultOptionColor The color to show for the "default" option (an entry with a null
 *   hex) in both the row swatch and the dialog. When the default option is selected the
 *   theme is re-derived from the system, so the *current* theme's primary is not a reliable
 *   stand-in for "what the system will use". Pass the platform's actual default accent so
 *   the swatch is always correct. When null, falls back to the current theme's primary.
 * @param modifier Modifier applied to the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorPreference(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<AccentColorOption>,
    title: String,
    summary: String,
    icon: @Composable (() -> Unit)? = null,
    dialogCancelLabel: String = "Cancel",
    defaultOptionColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    var openSelector by rememberSaveable { mutableStateOf(false) }
    // The color to show for the "default" option. When the caller provides the platform's
    // real default accent, use it (stable regardless of which option is currently selected);
    // otherwise fall back to the current theme's primary, which is only correct while the
    // default option is already active.
    val defaultSwatch = defaultOptionColor ?: MaterialTheme.colorScheme.primary
    val swatchColor = if (value.isEmpty()) {
        defaultSwatch
    } else {
        parseHexColor(value)
    }

    if (openSelector) {
        BasicAlertDialog(onDismissRequest = { openSelector = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlertDialogDefaults.titleContentColor,
                        modifier = Modifier
                            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    ) {
                        items(options) { accent ->
                            val accentHex = accent.hex ?: ""
                            val selected = accentHex == value
                            val accentColor = if (accentHex.isEmpty()) {
                                defaultSwatch
                            } else {
                                parseHexColor(accentHex)
                            }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected,
                                            true,
                                            Role.RadioButton,
                                            onClick = {
                                                onValueChange(accentHex)
                                                openSelector = false
                                            },
                                        )
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                    colors =
                                        RadioButtonDefaults.colors(
                                            selectedColor = accentColor,
                                        ),
                                )
                                Spacer(modifier = Modifier.width(24.dp))
                                Text(
                                    text = accent.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier =
                                        Modifier
                                            .size(24.dp)
                                            .background(accentColor, shape = RoundedCornerShape(8.dp)),
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { openSelector = false },
                        modifier =
                            Modifier
                                .align(Alignment.End)
                                .padding(end = 16.dp, bottom = 16.dp),
                    ) {
                        Text(dialogCancelLabel)
                    }
                }
            }
        }
    }

    Item(
        title = title,
        summary = summary,
        icon = icon,
        widgetContainer = {
            Box(
                modifier =
                    Modifier
                        .padding(end = 24.dp)
                        .size(24.dp)
                        .background(swatchColor, shape = RoundedCornerShape(8.dp)),
            )
        },
        onClick = { openSelector = true },
        modifier = modifier,
    )
}

/**
 * A selectable accent-color option for [ColorPreference].
 *
 * @param name The display name (e.g. `"Purple"`).
 * @param hex The hex color string (`"#RRGGBB"`); `null` means the platform default
 *   (dynamic colors).
 */
internal data class AccentColorOption(
    val name: String,
    val hex: String?,
)

/** Parses a `#RRGGBB` / `#AARRGGBB` hex string into a [Color], or [fallback] if invalid. */
internal fun parseHexColor(hex: String, fallback: Color = Color.Unspecified): Color =
    runCatching {
        var h = hex.trim().removePrefix("#")
        if (h.length == 6) h = "FF$h"
        if (h.length == 8) Color(h.toLong(16).toInt()) else fallback
    }.getOrDefault(fallback)
