package com.example.datasaver

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("datasaver", Context.MODE_PRIVATE)

    private fun b(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun putB(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun i(key: String, def: Int) = sp.getInt(key, def)
    private fun putI(key: String, v: Int) = sp.edit().putInt(key, v).apply()

    var data: Boolean
        get() = b("data", true)
        set(v) = putB("data", v)

    var wifi: Boolean
        get() = b("wifi", false)
        set(v) = putB("wifi", v)

    var bluetooth: Boolean
        get() = b("bluetooth", true)
        set(v) = putB("bluetooth", v)

    var silent: Boolean
        get() = b("silent", false)
        set(v) = putB("silent", v)

    var skipMusic: Boolean
        get() = b("skipMusic", true)
        set(v) = putB("skipMusic", v)

    var skipCharging: Boolean
        get() = b("skipCharging", false)
        set(v) = putB("skipCharging", v)

    var delaySec: Int
        get() = i("delaySec", 60)
        set(v) = putI("delaySec", v)

    var enabled: Boolean
        get() = b("enabled", false)
        set(v) = putB("enabled", v)

    var offData: Boolean
        get() = b("offData", false)
        set(v) = putB("offData", v)

    var offWifi: Boolean
        get() = b("offWifi", false)
        set(v) = putB("offWifi", v)

    var offBluetooth: Boolean
        get() = b("offBluetooth", false)
        set(v) = putB("offBluetooth", v)

    var offSilent: Boolean
        get() = b("offSilent", false)
        set(v) = putB("offSilent", v)

    var prevRinger: Int
        get() = i("prevRinger", 2)
        set(v) = putI("prevRinger", v)

    var count: Int
        get() = i("count", 0)
        set(v) = putI("count", v)
}
