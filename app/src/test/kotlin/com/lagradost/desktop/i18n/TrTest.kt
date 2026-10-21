package com.lagradost.desktop.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrTest {
    private val table = """
        # comment
        en	ar	tr
        Watch now	شاهد الآن	Şimdi izle
        Search {0} sources	ابحث في {0,plural,one{مصدر واحد}two{مصدرين}few{# مصادر}many{# مصدراً}other{# مصدر}}	{0} kaynakta ara
        E{0}	الحلقة {0}	B{0}
        Resume {0}	استئناف {0}	{0} devam et
        Library	المكتبة	Kütüphane
        Watching	أشاهده	İzleniyor
        {0} min ago	منذ {0} د	{0} dk önce
        Nothing from your list airs {0}	لا شيء من قائمتك يُعرض {0}	Listenizden {0} yayınlanan yok
        today	اليوم	bugün
        on {0}	يوم {0}	{0} günü
    """.trimIndent()

    private val ar get() = Tr.parseCatalog("ar", table)
    private val tr get() = Tr.parseCatalog("tr", table)

    @Test
    fun findsAnExactLineInTheColumnOfTheLanguage() {
        assertEquals("شاهد الآن", ar.translate("Watch now"))
        assertEquals("Şimdi izle", tr.translate("Watch now"))
        assertEquals("Not in the table", tr.translate("Not in the table"))
    }

    @Test
    fun fillsTheCutOutPiecesIntoTheTranslation() {
        assertEquals("12 kaynakta ara", tr.translate("Search 12 sources"))
        assertEquals("منذ 5 د", ar.translate("5 min ago"))
    }

    @Test
    fun arabicHasSixPluralForms() {
        val a = ar
        assertEquals("ابحث في مصدر واحد", a.translate("Search 1 sources"))
        assertEquals("ابحث في مصدرين", a.translate("Search 2 sources"))
        assertEquals("ابحث في 3 مصادر", a.translate("Search 3 sources"))
        assertEquals("ابحث في 10 مصادر", a.translate("Search 10 sources"))
        assertEquals("ابحث في 11 مصدراً", a.translate("Search 11 sources"))
        assertEquals("ابحث في 99 مصدراً", a.translate("Search 99 sources"))
        assertEquals("ابحث في 100 مصدر", a.translate("Search 100 sources"))
        assertEquals("zero", Tr.category("ar", 0))
        assertEquals("few", Tr.category("ar", 103))
        assertEquals("many", Tr.category("ar", 111))
        assertEquals("one", Tr.category("tr", 1))
        assertEquals("other", Tr.category("tr", 2))
    }

    @Test
    fun aKeyWithHardlyAnyWordsOnlyTakesNumbers() {
        // "E{0}" is for E5; it must not take "Explore" and turn it into a translation of its own
        assertEquals("B5", tr.translate("E5"))
        assertEquals("Explore", tr.translate("Explore"))
    }

    @Test
    fun aLineOfSeveralPartsIsTranslatedPartByPart() {
        assertEquals("Kütüphane · İzleniyor", tr.translate("Library · Watching"))
        assertEquals("الحلقة 3 · المكتبة", ar.translate("E3 · Library"))
        assertEquals("Kütüphane: İzleniyor", tr.translate("Library: Watching"))
    }

    @Test
    fun theCutOutPiecesAreTranslatedOnTheirOwn() {
        // "today" and "on Monday" are texts of their own inside a longer line
        assertEquals("Listenizden bugün yayınlanan yok", tr.translate("Nothing from your list airs today"))
        assertEquals("لا شيء من قائمتك يُعرض يوم Monday", ar.translate("Nothing from your list airs on Monday"))
        assertEquals("استئناف الحلقة 4", ar.translate("Resume E4"))
    }

    @Test
    fun theRealCatalogIsConsistent() {
        for (language in listOf("ar", "tr")) {
            val catalog = assertNotNull(Tr.Catalog.load(language), "no catalog for $language")
            assertTrue(catalog.exact.size > 800, "$language: only ${catalog.exact.size} exact lines")
            assertTrue(catalog.templates.size > 60, "$language: only ${catalog.templates.size} templates")
            assertEquals("المكتبة".takeIf { language == "ar" } ?: "Kütüphane", catalog.translate("Library"))
        }
    }

    @Test
    fun everyRowOfTheTableHasTheSamePiecesInEveryLanguage() {
        val text = Tr::class.java.classLoader.getResourceAsStream("i18n/strings.tsv")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val header = text.lineSequence().first { it.isNotBlank() && !it.startsWith("#") }.split('\t')
        assertEquals(listOf("en", "ar", "tr"), header.take(3))
        val index = Regex("""\{(\d+)[,}]""")
        val seen = HashSet<String>()
        var rows = 0
        for (line in text.lineSequence().dropWhile { it.startsWith("#") || it.isBlank() }.drop(1)) {
            if (line.isBlank() || line.startsWith("#")) continue
            val cells = line.split('\t')
            assertEquals(header.size, cells.size, "wrong number of columns: $line")
            val key = cells[0]
            assertTrue(seen.add(key), "the same English text twice: $key")
            val want = index.findAll(key).map { it.groupValues[1] }.toSet()
            for (col in 1 until cells.size) {
                val value = cells[col]
                if (value.isEmpty()) continue
                val got = index.findAll(value).map { it.groupValues[1] }.toSet()
                assertTrue(want.containsAll(got), "${header[col]}: uses a piece the English text does not have: $key")
                assertEquals(value.count { it == '{' }, value.count { it == '}' }, "${header[col]}: unbalanced braces: $key")
                assertTrue(value.trim().isNotEmpty(), "${header[col]}: empty translation: $key")
            }
            rows++
        }
        assertTrue(rows > 1000, "only $rows rows")
    }
}
