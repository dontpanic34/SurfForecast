package com.surfcast.surfforecast

import android.content.Context
import android.content.SharedPreferences

/** KeyValueStore adossé aux mêmes SharedPreferences "surf_prefs" que l'app Android. */
class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {

    constructor(context: Context) : this(context.getSharedPreferences("surf_prefs", Context.MODE_PRIVATE))

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String?) { prefs.edit().putString(key, value).apply() }
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    override fun contains(key: String): Boolean = prefs.contains(key)
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}
