package com.lagradost.cloudstream3.syncproviders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.syncproviders.providers.Addic7ed
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.KitsuApi
import com.lagradost.cloudstream3.syncproviders.providers.LocalList
import com.lagradost.cloudstream3.syncproviders.providers.MALApi
import com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi
import com.lagradost.cloudstream3.syncproviders.providers.SubDlApi
import com.lagradost.cloudstream3.syncproviders.providers.SubSourceApi
import com.lagradost.cloudstream3.syncproviders.providers.Kitsunekko
import com.lagradost.cloudstream3.syncproviders.providers.SubtitleCat
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.videoskip.AnimeSkipAuth
import java.util.concurrent.TimeUnit

abstract class AccountManager {
    companion object {
        const val NONE_ID: Int = -1
        val malApi = MALApi()
        val kitsuApi = KitsuApi()
        val aniListApi = AniListApi()
        val simklApi = SimklApi()
        val localListApi = LocalList()

        val openSubtitlesApi = OpenSubtitlesApi()
        val addic7ed = Addic7ed()
        val subDlApi = SubDlApi()
        val subSourceApi = SubSourceApi()
        val subtitleCat = SubtitleCat()
        val kitsunekko = Kitsunekko()
        val animeSkipApi = AnimeSkipAuth()

        /** Counts every change of the signed-in accounts or of the selected one: what shows them (Settings) reads it and redraws */
        var changes by androidx.compose.runtime.mutableIntStateOf(0)
            private set

        // strong reference: the store keeps its listeners weakly (declared before the init block, whose code sets it)
        private var storeListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

        var cachedAccounts: MutableMap<String, Array<AuthData>>
        var cachedAccountIds: MutableMap<String, Int>

        const val ACCOUNT_TOKEN = "auth_tokens"
        const val ACCOUNT_IDS = "auth_ids"

        fun accounts(prefix: String): Array<AuthData> {
            require(prefix != "NONE")
            return getKey<Array<AuthData>>(
                ACCOUNT_TOKEN,
                "${prefix}/${DataStoreHelper.currentAccount}"
            ) ?: arrayOf()
        }

        fun updateAccounts(prefix: String, array: Array<AuthData>) {
            require(prefix != "NONE")
            setKey(ACCOUNT_TOKEN, "${prefix}/${DataStoreHelper.currentAccount}", array)
            synchronized(cachedAccounts) {
                cachedAccounts[prefix] = array
            }
            changes++
        }

        fun updateAccountsId(prefix: String, id: Int) {
            require(prefix != "NONE")
            setKey(ACCOUNT_IDS, "${prefix}/${DataStoreHelper.currentAccount}", id)
            synchronized(cachedAccountIds) {
                cachedAccountIds[prefix] = id
            }
            changes++
        }

        val allApis = arrayOf(
            SyncRepo(malApi),
            SyncRepo(kitsuApi),
            SyncRepo(aniListApi),
            SyncRepo(simklApi),
            SyncRepo(localListApi),
            SubtitleRepo(openSubtitlesApi),
            SubtitleRepo(addic7ed),
            SubtitleRepo(subDlApi),
            PlainAuthRepo(animeSkipApi),
            SubtitleRepo(subSourceApi),
            SubtitleRepo(subtitleCat),
            SubtitleRepo(kitsunekko)
        )

        fun updateAccountIds() {
            val ids = mutableMapOf<String, Int>()
            for (api in allApis) {
                ids.put(
                    api.idPrefix,
                    getKey<Int>(
                        ACCOUNT_IDS,
                        "${api.idPrefix}/${DataStoreHelper.currentAccount}",
                        NONE_ID
                    ) ?: NONE_ID
                )
            }
            synchronized(cachedAccountIds) {
                cachedAccountIds = ids
            }
        }

        init {
            val data = mutableMapOf<String, Array<AuthData>>()
            val ids = mutableMapOf<String, Int>()
            for (api in allApis) {
                data.put(api.idPrefix, accounts(api.idPrefix))
                ids.put(
                    api.idPrefix,
                    getKey<Int>(
                        ACCOUNT_IDS,
                        "${api.idPrefix}/${DataStoreHelper.currentAccount}",
                        NONE_ID
                    ) ?: NONE_ID
                )
            }
            cachedAccounts = data
            cachedAccountIds = ids
            watchStoreForOtherWindows()
        }


        /**
         * Another window of the app (same data folder) signed in or out: the account lists here are read from the store again, so that the
         * accounts of every window are the same ones. A change made by this window comes through here too and finds the same lists.
         */
        private fun watchStoreForOtherWindows() {
            runCatching {
                val ctx = com.lagradost.cloudstream3.CloudStreamApp.context ?: return
                val prefs = with(com.lagradost.cloudstream3.utils.DataStore) { ctx.getSharedPrefs() }
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == null || key.startsWith("$ACCOUNT_TOKEN/") || key.startsWith("$ACCOUNT_IDS/")) reloadFromStore()
                }
                storeListener = listener
                prefs.registerOnSharedPreferenceChangeListener(listener)
            }
        }

        private fun reloadFromStore() {
            runCatching {
                val data = mutableMapOf<String, Array<AuthData>>()
                for (api in allApis) data[api.idPrefix] = accounts(api.idPrefix)
                synchronized(cachedAccounts) { cachedAccounts = data }
                updateAccountIds()
                changes++
            }
        }

        // I do not want to place this in the init block as JVM initialization order is weird, and it may cause exceptions
        // accessing other classes
        fun initMainAPI() {
            LoadResponse.malIdPrefix = malApi.idPrefix
            LoadResponse.kitsuIdPrefix = kitsuApi.idPrefix
            LoadResponse.aniListIdPrefix = aniListApi.idPrefix
            LoadResponse.simklIdPrefix = simklApi.idPrefix
        }

        val subtitleProviders = arrayOf(
            SubtitleRepo(openSubtitlesApi),
            SubtitleRepo(addic7ed),
            SubtitleRepo(subDlApi),
            SubtitleRepo(subSourceApi),
            SubtitleRepo(subtitleCat),
            SubtitleRepo(kitsunekko)
        )
        val syncApis = arrayOf(
            SyncRepo(malApi),
            SyncRepo(kitsuApi),
            SyncRepo(aniListApi),
            SyncRepo(simklApi),
            SyncRepo(localListApi)
        )

        const val APP_STRING = "cloudstreamapp"
        const val APP_STRING_REPO = "cloudstreamrepo"
        const val APP_STRING_PLAYER = "cloudstreamplayer"

        // Instantly start the search given a query
        const val APP_STRING_SEARCH = "cloudstreamsearch"

        // Instantly resume watching a show
        const val APP_STRING_RESUME_WATCHING = "cloudstreamcontinuewatching"

        const val APP_STRING_SHARE = "csshare"

        fun secondsToReadable(seconds: Int, completedValue: String): String {
            var secondsLong = seconds.toLong()
            val days = TimeUnit.SECONDS
                .toDays(secondsLong)
            secondsLong -= TimeUnit.DAYS.toSeconds(days)

            val hours = TimeUnit.SECONDS
                .toHours(secondsLong)
            secondsLong -= TimeUnit.HOURS.toSeconds(hours)

            val minutes = TimeUnit.SECONDS
                .toMinutes(secondsLong)
            secondsLong -= TimeUnit.MINUTES.toSeconds(minutes)
            if (minutes < 0) {
                return completedValue
            }
            //println("$days $hours $minutes")
            return "${if (days != 0L) "$days" + "d " else ""}${if (hours != 0L) "$hours" + "h " else ""}${minutes}m"
        }
    }
}