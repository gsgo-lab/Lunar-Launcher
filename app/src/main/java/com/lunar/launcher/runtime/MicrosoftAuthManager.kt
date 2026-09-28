package com.lunar.launcher.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Microsoft -> Xbox Live -> XSTS -> Minecraft Services authentication.
 *
 * Uses the public Minecraft client/device-code OAuth flow, so the user does not
 * need to create an Azure app just to sign in. No Microsoft password is stored.
 */
object MicrosoftAuthManager {
    private const val CLIENT_ID = "00000000402b5328"
    private const val DEVICE_CODE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private const val TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    private const val XBL = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val MC_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private const val MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile"
    private const val MC_ENTITLEMENTS = "https://api.minecraftservices.com/entitlements/mcstore"
    private const val PREFS = "microsoft_account"
    private const val BLOB = "encrypted"
    private const val KEY_ALIAS = "lunar_microsoft_auth_v1"

    data class Account(
        val name: String,
        val uuid: String,
        val accessToken: String,
        val expiresAt: Long,
        val refreshToken: String
    )

    data class DeviceLogin(
        val verificationUri: String,
        val userCode: String,
        val message: String,
        val expiresAt: Long,
        val intervalSeconds: Long
    )

    fun active(context: Context): Account? = load(context)

    fun startDeviceLogin(
        context: Context,
        onCode: (Result<DeviceLogin>) -> Unit,
        onComplete: (Result<Account>) -> Unit
    ) {
        Thread {
            val result = runCatching {
                val json = postForm(DEVICE_CODE, mapOf(
                    "client_id" to CLIENT_ID,
                    "scope" to "XboxLive.signin offline_access"
                ))
                DeviceLogin(
                    verificationUri = json.optString("verification_uri", json.optString("verification_uri_complete")),
                    userCode = json.getString("user_code"),
                    message = json.optString("message", "Откройте страницу Microsoft и введите код."),
                    expiresAt = System.currentTimeMillis() + json.getLong("expires_in") * 1000L,
                    intervalSeconds = json.optLong("interval", 5L)
                ).also { login ->
                    synchronized(pendingCodes) { pendingCodes[login.userCode] = json.getString("device_code") }
                    main { onCode(Result.success(login)) }
                    Thread {
                        val completed = runCatching { finishDeviceLogin(context, login) }
                        main { onComplete(completed) }
                    }.start()
                }
            }
            result.onFailure { main { onCode(Result.failure(it)) } }
        }.start()
    }

    fun refreshIfNeeded(context: Context): Account? {
        val current = load(context) ?: return null
        if (current.expiresAt > System.currentTimeMillis() + 60_000L) return current
        return runCatching {
            val token = postForm(TOKEN, mapOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "refresh_token",
                "refresh_token" to current.refreshToken,
                "scope" to "XboxLive.signin offline_access"
            ))
            val account = minecraftAccount(token.getString("access_token"), token.optString("refresh_token", current.refreshToken), token.optLong("expires_in", 3600L))
            save(context, account)
            account
        }.getOrNull()
    }

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(BLOB).apply()
    }

    private fun finishDeviceLogin(context: Context, login: DeviceLogin): Account {
        var interval = login.intervalSeconds.coerceAtLeast(5L)
        while (System.currentTimeMillis() < login.expiresAt) {
            Thread.sleep(interval * 1000L)
            val result = postFormAllowError(TOKEN, mapOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                "device_code" to deviceCodeForCurrentLogin(login)
            ))
            if (result.first == 200) {
                val body = JSONObject(result.second)
                val account = minecraftAccount(
                    body.getString("access_token"),
                    body.optString("refresh_token"),
                    body.optLong("expires_in", 3600L)
                )
                save(context, account)
                synchronized(pendingCodes) { pendingCodes.remove(login.userCode) }
                return account
            }
            val error = runCatching { JSONObject(result.second).optString("error") }.getOrDefault("")
            when (error) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5L
                "authorization_declined" -> error("Вход Microsoft отклонён.")
                "expired_token" -> error("Код Microsoft истёк. Запустите вход заново.")
                else -> error("Microsoft OAuth: ${result.second.take(500)}")
            }
        }
        synchronized(pendingCodes) { pendingCodes.remove(login.userCode) }
        error("Код Microsoft истёк. Запустите вход заново.")
    }

    private fun main(block: () -> Unit) = Handler(Looper.getMainLooper()).post(block)

    // Device-code metadata is stored only in memory. The token endpoint requires the device_code;
    // we keep it in this process through a short-lived map keyed by the user code.
    private val pendingCodes = mutableMapOf<String, String>()

    private fun deviceCodeForCurrentLogin(login: DeviceLogin): String = synchronized(pendingCodes) {
        pendingCodes[login.userCode] ?: error("Microsoft device login state lost. Start again.")
    }

    private fun minecraftAccount(msAccessToken: String, refreshToken: String, expiresIn: Long): Account {
        val xblBody = JSONObject().apply {
            put("Properties", JSONObject().apply {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", "d=$msAccessToken")
            })
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }
        val xbl = postJson(XBL, xblBody)
        val xblToken = xbl.getString("Token")
        val uhs = xbl.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs")

        val xsts = postJson(XSTS, JSONObject().apply {
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
            put("Properties", JSONObject().apply {
                put("SandboxId", "RETAIL")
                put("UserTokens", org.json.JSONArray().put(xblToken))
            })
        })
        val xstsToken = xsts.getString("Token")
        val mc = postJson(MC_LOGIN, JSONObject().put("identityToken", "XBL3.0 x=$uhs;$xstsToken"))
        val mcToken = mc.getString("access_token")
        val entitlements = getJson(MC_ENTITLEMENTS, mcToken)
        val items = entitlements.optJSONArray("items")
        if (items == null || items.length() == 0) error("У Microsoft-аккаунта не найдена лицензия Minecraft Java Edition.")
        val profile = getJson(MC_PROFILE, mcToken)
        // A missing profile usually means the Microsoft account does not own Java Edition.
        val uuid = profile.getString("id")
        val name = profile.getString("name")
        return Account(name, uuid, mcToken, System.currentTimeMillis() + expiresIn * 1000L, refreshToken)
    }

    private fun save(context: Context, account: Account) {
        val json = JSONObject().apply {
            put("name", account.name)
            put("uuid", account.uuid)
            put("accessToken", account.accessToken)
            put("expiresAt", account.expiresAt)
            put("refreshToken", account.refreshToken)
        }.toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(BLOB, encrypt(json)).apply()
    }

    private fun load(context: Context): Account? = runCatching {
        val blob = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(BLOB, null) ?: return null
        val j = JSONObject(decrypt(blob))
        Account(j.getString("name"), j.getString("uuid"), j.getString("accessToken"), j.getLong("expiresAt"), j.getString("refreshToken"))
    }.getOrNull()

    private fun postForm(url: String, form: Map<String, String>): JSONObject {
        val result = postFormAllowError(url, form)
        if (result.first !in 200..299) error("HTTP ${result.first}: ${result.second.take(500)}")
        return JSONObject(result.second)
    }

    private fun postFormAllowError(url: String, form: Map<String, String>): Pair<Int, String> {
        val body = form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return request(url, "POST", "application/x-www-form-urlencoded", body)
    }

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val result = request(url, "POST", "application/json", body.toString())
        if (result.first !in 200..299) error("HTTP ${result.first}: ${result.second.take(500)}")
        return JSONObject(result.second)
    }

    private fun getJson(url: String, bearer: String): JSONObject {
        val result = request(url, "GET", null, null, mapOf("Authorization" to "Bearer $bearer"))
        if (result.first !in 200..299) error("HTTP ${result.first}: ${result.second.take(500)}")
        return JSONObject(result.second)
    }

    private fun request(url: String, method: String, contentType: String?, body: String?, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                if (contentType != null) setRequestProperty("Content-Type", contentType)
            }
        }
        if (body != null) c.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        return c.responseCode to text
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        gen.init(android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val all = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(all, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val all = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(cipher.doFinal(all.copyOfRange(12, all.size)), StandardCharsets.UTF_8)
    }
}
