package com.andyechc.batmonitor.update

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The JSON contract is fixed by the landing deployment, so it is pinned here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateCheckTest {
    private lateinit var previousLocale: Locale

    private val manifest = """
        {
          "versionName": "1.1",
          "versionCode": 2,
          "notes": { "es": "Corrige la reconexión.", "en": "Fixes reconnection." },
          "uptodownUrl": "https://batmonitor.uptodown.com/android",
          "apkUrl": "https://github.com/andyechc/batmonitor/releases/latest/download/batmonitor.apk"
        }
    """.trimIndent()

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun parsesThePublishedContractWithSpanishNotes() {
        Locale.setDefault(Locale.forLanguageTag("es-ES"))

        val update = UpdateCheck.parse(manifest)!!

        assertEquals("1.1", update.versionName)
        assertEquals(2L, update.versionCode)
        assertEquals("Corrige la reconexión.", update.notes)
        assertEquals("https://batmonitor.uptodown.com/android", update.downloadUrl)
        assertEquals(
            "https://github.com/andyechc/batmonitor/releases/latest/download/batmonitor.apk",
            update.apkUrl,
        )
    }

    @Test
    fun picksEnglishNotesForAnEnglishLocale() {
        Locale.setDefault(Locale.US)
        assertEquals("Fixes reconnection.", UpdateCheck.parse(manifest)!!.notes)
    }

    @Test
    fun fallsBackToSpanishWhenTheEnglishNoteIsMissing() {
        Locale.setDefault(Locale.US)
        val onlySpanish = """{"versionName":"1.2","versionCode":3,"notes":{"es":"Solo español"}}"""
        assertEquals("Solo español", UpdateCheck.parse(onlySpanish)!!.notes)
    }

    @Test
    fun fallsBackToUptodownWhenTheManifestOmitsTheUrl() {
        val update = UpdateCheck.parse("""{"versionName":"1.2","versionCode":3,"notes":{"es":"x"}}""")!!
        assertEquals(UpdateCheck.DEFAULT_DOWNLOAD_URL, update.downloadUrl)
        assertNull(update.apkUrl)
    }

    @Test
    fun brokenJsonIsIgnored() {
        assertNull(UpdateCheck.parse("not json"))
        assertNull(UpdateCheck.parse(""))
    }
}
