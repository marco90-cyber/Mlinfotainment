package com.marco90.mlinfotainment.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.marco90.mlinfotainment.BuildConfig
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** I moduli della schermata principale. */
enum class Module(val key: String, val title: String) {
    NAV("nav", "Navigazione"),
    SPEED("speed", "Velocità e orologio"),
    MUSIC("music", "Musica"),
    WEATHER("weather", "Meteo"),
    APPS("apps", "App preferite")
}

/** Le scelte di MLinfotainment (il tema è in MLTheme). Si salvano da sole e fanno aggiornare la schermata. */
class CarSettings private constructor(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("car", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean): ReadWriteProperty<Any?, Boolean> = object : ReadWriteProperty<Any?, Boolean> {
        private val st = mutableStateOf(sp.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = st.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            st.value = value
            sp.edit().putBoolean(key, value).apply()
        }
    }

    private fun int(key: String, def: Int): ReadWriteProperty<Any?, Int> = object : ReadWriteProperty<Any?, Int> {
        private val st = mutableStateOf(sp.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = st.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            st.value = value
            sp.edit().putInt(key, value).apply()
        }
    }

    private fun str(key: String, def: String): ReadWriteProperty<Any?, String> = object : ReadWriteProperty<Any?, String> {
        private val st = mutableStateOf(sp.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = st.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            st.value = value
            sp.edit().putString(key, value).apply()
        }
    }

    // Avvio automatico
    // nella versione "Sistema" parte spenta, per non aprirsi insieme alla versione normale
    var autoStart by bool("autoStart", !BuildConfig.SYSTEM_BUILD)
    var sideBySide by bool("sideBySide", true)
    var embedNav by bool("embedNav", BuildConfig.SYSTEM_BUILD)
    var trigBt by bool("trigBt", true)
    var trigActivity by bool("trigActivity", true)
    var trigGps by bool("trigGps", true)
    var gpsThreshold by int("gpsThreshold", 20)
    var cooldownMin by int("cooldownMin", 10)
    private var btDevices by str("btDevices", "")

    // Schermata
    var navigator by str("navigator", "maps")
    var unit by str("unit", "kmh")
    var keepOn by bool("keepOn", true)
    var immersive by bool("immersive", true)
    var homeAddress by str("homeAddress", "")
    var workAddress by str("workAddress", "")
    private var moduleOrder by str("moduleOrder", "nav,speed,music,weather,apps")
    private var modulesOff by str("modulesOff", "")
    private var favApps by str("favApps", "")

    var lastAutoLaunch: Long
        get() = sp.getLong("lastAutoLaunch", 0L)
        set(v) { sp.edit().putLong("lastAutoLaunch", v).apply() }

    var btList: List<String>
        get() = btDevices.split(",").filter { it.isNotBlank() }
        set(v) { btDevices = v.joinToString(",") }

    var favList: List<String>
        get() = favApps.split(",").filter { it.isNotBlank() }
        set(v) { favApps = v.joinToString(",") }

    val orderedModules: List<Module>
        get() {
            val fromKeys = moduleOrder.split(",").mapNotNull { k -> Module.entries.firstOrNull { it.key == k } }.distinct()
            return fromKeys + Module.entries.filter { it !in fromKeys }
        }

    fun enabled(m: Module): Boolean = m.key !in modulesOff.split(",")

    fun setEnabled(m: Module, on: Boolean) {
        val off = modulesOff.split(",").filter { it.isNotBlank() && it != m.key }.toMutableList()
        if (!on) off.add(m.key)
        modulesOff = off.joinToString(",")
    }

    val activeModules: List<Module>
        get() = orderedModules.filter { enabled(it) }

    fun move(m: Module, delta: Int) {
        val list = orderedModules.toMutableList()
        val i = list.indexOf(m)
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        list.removeAt(i)
        list.add(j, m)
        moduleOrder = list.joinToString(",") { it.key }
    }

    companion object {
        @Volatile private var instance: CarSettings? = null
        fun get(ctx: Context): CarSettings =
            instance ?: synchronized(this) {
                instance ?: CarSettings(ctx.applicationContext).also { instance = it }
            }
    }
}
