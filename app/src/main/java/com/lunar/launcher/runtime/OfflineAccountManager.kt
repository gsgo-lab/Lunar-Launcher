package com.lunar.launcher.runtime

import android.content.Context
import java.util.UUID

object OfflineAccountManager {
    data class Account(val name: String, val uuid: String)
    private const val PREFS = "offline_accounts"
    private const val ACTIVE = "active"
    private const val LIST = "list"

    fun list(context: Context): List<Account> = context.getSharedPreferences(PREFS, 0).getStringSet(LIST, emptySet()).orEmpty()
        .mapNotNull { it.split("|", limit = 2).takeIf { p -> p.size == 2 }?.let { Account(it[0], it[1]) } }.sortedBy { it.name.lowercase() }

    fun active(context: Context): Account? {
        val name = context.getSharedPreferences(PREFS, 0).getString(ACTIVE, null) ?: return null
        return list(context).firstOrNull { it.name == name }
    }

    fun add(context: Context, name: String): Account {
        val clean = name.trim().replace("|", "").take(16)
        require(clean.length in 3..16) { "Имя должно быть от 3 до 16 символов" }
        val account = Account(clean, UUID.nameUUIDFromBytes("Lunar:$clean".toByteArray()).toString())
        val p = context.getSharedPreferences(PREFS, 0)
        val values = p.getStringSet(LIST, emptySet()).orEmpty().toMutableSet()
        values.removeAll { it.substringBefore("|").equals(clean, true) }
        values += "${account.name}|${account.uuid}"
        p.edit().putStringSet(LIST, values).putString(ACTIVE, account.name).apply()
        return account
    }

    fun select(context: Context, name: String) { context.getSharedPreferences(PREFS, 0).edit().putString(ACTIVE, name).apply() }
    fun remove(context: Context, name: String) {
        val p = context.getSharedPreferences(PREFS, 0); val values = p.getStringSet(LIST, emptySet()).orEmpty().filterNot { it.substringBefore("|") == name }.toSet()
        p.edit().putStringSet(LIST, values).apply()
        if (p.getString(ACTIVE, null) == name) p.edit().remove(ACTIVE).apply()
    }
}
