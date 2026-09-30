package com.surfcast.surfforecast

import platform.Foundation.NSUserDefaults

/** KeyValueStore iOS, équivalent des SharedPreferences "surf_prefs" d'Android. */
class UserDefaultsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : KeyValueStore {

    override fun getString(key: String, default: String?): String? =
        if (contains(key)) defaults.stringForKey(key) else default

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }

    override fun getInt(key: String, default: Int): Int =
        if (contains(key)) defaults.integerForKey(key).toInt() else default

    override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), forKey = key)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (contains(key)) defaults.boolForKey(key) else default

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = key)

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun remove(key: String) = defaults.removeObjectForKey(key)
}
