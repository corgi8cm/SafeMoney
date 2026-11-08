package io.github.corgi8cm.safemoney.sync

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** 需要使用者在畫面上同意（第一次，或授權被收回）。 */
class NeedsConsentException : Exception("需要重新授權 Google 雲端硬碟")

/** Google 登入 / 授權失敗，[message] 可直接顯示給使用者。 */
class AuthFailedException(message: String) : Exception(message)

/**
 * 用 Google 帳號授權存取「雲端硬碟裡這個 App 專用的隱藏資料夾」（drive.appdata）。
 * 這個權限只能看到 App 自己建立的檔案，看不到使用者其他的雲端硬碟內容。
 */
class GoogleDriveAuth(private val context: Context) {
    sealed interface Result {
        data class Token(val value: String) : Result
        data class NeedsConsent(val intentSender: IntentSender) : Result
    }

    private val client get() = Identity.getAuthorizationClient(context)

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    suspend fun authorize(): Result {
        val result = try {
            client.authorize(request()).await()
        } catch (e: ApiException) {
            throw AuthFailedException(describe(e))
        }
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) {
            Result.NeedsConsent(pending.intentSender)
        } else {
            Result.Token(result.accessToken ?: throw AuthFailedException("Google 沒有回傳授權，請再試一次"))
        }
    }

    /** 背景同步用：已經同意過就直接拿到 token，否則丟 [NeedsConsentException]。 */
    suspend fun token(): String = when (val r = authorize()) {
        is Result.Token -> r.value
        is Result.NeedsConsent -> throw NeedsConsentException()
    }

    /** 使用者在同意畫面按完之後，從結果取出 token。 */
    fun tokenFromConsent(data: Intent?): String {
        val result = try {
            client.getAuthorizationResultFromIntent(data)
        } catch (e: ApiException) {
            throw AuthFailedException(describe(e))
        }
        return result.accessToken ?: throw AuthFailedException("沒有完成授權")
    }

    /** token 被拒（過期或收回）時清掉快取，下次會重新取得。 */
    suspend fun invalidate(token: String) {
        withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(context, token) } }
    }

    private fun describe(e: ApiException): String = when (e.statusCode) {
        CommonStatusCodes.DEVELOPER_ERROR -> "Google 登入還沒設定完成（錯誤碼 10）：請確認 Google Cloud 的 Android 用戶端套件名稱與 SHA-1"
        CommonStatusCodes.NETWORK_ERROR -> "網路連線失敗，請稍後再試"
        CommonStatusCodes.CANCELED -> "已取消授權"
        else -> "Google 授權失敗（錯誤碼 ${e.statusCode}）"
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
