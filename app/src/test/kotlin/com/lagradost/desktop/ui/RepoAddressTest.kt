package com.lagradost.desktop.ui

import com.lagradost.desktop.ui.screens.extensions.shortRepoAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class RepoAddressTest {
    @Test
    fun repositoriesShowWhoseTheyAre() {
        assertEquals("GitHub · phisher98/cloudstream-extensions-phisher", shortRepoAddress("https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json"))
        assertEquals("GitHub · aslafy-z/cloudstream-plugins", shortRepoAddress("https://github.com/aslafy-z/cloudstream-plugins/releases/download/catalog-stable/repo.json"))
        assertEquals("GitLab · tearrs/cloudstream-vietnamese", shortRepoAddress("https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/repo.json"))
        assertEquals("cs.eclipsia.dpdns.org", shortRepoAddress("https://cs.eclipsia.dpdns.org/repo.json"))
        assertEquals("git.disroot.org", shortRepoAddress("https://git.disroot.org/ayza/FStream/raw/branch/main/repo.json"))
    }
}
