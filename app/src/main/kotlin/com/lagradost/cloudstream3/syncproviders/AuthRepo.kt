package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.CloudStreamApp.Companion.openBrowser
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.NONE_ID
import com.lagradost.cloudstream3.utils.txt

/** General-purpose repo */
class PlainAuthRepo(api: AuthAPI) : AuthRepo(api)

/** Safe abstraction for AuthAPI that provides both a catching interface, and automatic token management. */
abstract class AuthRepo(open val api: AuthAPI) {
    fun isValidRedirectUrl(url: String) = safe { api.isValidRedirectUrl(url) } ?: false
    val idPrefix get() = api.idPrefix
    val name get() = api.name
    val icon get() = api.icon
    val requiresLogin get() = api.requiresLogin
    val createAccountUrl get() = api.createAccountUrl
    val hasOAuth2 get() = api.hasOAuth2
    val hasPin get() = api.hasPin
    val hasInApp get() = api.hasInApp
    val inAppLoginRequirement get() = api.inAppLoginRequirement
    val isAvailable get() = !api.requiresLogin || authUser() != null

    companion object {
        private val oauthPayload: MutableMap<String, String?> = mutableMapOf()

        fun setOAuthPayload(idPrefix: String, payload: String?) {
            synchronized(oauthPayload) {
                oauthPayload[idPrefix] = payload
            }
            if (payload != null) {
                com.lagradost.cloudstream3.CloudStreamApp.setKey("oauth_payload", idPrefix, payload)
            } else {
                com.lagradost.cloudstream3.CloudStreamApp.removeKey("oauth_payload", idPrefix)
            }
        }

        fun getOAuthPayload(idPrefix: String): String? {
            return synchronized(oauthPayload) {
                oauthPayload[idPrefix]
                    ?: com.lagradost.cloudstream3.CloudStreamApp.getKey<String>("oauth_payload", idPrefix)
            }
        }
    }

    @Throws
    protected suspend fun freshAuth(): AuthData? {
        val data = authData() ?: return null
        if (data.token.isAccessTokenExpired()) {
            val newToken = api.refreshToken(data.token) ?: return null
            val newAuth = AuthData(user = data.user, token = newToken)
            refreshUser(newAuth)
            return newAuth
        }
        return data
    }

    @Throws
    fun openOAuth2Page(): Boolean {
        // desktop: without the user's own client ID the page says client_id=null / "Client authentication failed":
        // ask for the keys first (Sign-in keys dialog) instead of opening a page that cannot work
        if (ApiKeys.missingFor(idPrefix, browserFlow = true).isNotEmpty()) {
            com.lagradost.desktop.ui.showApiKeysDialog(name)
            return true
        }
        val page = api.loginRequest() ?: return false
        setOAuthPayload(idPrefix, page.payload)
        // the service sends the browser back to http://localhost:52526/<service> (a page of this app), which hands the answer to the
        // same login code as the old cloudstreamapp:// link did; that link still works for clients registered with it
        com.lagradost.desktop.net.OAuthCallback.arm { link ->
            com.lagradost.desktop.ui.DesktopUiHost.window?.let { it.isVisible = true; it.toFront() }
            com.lagradost.desktop.NativeLinks.open(link)
        }
        openBrowser(page.url)
        return true
    }

    fun openOAuth2PageWithToast() {
        try {
            if (!openOAuth2Page()) {
                showToast(txt(R.string.authenticated_user_fail, api.name))
            }
        } catch (t: Throwable) {
            logError(t)
            if (t is ErrorLoadingException && t.message != null) {
                showToast(t.message)
                return
            }
            showToast(txt(R.string.authenticated_user_fail, api.name))
        }
    }

    suspend fun logout(from: AuthUser) {
        val currentAccounts = AccountManager.accounts(idPrefix)
        val (newAccounts, oldAccounts) = currentAccounts.partition { it.user.id != from.id }
        if (newAccounts.size < currentAccounts.size) {
            AccountManager.updateAccounts(idPrefix, newAccounts.toTypedArray())
            AccountManager.updateAccountsId(idPrefix, 0)
        }

        for (oldAccount in oldAccounts) {
            try {
                api.invalidateToken(oldAccount.token)
            } catch (_: NotImplementedError) {
                // no-op
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    fun refreshUser(newAuth: AuthData) {
        val currentAccounts = AccountManager.accounts(idPrefix)
        val newAccounts = currentAccounts.map {
            if (it.user.id == newAuth.user.id) {
                newAuth
            } else {
                it
            }
        }.toTypedArray()
        AccountManager.updateAccounts(idPrefix, newAccounts)
    }

    fun authData(): AuthData? = synchronized(AccountManager.cachedAccountIds) {
        AccountManager.cachedAccountIds[idPrefix]?.let { id ->
            AccountManager.cachedAccounts[idPrefix]?.firstOrNull { data -> data.user.id == id }
        }
    }

    fun authToken(): AuthToken? = authData()?.token

    fun authUser(): AuthUser? = authData()?.user

    val accounts
        get() = synchronized(AccountManager.cachedAccounts) {
            AccountManager.cachedAccounts[idPrefix] ?: emptyArray()
        }
    var accountId
        get() = synchronized(AccountManager.cachedAccountIds) {
            AccountManager.cachedAccountIds[idPrefix] ?: NONE_ID
        }
        set(value) {
            AccountManager.updateAccountsId(idPrefix, value)
        }

    @Throws
    suspend fun pinRequest() =
        api.pinRequest()

    @Throws
    private suspend fun setupLogin(token: AuthToken): Boolean {
        val user = api.user(token) ?: return false

        val newAccount = AuthData(
            token = token,
            user = user,
        )

        val currentAccounts = AccountManager.accounts(idPrefix)
        val newAccounts = if (currentAccounts.any { it.user.id == newAccount.user.id }) {
            currentAccounts.map { if (it.user.id == newAccount.user.id) newAccount else it }.toTypedArray()
        } else {
            currentAccounts + newAccount
        }

        AccountManager.updateAccounts(idPrefix, newAccounts)
        AccountManager.updateAccountsId(idPrefix, user.id)
        if (this is SyncRepo) {
            requireLibraryRefresh = true
        }
        return true
    }

    @Throws
    suspend fun login(form: AuthLoginResponse): Boolean {
        return setupLogin(api.login(form) ?: return false)
    }

    @Throws
    suspend fun login(payload: AuthPinData): Boolean {
        return setupLogin(api.login(payload) ?: return false)
    }

    @Throws
    suspend fun login(redirectUrl: String): Boolean {
        val payload = getOAuthPayload(api.idPrefix)
        return setupLogin(
            api.login(redirectUrl, payload) ?: return false
        )
    }
}