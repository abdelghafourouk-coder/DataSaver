package com.example.datasaver

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var shizukuStatus: TextView
    private lateinit var dndStatus: TextView
    private lateinit var stats: TextView

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        try {
            Shizuku.addRequestPermissionResultListener(permListener)
        } catch (_: Throwable) {
        }

        setContentView(buildUi())
        if (prefs.enabled) startSvc()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        root.addView(label("مُوفر البيانات الذكي", 24f, Color.WHITE))
        root.addView(label("يطفئ البيانات والواي فاي والبلوتوث عند قفل الشاشة ويعيدها عند فتحها.", 13f, Color.parseColor("#94A3B8")))

        root.addView(section("1) الصلاحيات"))
        shizukuStatus = label("", 14f, Color.WHITE)
        root.addView(shizukuStatus)
        root.addView(button("فتح تطبيق Shizuku") { openShizuku() })
        root.addView(button("منح إذن Shizuku لهذا التطبيق") { requestShizuku() })

        dndStatus = label("", 14f, Color.WHITE)
        root.addView(dndStatus)
        root.addView(button("منح إذن عدم الإزعاج (للوضع الصامت)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
        root.addView(button("استثناء التطبيق من تحسين البطارية") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        root.addView(section("2) ماذا يُطفأ عند قفل الشاشة"))
        root.addView(toggle("بيانات الهاتف", prefs.data) { prefs.data = it })
        root.addView(toggle("الواي فاي (قد يقطع الإشعارات)", prefs.wifi) { prefs.wifi = it })
        root.addView(toggle("البلوتوث", prefs.bluetooth) { prefs.bluetooth = it })
        root.addView(toggle("الوضع الصامت", prefs.silent) { prefs.silent = it })

        root.addView(section("3) شروط الأمان"))
        root.addView(toggle("لا تطفئ أثناء تشغيل صوت أو موسيقى", prefs.skipMusic) { prefs.skipMusic = it })
        root.addView(toggle("لا تطفئ أثناء الشحن", prefs.skipCharging) { prefs.skipCharging = it })

        root.addView(label("التأخير بعد قفل الشاشة:", 14f, Color.WHITE))
        root.addView(delaySpinner())

        root.addView(section("4) التشغيل"))
        val master = toggle("تفعيل الخدمة", prefs.enabled) { on ->
            prefs.enabled = on
            if (on) startSvc() else stopService(Intent(this, ScreenService::class.java))
        }
        root.addView(master)

        stats = label("", 14f, Color.parseColor("#7DD3FC"))
        root.addView(stats)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            addView(root)
        }
    }

    private fun delaySpinner(): Spinner {
        val labels = arrayOf("فوري", "30 ثانية", "دقيقة", "دقيقتان", "5 دقائق")
        val values = intArrayOf(0, 30, 60, 120, 300)
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(values.indexOf(prefs.delaySec).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.delaySec = values[pos]
                }

                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
    }

    private fun refresh() {
        shizukuStatus.text = when {
            !ShellRunner.running() -> "Shizuku: ❌ غير مشغّل (شغّله أولاً من تطبيق Shizuku)"
            !ShellRunner.granted() -> "Shizuku: ⚠️ يعمل لكن لم تمنح الإذن لهذا التطبيق"
            else -> "Shizuku: ✅ جاهز"
        }
        val nm = getSystemService(android.app.NotificationManager::class.java)
        dndStatus.text = if (nm.isNotificationPolicyAccessGranted) {
            "عدم الإزعاج: ✅ ممنوح"
        } else {
            "عدم الإزعاج: ⚠️ غير ممنوح (مطلوب للوضع الصامت فقط)"
        }
        stats.text = "عدد مرات التنفيذ الفعلية: ${prefs.count}"
    }
        private fun requestShizuku() {
        try {
            when {
                !Shizuku.pingBinder() ->
                    Toast.makeText(this, "شغّل Shizuku أولاً", Toast.LENGTH_LONG).show()
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                    Toast.makeText(this, "الإذن ممنوح مسبقاً", Toast.LENGTH_SHORT).show()
                else -> Shizuku.requestPermission(100)
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "تعذر الوصول إلى Shizuku", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun openShizuku() {
        val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (i != null) {
            startActivity(i)
        } else {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                )
            )
        }
    }

    private fun startSvc() {
        startForegroundService(Intent(this, ScreenService::class.java))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun section(text: String) = label(text, 17f, Color.parseColor("#60A5FA")).apply {
        setPadding(0, dp(22), 0, dp(6))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun toggle(text: String, initial: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.WHITE)
        isChecked = initial
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }
}
