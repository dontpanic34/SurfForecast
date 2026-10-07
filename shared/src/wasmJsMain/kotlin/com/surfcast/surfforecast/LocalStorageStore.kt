package com.surfcast.surfforecast

import kotlinx.browser.localStorage

/** KeyValueStore du navigateur (localStorage) : équivalent des SharedPreferences "surf_prefs". */
class LocalStorageStore(private val prefix: String = "surf_prefs.") : KeyValueStore {
    private fun k(key: String) = prefix + key

    override fun getString(key: String, default: String?): String? = localStorage.getItem(k(key)) ?: default
    override fun putString(key: String, value: String?) {
        if (value == null) localStorage.removeItem(k(key)) else localStorage.setItem(k(key), value)
    }
    override fun getInt(key: String, default: Int): Int = localStorage.getItem(k(key))?.toIntOrNull() ?: default
    override fun putInt(key: String, value: Int) = localStorage.setItem(k(key), value.toString())
    override fun getBoolean(key: String, default: Boolean): Boolean =
        localStorage.getItem(k(key))?.toBooleanStrictOrNull() ?: default
    override fun putBoolean(key: String, value: Boolean) = localStorage.setItem(k(key), value.toString())
    override fun contains(key: String): Boolean = localStorage.getItem(k(key)) != null
    override fun remove(key: String) = localStorage.removeItem(k(key))
}
