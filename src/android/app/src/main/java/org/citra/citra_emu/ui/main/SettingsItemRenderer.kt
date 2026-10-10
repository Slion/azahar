// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.lang.NumberFormatException
import java.text.SimpleDateFormat
import net.slions.compose.toolkit.item
import net.slions.compose.toolkit.itemActionIconButton
import net.slions.compose.toolkit.itemList
import net.slions.compose.toolkit.itemMultiSelectList
import net.slions.compose.toolkit.itemSlider
import net.slions.compose.toolkit.itemSwitch
import net.slions.compose.toolkit.itemTextField
import net.slions.compose.toolkit.section
import org.citra.citra_emu.R
import org.citra.citra_emu.features.settings.model.AbstractIntSetting
import org.citra.citra_emu.features.settings.model.AbstractStringSetting
import org.citra.citra_emu.features.settings.model.FloatSetting
import org.citra.citra_emu.features.settings.model.view.DateTimeSetting
import org.citra.citra_emu.features.settings.model.view.HeaderSetting
import org.citra.citra_emu.features.settings.model.view.InputBindingSetting
import org.citra.citra_emu.features.settings.model.view.MultiChoiceSetting
import org.citra.citra_emu.features.settings.model.view.RunnableSetting
import org.citra.citra_emu.features.settings.model.view.SettingsItem
import org.citra.citra_emu.features.settings.model.view.SingleChoiceSetting
import org.citra.citra_emu.features.settings.model.view.SliderSetting
import org.citra.citra_emu.features.settings.model.view.StringInputSetting
import org.citra.citra_emu.features.settings.model.view.StringSingleChoiceSetting
import org.citra.citra_emu.features.settings.model.view.SubmenuSetting
import org.citra.citra_emu.features.settings.model.view.SwitchSetting
import org.citra.citra_emu.features.settings.ui.SettingsListActions
import org.citra.citra_emu.features.settings.ui.SettingsListBuilder
import org.citra.citra_emu.fragments.AutoMapDialogFragment
import org.citra.citra_emu.fragments.MotionBottomSheetDialogFragment
import kotlin.math.roundToInt

/**
 * An app-drawable row icon. Rasterizes the framework-inflated drawable, like the settings
 * list, so wrapper drawables (e.g. the rotated layout icon) and theme-attribute colors render
 * as-is.
 */
internal fun drawableIcon(drawableResId: Int): @Composable () -> Unit =
    @Composable {
        val context = LocalContext.current
        val bitmap = remember(drawableResId) {
            val sizePx = (24 * context.resources.displayMetrics.density).toInt()
            val drawable = ContextCompat.getDrawable(context, drawableResId)!!.mutate()
            val androidBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(androidBitmap))
            androidBitmap.asImageBitmap()
        }
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }

/**
 * Renders one settings row. Submenu items are handled by the page builders (as nested
 * pages), so this only renders the non-submenu row types.
 */
internal fun LazyListScope.renderSettingsItem(
    index: Int,
    item: SettingsItem,
    activity: Activity,
    builder: SettingsListBuilder,
    actions: SettingsListActions,
    refresh: MutableState<Int>,
) {
    val str: (Int) -> String = activity::getString
    val key = "$index:${item.setting?.key ?: item.nameId}"
    val summary = if (item.descriptionId != 0) str(item.descriptionId) else null
    val disabledClick: () -> Unit = {
        actions.onClickDisabledSetting(!item.isEditable, item.disabledMessage)
    }
    when (item) {
        is HeaderSetting ->
            section(key = key, title = str(item.nameId))

        is SwitchSetting -> {
            val checked = item.isChecked
            itemSwitch(
                key = key,
                value = checked,
                onValueChange = { newValue ->
                    if (item.isActive) {
                        if (checked != newValue) actions.onSettingsChanged()
                        builder.putSetting(item.setChecked(newValue))
                    } else {
                        disabledClick()
                    }
                },
                title = str(item.nameId),
                enabled = item.isActive,
                summary = summary,
            )
        }

        is SingleChoiceSetting -> {
            val names = activity.resources.getStringArray(item.choicesId)
            val values = if (item.valuesId > 0) {
                activity.resources.getIntArray(item.valuesId).toList()
            } else {
                names.indices.toList()
            }
            val selected = item.selectedValue
            val selectedIndex = values.indexOf(selected)
            if (item.setting is AbstractIntSetting) {
                itemList(
                    key = key,
                    value = selected,
                    onValueChange = { newValue ->
                        if (item.isActive) {
                            if (selected != newValue) actions.onSettingsChanged()
                            builder.putSetting(item.setSelectedValue(newValue))
                        } else {
                            disabledClick()
                        }
                    },
                    values = values,
                    title = str(item.nameId),
                    enabled = item.isActive,
                    summary = if (selectedIndex >= 0) names[selectedIndex] else summary,
                    valueToText = { value ->
                        AnnotatedString(names.getOrNull(values.indexOf(value)) ?: value.toString())
                    },
                )
            } else {
                val selectedShort = selected.toShort()
                itemList(
                    key = key,
                    value = selectedShort,
                    onValueChange = { newValue ->
                        if (item.isActive) {
                            if (selectedShort != newValue) actions.onSettingsChanged()
                            builder.putSetting(item.setSelectedValue(newValue))
                        } else {
                            disabledClick()
                        }
                    },
                    values = values.map { it.toShort() },
                    title = str(item.nameId),
                    enabled = item.isActive,
                    summary = if (selectedIndex >= 0) names[selectedIndex] else summary,
                    valueToText = { value ->
                        AnnotatedString(names.getOrNull(values.indexOf(value.toInt())) ?: value.toString())
                    },
                )
            }
        }

        is StringSingleChoiceSetting -> {
            val values = item.values ?: arrayOf("")
            val selected = item.selectedValue
            if (item.setting is AbstractStringSetting) {
                itemList(
                    key = key,
                    value = selected,
                    onValueChange = { newValue ->
                        if (item.isActive) {
                            if (selected != newValue) actions.onSettingsChanged()
                            builder.putSetting(item.setSelectedValue(newValue))
                        } else {
                            disabledClick()
                        }
                    },
                    values = values.toList(),
                    title = str(item.nameId),
                    enabled = item.isActive,
                    summary = if (selected.isNotEmpty()) selected else summary,
                    valueToText = { AnnotatedString(it) },
                )
            } else {
                val selectedIndex = item.selectValueIndex
                itemList(
                    key = key,
                    value = selectedIndex,
                    onValueChange = { newValue ->
                        if (item.isActive) {
                            if (selectedIndex != newValue) actions.onSettingsChanged()
                            builder.putSetting(
                                item.setSelectedValue(
                                    values.getOrElse(newValue) { "" }.toShortOrNull() ?: 1.toShort()
                                )
                            )
                        } else {
                            disabledClick()
                        }
                    },
                    values = values.indices.toList(),
                    title = str(item.nameId),
                    enabled = item.isActive,
                    summary = if (selectedIndex >= 0) item.choices.getOrElse(selectedIndex) { selected } else summary,
                    valueToText = { value ->
                        AnnotatedString(item.choices.getOrElse(value) { value.toString() })
                    },
                )
            }
        }

        is MultiChoiceSetting -> {
            val names = activity.resources.getStringArray(item.choicesId)
            val values = if (item.valuesId > 0) {
                activity.resources.getIntArray(item.valuesId).toList()
            } else {
                names.indices.toList()
            }
            val selected = item.selectedValues
            itemMultiSelectList(
                key = key,
                value = selected.toSet(),
                onValueChange = { newValues ->
                    if (item.isActive) {
                        val sorted = newValues.toList().sorted()
                        if (sorted != selected) {
                            actions.onSettingsChanged()
                            builder.putSetting(item.setSelectedValue(sorted))
                        }
                    } else {
                        disabledClick()
                    }
                },
                values = values,
                title = str(item.nameId),
                enabled = item.isActive,
                summary = summary,
                valueToText = { value ->
                    AnnotatedString(names.getOrNull(values.indexOf(value)) ?: value.toString())
                },
            )
        }

        is SliderSetting -> {
            val value = item.selectedFloat
            val isFloat = item.setting is FloatSetting
            // The Material slider only steps when the range divides evenly, so the grid
            // starts at the first multiple of the step (e.g. 10, 20, ... for a 1..200
            // range with step 10); if that still does not divide, the slider stays
            // continuous and the committed value snaps to the step grid.
            val start = ((item.min + item.step - 1) / item.step) * item.step
            val stepped = !isFloat && (item.max - start) % item.step == 0
            itemSlider(
                key = key,
                value = value,
                onValueChange = { newValue ->
                    if (item.isActive) {
                        if (isFloat) {
                            if (value != newValue) {
                                actions.onSettingsChanged()
                                builder.putSetting(item.setSelectedValue(newValue))
                            }
                        } else {
                            val intValue = ((newValue / item.step).roundToInt() * item.step)
                                .coerceIn(item.min, item.max)
                            if (intValue != value.roundToInt()) {
                                actions.onSettingsChanged()
                                builder.putSetting(item.setSelectedValue(intValue))
                            }
                        }
                    } else {
                        disabledClick()
                    }
                },
                sliderValue = value,
                onSliderValueChange = {},
                title = str(item.nameId),
                // steps counts the intermediate positions (the endpoints are always allowed),
                // so a 10..200 range with step 10 needs 18, not 19.
                valueRange = start.toFloat()..item.max.toFloat(),
                valueSteps = if (stepped) (item.max - start) / item.step - 1 else 0,
                enabled = item.isActive,
                summary = summary,
                valueText = { v ->
                    (if (isFloat) v.toString() else v.roundToInt().toString()) + item.units
                },
            )
        }

        is StringInputSetting -> {
            val value = item.selectedValue
            itemTextField(
                key = key,
                value = value,
                onValueChange = { newValue ->
                    if (item.isActive) {
                        if (value != newValue) actions.onSettingsChanged()
                        builder.putSetting(item.setSelectedValue(newValue))
                    } else {
                        disabledClick()
                    }
                },
                title = str(item.nameId),
                textToValue = { text ->
                    if (item.characterLimit != 0 && text.length > item.characterLimit) null else text
                },
                enabled = item.isActive,
                summary = summary,
                valueToText = { it },
            )
        }

        is DateTimeSetting ->
            item(
                key = key,
                title = str(item.nameId),
                enabled = item.isActive,
                summary = item.value,
                onClick = {
                    if (item.isActive) {
                        showDateTimePickers(activity as FragmentActivity, item) { selected ->
                            if (item.value != selected) actions.onSettingsChanged()
                            builder.putSetting(item.setSelectedValue(selected))
                        }
                    } else {
                        disabledClick()
                    }
                },
            )

        is InputBindingSetting ->
            itemActionIconButton(
                key = key,
                title = str(item.nameId),
                iconButtonIcon = {
                    Icon(imageVector = Icons.Filled.Gamepad, contentDescription = null)
                },
                enabled = item.isActive,
                summary = item.value,
                onClick = { if (!item.isActive) disabledClick() },
                onIconButtonClick = {
                    if (item.isActive) {
                        MotionBottomSheetDialogFragment.newInstance(item, {}) {
                            actions.onSettingsChanged()
                            refresh.value++
                        }.show(
                            (activity as FragmentActivity).supportFragmentManager,
                            MotionBottomSheetDialogFragment.TAG,
                        )
                    } else {
                        disabledClick()
                    }
                },
            )

        is RunnableSetting -> {
            val iconId = item.iconId
            item(
                key = key,
                title = str(item.nameId),
                enabled = item.isActive,
                icon = if (iconId != 0) drawableIcon(iconId) else null,
                summary = item.value?.invoke() ?: summary,
                onClick = {
                    if (item.isActive) {
                        item.runnable()
                    } else {
                        disabledClick()
                    }
                },
            )
        }

        is SubmenuSetting ->
            Unit // Submenu entries are rendered as nested pages by the page builders.

        else ->
            section(key = key, title = str(item.nameId))
    }
}

/** The legacy date and time pickers of [DateTimeSetting], writing back through [onSelected]. */
@SuppressLint("SimpleDateFormat")
private fun showDateTimePickers(
    activity: FragmentActivity,
    item: DateTimeSetting,
    onSelected: (String) -> Unit,
) {
    val storedTime: Long = try {
        java.lang.Long.decode(item.value) * 1000
    } catch (e: NumberFormatException) {
        val date = item.value.substringBefore(" ")
        val time = item.value.substringAfter(" ")
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZZZZ")
        formatter.parse("$date T$time+0000")!!.time
    }
    val calendar = Calendar.getInstance().apply {
        timeInMillis = storedTime
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val timeFormat =
        if (DateFormat.is24HourFormat(activity)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
    val datePicker = MaterialDatePicker.Builder.datePicker()
        .setSelection(storedTime)
        .setTitleText(R.string.select_rtc_date)
        .build()
    val timePicker = MaterialTimePicker.Builder()
        .setTimeFormat(timeFormat)
        .setHour(calendar.get(Calendar.HOUR_OF_DAY))
        .setMinute(calendar.get(Calendar.MINUTE))
        .setTitleText(R.string.select_rtc_time)
        .build()
    datePicker.addOnPositiveButtonClickListener {
        timePicker.show(activity.supportFragmentManager, "TimePicker")
    }
    timePicker.addOnPositiveButtonClickListener {
        var epochTime = datePicker.selection!! / 1000
        epochTime += timePicker.hour.toLong() * 60 * 60
        epochTime += timePicker.minute.toLong() * 60
        onSelected(epochTime.toString())
    }
    datePicker.show(activity.supportFragmentManager, "DatePicker")
}

/** The [SettingsListActions] of the settings screen. */
internal class SettingsActions(
    private val activity: Activity,
    private val refresh: MutableState<Int>,
) : SettingsListActions {
    override var onSettingsChanged: () -> Unit = {}

    override fun onClickAutoMap() {
        AutoMapDialogFragment.newInstance {
            onSettingsChanged()
            refresh.value++
        }.show((activity as FragmentActivity).supportFragmentManager, AutoMapDialogFragment.TAG)
    }

    override fun clearAllBindings() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.controller_clear_all)
            .setMessage(R.string.controller_clear_all_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                InputBindingSetting.clearAllBindings()
                onSettingsChanged()
                refresh.value++
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onClickDisabledSetting(isRuntimeDisabled: Boolean, @StringRes disabledMessage: Int) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(if (isRuntimeDisabled) R.string.setting_not_editable else R.string.setting_disabled)
            .setMessage(disabledMessage)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onThemePrefChanged() {
        activity.recreate()
    }

    override fun refreshList() {
        refresh.value++
    }
}
