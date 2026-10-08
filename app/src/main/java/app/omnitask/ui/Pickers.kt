package app.omnitask.ui

import android.app.TimePickerDialog
import android.content.Context
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The phone's own time picker (the clock dial on ColorOS), which the owner prefers to typing digits.
 * Always 24-hour, like the times written into the vault.
 */
fun pickSystemTime(context: Context, initial: LocalTime, onPick: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        android.R.style.Theme_DeviceDefault_Dialog,
        { _, hour, minute -> onPick(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        true,
    ).show()
}

/** The phone's own date picker, starting on [initial]. */
fun pickSystemDate(context: Context, initial: kotlinx.datetime.LocalDate, onPick: (kotlinx.datetime.LocalDate) -> Unit) {
    android.app.DatePickerDialog(
        context,
        android.R.style.Theme_DeviceDefault_Dialog,
        { _, year, month, day -> onPick(kotlinx.datetime.LocalDate(year, month + 1, day)) },
        initial.year,
        initial.monthNumber - 1,
        initial.dayOfMonth,
    ).show()
}
