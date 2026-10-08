package app.omnitask.ui

import android.app.TimePickerDialog
import android.content.Context
import java.time.LocalTime

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
