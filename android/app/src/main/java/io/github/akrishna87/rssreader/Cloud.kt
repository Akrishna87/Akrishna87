package io.github.akrishna87.rssreader

import android.content.SharedPreferences
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The sync service refused or failed; the message is safe to show. */
class SyncException(message: String) : IOException(message)

class Session(val email: String, val idToken: String, val refreshToken: String, val expiresAt: Long)

/**
 * Talks to the same Firebase project as the web reader over its REST APIs (no Firebase SDK):
 * Google sign-in → Firebase ID token → one private Firestore document per account, rssReaderAndroid/{email}.
 */
class Cloud(private val prefs: SharedPreferences) {
    private companion object {
        const val API_KEY = "AIzaSyCk68zOHD-pn75g63XnJ5NrK6iRSs4e3iw"
        const val PROJECT = "school-notebook-tracker"
        const val COLLECTION = "rssReaderAndroid"
    }

    fun session(): Session? {
        val email = prefs.getString("email", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        return Session(email, prefs.getString("id", "").orEmpty(), refresh, prefs.getLong("exp", 0))
    }

    fun signOut() = prefs.edit().clear().apply()

    /** Exchange a Google ID token (from the sign-in sheet) for a Firebase session. */
    fun signIn(googleIdToken: String): Session {
        val body = JSONObject()
            .put("postBody", "id_token=" + URLEncoder.encode(googleIdToken, "UTF-8") + "&providerId=google.com")
            .put("requestUri", "http://localhost")
            .put("returnSecureToken", true)
        val r = call("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$API_KEY", "POST", body.toString(), null)
        val o = JSONObject(r.body)
        val email = o.optString("email").lowercase()
        if (email.isEmpty()) throw SyncException("Google didn't share an email address")
        return save(email, o.getString("idToken"), o.getString("refreshToken"), o.optString("expiresIn", "3600").toLong())
    }

    private fun save(email: String, id: String, refresh: String, expiresIn: Long): Session {
        val exp = System.currentTimeMillis() + (expiresIn - 120) * 1000
        prefs.edit().putString("email", email).putString("id", id).putString("refresh", refresh).putLong("exp", exp).apply()
        return Session(email, id, refresh, exp)
    }

    private fun fresh(): Session {
        val s = session() ?: throw SyncException("Not signed in")
        if (s.idToken.isNotEmpty() && System.currentTimeMillis() < s.expiresAt) return s
        val r = call(
            "https://securetoken.googleapis.com/v1/token?key=$API_KEY", "POST",
            "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(s.refreshToken, "UTF-8"), null, form = true,
        )
        val o = JSONObject(r.body)
        return save(s.email, o.getString("id_token"), o.optString("refresh_token", s.refreshToken), o.optString("expires_in", "3600").toLong())
    }

    class Remote(val json: String?, val updateTime: String?)

    private fun docUrl(email: String) =
        "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents/$COLLECTION/" + URLEncoder.encode(email, "UTF-8")

    fun pull(): Remote {
        val s = fresh()
        val r = call(docUrl(s.email), "GET", null, s.idToken, allow404 = true)
        if (r.code == 404) return Remote(null, null)
        val o = JSONObject(r.body)
        val json = o.optJSONObject("fields")?.optJSONObject("json")?.optString("stringValue")
        return Remote(json, o.optString("updateTime").ifEmpty { null })
    }

    /** Write [json] if nobody else wrote since [updateTime]. Returns false when someone did (pull and merge again). */
    fun push(json: String, updateTime: String?): Boolean {
        val s = fresh()
        val precondition = if (updateTime == null) "currentDocument.exists=false" else "currentDocument.updateTime=" + URLEncoder.encode(updateTime, "UTF-8")
        val url = docUrl(s.email) + "?updateMask.fieldPaths=json&updateMask.fieldPaths=updatedAt&$precondition"
        val body = JSONObject().put(
            "fields",
            JSONObject()
                .put("json", JSONObject().put("stringValue", json))
                .put("updatedAt", JSONObject().put("integerValue", System.currentTimeMillis().toString())),
        )
        val r = call(url, "PATCH", body.toString(), s.idToken, allowConflict = true)
        return r.code != 409 && r.code != 400
    }

    private class Reply(val code: Int, val body: String)

    private fun call(
        url: String, method: String, body: String?, bearer: String?,
        form: Boolean = false, allow404: Boolean = false, allowConflict: Boolean = false,
    ): Reply {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code < 400 || (code == 404 && allow404) || (allowConflict && (code == 409 || code == 400))) return Reply(code, text)
            throw SyncException(explain(code, text))
        } finally {
            c.disconnect()
        }
    }

    private fun explain(code: Int, body: String): String {
        val msg = try { JSONObject(body).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
        return when {
            code == 403 && msg.contains("permission", true) || msg.contains("PERMISSION_DENIED") ->
                "Sync isn't switched on in Firebase yet. Add the Firestore rule for rssReaderAndroid."
            code == 401 || msg.contains("INVALID_IDP_RESPONSE") || msg.contains("TOKEN_EXPIRED") || msg.contains("INVALID_REFRESH_TOKEN") ->
                "Sign in again to keep syncing."
            msg.isNotEmpty() -> msg
            else -> "Sync failed (HTTP $code)"
        }
    }
}
