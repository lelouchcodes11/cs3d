package com.lagradost.cloudstream3.ui

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.newHomePageResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class APIRepositoryMainPageTest {
    private fun api(failing: Set<String>) = object : MainAPI() {
        override var name = "MainPageRows" + failing.joinToString()
        override var mainUrl = "https://example.com"
        override val hasMainPage = true
        override val mainPage = mainPageOf("a" to "A", "b" to "B", "c" to "C")
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            if (request.data in failing) throw IllegalStateException("row ${request.data} moved")
            return newHomePageResponse(request.name, emptyList())
        }
    }

    @Test fun oneFailedRowLeavesTheOthers() = runBlocking {
        val r = APIRepository(api(setOf("b"))).getMainPage(1, null)
        assertTrue(r is Resource.Success)
        assertEquals(listOf("A", "C"), (r as Resource.Success).value.mapNotNull { it?.items?.firstOrNull()?.name })
    }

    @Test fun allRowsFailedIsAnError() = runBlocking {
        assertTrue(APIRepository(api(setOf("a", "b", "c"))).getMainPage(1, null) is Resource.Failure)
    }
}
