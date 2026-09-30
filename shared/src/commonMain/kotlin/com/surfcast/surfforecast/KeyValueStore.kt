package com.surfcast.surfforecast

/**
 * Stockage clé/valeur persistant, équivalent multiplateforme des SharedPreferences
 * "surf_prefs" d'app/ : SharedPreferences sur Android, NSUserDefaults sur iOS.
 * Les clés sont les mêmes que côté Android pour garder les réglages existants.
 */
interface KeyValueStore {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun contains(key: String): Boolean
    fun remove(key: String)
}

/** Implémentation en mémoire (tests, aperçus). */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any?>()

    override fun getString(key: String, default: String?): String? =
        if (key in values) values[key] as String? else default

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun contains(key: String): Boolean = key in values
    override fun remove(key: String) { values.remove(key) }
}

private inline fun <reified T : Enum<T>> safeEnumValueOf(name: String?, default: T): T =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

/** Comme loadEngineConfigFromPrefs d'app/ : une valeur corrompue retombe sur le défaut. */
fun loadEngineConfigFromPrefs(prefs: KeyValueStore): ForecastEngineConfig = ForecastEngineConfig(
    shortTermWeather = safeEnumValueOf(prefs.getString("short_weather", null), WeatherModel.AROME),
    shortTermWave = safeEnumValueOf(prefs.getString("short_wave", null), WaveModel.MFWAM),
    longTermWeather = safeEnumValueOf(prefs.getString("long_weather", null), WeatherModel.ECMWF_IFS),
    longTermWave = safeEnumValueOf(prefs.getString("long_wave", null), WaveModel.MFWAM)
)
