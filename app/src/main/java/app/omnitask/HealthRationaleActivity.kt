package app.omnitask

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import app.omnitask.model.tr

/**
 * What Health Connect shows when it asks why Omni wants to read sleep (it will not ask without this screen): the one
 * thing read, what for, and that it stays on the phone.
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        setContentView(
            ScrollView(this).apply {
                addView(
                    TextView(this@HealthRationaleActivity).apply {
                        setPadding(pad, pad, pad, pad)
                        textSize = 16f
                        text = tr(
                            "Omni อ่านเฉพาะการนอนจาก Health Connect (เวลาเริ่มนอนและเวลาตื่น ย้อนหลัง 15 วัน)\n\n" +
                                "ใช้เพื่อให้ตารางวันนี้เริ่มหลังเวลาตื่นจริง และหยุดจัดงานก่อนเวลานอนปกติ\n\n" +
                                "ข้อมูลอยู่ในมือถือเครื่องนี้เท่านั้น ไม่เขียนลงโน้ต ไม่ส่งออกไปไหน\n\n" +
                                "เลิกได้ทุกเมื่อ: ปิดสวิตช์ \"อ่านเวลาตื่นและนอนจากนาฬิกา\" ในการตั้งค่าของ Omni หรือถอนสิทธิ์ใน Health Connect",
                            "Omni reads only sleep from Health Connect (when you went to bed and woke up, the last 15 days).\n\n" +
                                "It is used so today's plan starts after you really woke and stops before your usual bedtime.\n\n" +
                                "It stays on this phone: nothing is written to your notes or sent anywhere.\n\n" +
                                "Stop any time: turn off \"Wake and bed times from the watch\" in Omni's settings, or remove the permission in Health Connect.",
                        )
                    },
                )
            },
        )
    }
}
