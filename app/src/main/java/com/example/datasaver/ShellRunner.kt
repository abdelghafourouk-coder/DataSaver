package com.example.datasaver

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShellRunner {

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun run(cmd: String): Boolean {
        if (!granted()) return false
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            p.waitFor() == 0
        } catch (e: Throwable) {
            false
        }
    }
}
