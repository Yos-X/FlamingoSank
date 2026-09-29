package yos.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import yos.music.player.code.utils.lrc.YosTtmlFactory

class YosTtmlFactoryTest {
    private val sample = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word">
        <head><metadata><ttm:agent type="person" xml:id="v1"/>
        <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
        <translations><translation type="subtitle" xml:lang="en-US"><text for="L1">Hello world</text></translation></translations>
        <transliterations><transliteration xml:lang="zh-Latn-pinyin"><text for="L1"><span begin="1.000" end="1.500" xmlns="http://www.w3.org/ns/ttml">ni hao</span> <span begin="1.500" end="2.000" xmlns="http://www.w3.org/ns/ttml">shi jie</span></text></transliteration></transliterations>
        </iTunesMetadata></metadata></head>
        <body><div>
        <p begin="1.000" end="2.000" itunes:key="L1" ttm:agent="v1"><span begin="1.000" end="1.500">你好</span> <span begin="1.500" end="2.000">世界</span></p>
        <p begin="0:03.000" end="0:04.000" itunes:key="L2" ttm:agent="v2"><span begin="0:03.000" end="0:04.000">再见</span></p>
        </div></body></tt>
    """.trimIndent()

    @Test
    fun parsesWordTimingsWithSubtitle() {
        val result = YosTtmlFactory().formatTtmlEntries(sample)
        assertNotNull(result)
        val first = result!!.entries[0]
        assertEquals(1000f to "", first[0])
        assertEquals(1500f to "你好", first[1])
        assertEquals(2000f to " 世界", first[2])
        assertEquals(1000f to "ni hao shi jie\nHello world", first.last())
    }

    @Test
    fun lineWithoutSubtitleEndsWithEmptyPair() {
        val second = YosTtmlFactory().formatTtmlEntries(sample)!!.entries[1]
        assertEquals(3000f to "", second.first())
        assertEquals(3000f to "", second.last())
    }

    @Test
    fun marksSecondAgentAsOtherSide() {
        val result = YosTtmlFactory().formatTtmlEntries(sample)!!
        assertEquals(listOf(false, true), result.otherSideForLines)
    }

    @Test
    fun rejectsPlainLrc() {
        assertTrue(!YosTtmlFactory.isTtml("[00:01.00]hello"))
        assertNull(YosTtmlFactory().formatTtmlEntries("[00:01.00]hello"))
    }
}
