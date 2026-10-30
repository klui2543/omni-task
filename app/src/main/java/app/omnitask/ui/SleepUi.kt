package app.omnitask.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.omnitask.model.DayFill
import app.omnitask.model.SleepSession
import app.omnitask.model.SleepTimes
import app.omnitask.model.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import app.omnitask.time.*

/**
 * "อ่านเวลาตื่นและนอนจากนาฬิกา" (owner's pick, 2026-10-30): the watch's sleeps, from Samsung Health through Health
 * Connect on this phone, give the day plan its real wake and bed times. A Settings switch, off at first; this is its one
 * reader. Nothing read here leaves the phone.
 */
object SleepMode {
    private const val PREFS = "omni-sleep"
    private const val ON = "on"
    /** Read again at most this often, unless asked (the switch, a new permission). */
    private const val EVERY_MS = 10 * 60_000L

    enum class Status { OFF, READING, NO_APP, UPDATE, NO_PERMISSION, OK, FAILED }

    val PERMISSIONS = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    var on by mutableStateOf(false)
        private set
    /** The sleeps of the last [SleepTimes.DAYS] days and one more; null while the switch is off or nothing was read. */
    var sleeps by mutableStateOf<List<SleepSession>?>(null)
        private set
    var status by mutableStateOf(Status.OFF)
        private set
    /** What went wrong on the last read, said in Settings. */
    var failure by mutableStateOf<String?>(null)
        private set
    private var loaded = false
    private var readAt = 0L

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ON, false)
        status = if (on) Status.READING else Status.OFF
    }

    fun setOn(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ON, value).apply()
        readAt = 0L
        if (!value) {
            sleeps = null
            status = Status.OFF
            failure = null
        } else status = Status.READING
    }

    /** What the day plan reads: null while the switch is off, so the plan stays as it was. */
    val forPlan: List<SleepSession>? get() = if (on) sleeps else null

    /** Why nothing could be read, said on the plan's "ตื่น" and "นอน" rows as well as in Settings. */
    val problem: String?
        get() = if (!on) null else when (status) {
            Status.NO_APP -> tr("มือถือเปิด Health Connect ไม่ได้", "Health Connect is not available")
            Status.UPDATE -> tr("ต้องอัปเดต Health Connect ก่อน", "Health Connect needs an update")
            Status.NO_PERMISSION -> tr("ยังไม่ได้อนุญาตให้อ่านการนอน (ดูในตั้งค่า)", "reading sleep is not allowed yet (see Settings)")
            Status.FAILED -> tr("อ่านข้อมูลการนอนไม่ได้ (ดูในตั้งค่า)", "sleep could not be read (see Settings)")
            else -> null
        }

    /** Reads the sleeps from Health Connect, at most every 10 minutes unless [force]. */
    suspend fun refresh(context: Context, force: Boolean = false) {
        load(context)
        if (!on) return
        val nowMs = System.currentTimeMillis()
        if (!force && nowMs - readAt < EVERY_MS) return
        readAt = nowMs
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> Unit
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                status = Status.UPDATE
                sleeps = emptyList()
                readAt = 0L
                return
            }
            else -> {
                status = Status.NO_APP
                sleeps = emptyList()
                return
            }
        }
        try {
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)) {
                status = Status.NO_PERMISSION
                sleeps = emptyList()
                // Asked again on the next visit, so a permission given in Health Connect itself shows at once.
                readAt = 0L
                return
            }
            val end = Instant.now()
            val read = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(end.minus(Duration.ofDays(SleepTimes.DAYS + 1L)), end)),
            )
            val zone = ZoneId.systemDefault()
            sleeps = read.records.map {
                SleepSession(
                    java.time.LocalDateTime.ofInstant(it.startTime, zone).toKotlinLocalDateTime(),
                    java.time.LocalDateTime.ofInstant(it.endTime, zone).toKotlinLocalDateTime(),
                )
            }.sortedBy { it.start }
            status = Status.OK
            failure = null
        } catch (e: CancellationException) {
            readAt = 0L
            throw e
        } catch (e: Exception) {
            // Said in Settings and on the plan's "ตื่น" and "นอน" rows ([problem]); the plan uses the profile's times.
            status = Status.FAILED
            failure = e.message ?: e.javaClass.simpleName
            sleeps = emptyList()
            readAt = 0L
        }
    }
}

/** The switch under "เลขาจัดตารางวันนี้", and what came in from the watch. */
@Composable
fun SleepSetting(context: Context) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { SleepMode.refresh(context, force = true) }
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { SleepMode.refresh(context, force = true) }
    }
    SettingSwitch(
        tr("อ่านเวลาตื่นและนอนจากนาฬิกา", "Wake and bed times from the watch"),
        tr(
            "ผ่าน Health Connect อ่านเฉพาะการนอน ข้อมูลอยู่ในมือถือ ไม่ส่งออกไปไหน  ปิด: ใช้เวลาจากโปรไฟล์",
            "Through Health Connect, sleep only, kept on this phone. Off: the profile's times",
        ),
        SleepMode.on,
    ) { v ->
        SleepMode.setOn(context, v)
        if (v) scope.launch {
            SleepMode.refresh(context, force = true)
            if (SleepMode.status == SleepMode.Status.NO_PERMISSION) ask.launch(SleepMode.PERMISSIONS)
        }
    }
    if (!SleepMode.on) return
    val muted = Modifier.padding(bottom = 12.dp)
    when (SleepMode.status) {
        SleepMode.Status.OFF, SleepMode.Status.READING -> Text(tr("กำลังอ่านข้อมูลการนอน", "Reading sleep"), muted, color = C.muted, fontSize = TS.caption)
        SleepMode.Status.NO_APP -> Text(
            tr("มือถือนี้เปิด Health Connect ไม่ได้ ตารางใช้เวลาจากโปรไฟล์ไปก่อน", "Health Connect is not available on this phone: the plan uses the profile's times"),
            muted, color = C.muted, fontSize = TS.caption,
        )
        SleepMode.Status.UPDATE -> Text(
            tr("ต้องอัปเดต Health Connect ใน Play Store ก่อน ตารางใช้เวลาจากโปรไฟล์ไปก่อน", "Update Health Connect in the Play Store first: the plan uses the profile's times"),
            muted, color = C.muted, fontSize = TS.caption,
        )
        SleepMode.Status.NO_PERMISSION -> Text(
            tr("ยังไม่ได้อนุญาตให้อ่านการนอน แตะเพื่ออนุญาต", "Not allowed to read sleep yet: tap to allow"),
            Modifier.padding(bottom = 12.dp).clip(RoundedCornerShape(6.dp)).clickable { ask.launch(SleepMode.PERMISSIONS) }.padding(vertical = 4.dp),
            color = C.accentText, fontSize = TS.caption,
        )
        SleepMode.Status.FAILED -> Text(
            tr("อ่านข้อมูลการนอนไม่ได้: ", "Could not read sleep: ") + (SleepMode.failure ?: ""),
            muted, color = C.red, fontSize = TS.caption,
        )
        SleepMode.Status.OK -> {
            val sleeps = SleepMode.sleeps.orEmpty()
            val today = LocalDate.now()
            val last = SleepTimes.night(sleeps, today.minusDays(1))
            val nights = (1..SleepTimes.DAYS).count { SleepTimes.night(sleeps, today.minusDays(it.toLong())) != null }
            val text = when {
                sleeps.isEmpty() -> tr(
                    "ยังไม่มีข้อมูลการนอนใน Health Connect ดูใน Samsung Health > การตั้งค่า > Health Connect ว่าเปิดส่ง การนอน ไว้",
                    "No sleep in Health Connect yet: in Samsung Health, Settings > Health Connect, let it share sleep",
                )
                last == null -> tr("เมื่อคืนยังไม่มีข้อมูล มีข้อมูล $nights คืนใน 14 วัน", "Nothing for last night yet; $nights nights in the last 14 days")
                else -> {
                    val m = last.minutes
                    tr("เมื่อคืน นอน ", "Last night: bed ") + DayFill.hm(DayFill.minutesOf(last.start, last.start.date)) +
                        tr(" ตื่น ", ", up ") + DayFill.hm(DayFill.minutesOf(last.end, last.end.date)) +
                        tr(" (${m / 60} ชม. ${m % 60} นาที)", " (${m / 60} h ${m % 60} min)") +
                        tr("\nมีข้อมูล $nights คืนใน 14 วัน", "\n$nights nights in the last 14 days")
                }
            }
            Text(text, muted, color = C.muted, fontSize = TS.caption)
        }
    }
}
