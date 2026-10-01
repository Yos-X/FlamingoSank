package yos.music.player.code.utils.lrc

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

// Adapted from the TTML parser in shouryadixitisverycool/Flamingo.
class YosTtmlFactory {
    class Result(
        val entries: List<List<Pair<Float, String>>>,
        val otherSideForLines: List<Boolean>,
        val transliterationForLines: List<String>,
        // One entry per lyric word, empty when the line has no word-level transliteration
        val transliterationWordsForLines: List<List<String>>
    )

    private class Word(val endTime: Float, val text: String)

    private class Span(val begin: Float, val text: String)

    fun formatTtmlEntries(ttml: String): Result? {
        if (!isTtml(ttml)) return null
        return runCatching { parse(ttml) }.getOrNull()
    }

    private fun parse(ttml: String): Result? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val root = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml))).documentElement
        val body = root.descendants("body").firstOrNull() ?: return null
        val translations = root.textByKey("translations")
        val transliterations = root.textByKey("transliterations")
        val transliterationSpans = root.spansByKey("transliterations")

        val entries = mutableListOf<List<Pair<Float, String>>>()
        val otherSide = mutableListOf<Boolean>()
        val transliterationLines = mutableListOf<String>()
        val transliterationWords = mutableListOf<List<String>>()

        body.descendants("p").forEach { line ->
            val begin = line.time("begin") ?: return@forEach
            val words = line.words(begin)
            val pairs = mutableListOf(begin to "")
            if (words.isNotEmpty()) {
                words.forEach { pairs.add(it.endTime to it.text) }
            } else {
                val text = line.textContent.collapse()
                if (text.isEmpty()) return@forEach
                pairs.add(begin to text)
            }
            pairs.add(begin to "")

            // Same shape YosLrcFactory produces for a line with a translation
            val key = line.attr("key")
            val translation = key?.let { translations[it] }.orEmpty()
            if (translation.isNotEmpty()) {
                pairs.add(begin to "")
                pairs.add(begin to translation)
            }

            entries.add(pairs)
            otherSide.add(line.attr("agent").equals("v2", ignoreCase = true))
            transliterationLines.add(key?.let { transliterations[it] }.orEmpty())
            transliterationWords.add(
                if (words.isEmpty()) emptyList()
                else alignTransliteration(words, key?.let { transliterationSpans[it] }.orEmpty())
            )
        }

        return if (entries.isEmpty()) null
        else Result(entries, otherSide, transliterationLines, transliterationWords)
    }

    // By index when the counts match, otherwise by the word each span starts in
    private fun alignTransliteration(words: List<Word>, spans: List<Span>): List<String> {
        if (spans.isEmpty()) return emptyList()
        if (spans.size == words.size) return spans.map { it.text }
        val aligned = List(words.size) { mutableListOf<String>() }
        spans.forEach { span ->
            val index = words.indexOfFirst { span.begin < it.endTime }.takeIf { it >= 0 } ?: words.lastIndex
            aligned[index].add(span.text)
        }
        return aligned.map { it.joinToString(" ") }
    }

    private fun Element.words(lineBegin: Float): List<Word> {
        val words = mutableListOf<Word>()
        val pending = StringBuilder()
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> pending.append(node.nodeValue)
                Node.ELEMENT_NODE -> {
                    val child = node as Element
                    if (child.tag() == "span") {
                        val end = child.time("end") ?: child.time("begin") ?: lineBegin
                        val text = (pending.toString() + child.textContent).replace(WHITESPACE, " ")
                            .let { if (words.isEmpty()) it.trimStart() else it }
                        pending.clear()
                        if (text.isNotEmpty()) words.add(Word(end, text))
                    } else {
                        pending.append(child.textContent)
                    }
                }
            }
        }
        if (pending.isNotBlank() && words.isNotEmpty()) {
            val last = words.removeAt(words.lastIndex)
            words.add(Word(last.endTime, last.text + pending.toString().replace(WHITESPACE, " ")))
        }
        return words
    }

    // First text per line key inside <translations> or <transliterations>
    private fun Element.textByKey(container: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        descendants(container).forEach { c ->
            c.descendants("text").forEach { t ->
                val key = t.attr("for") ?: return@forEach
                val text = t.textContent.collapse()
                if (text.isNotEmpty() && key !in result) result[key] = text
            }
        }
        return result
    }

    // Timed spans of the first text per line key inside <transliterations>
    private fun Element.spansByKey(container: String): Map<String, List<Span>> {
        val result = mutableMapOf<String, List<Span>>()
        descendants(container).forEach { c ->
            c.descendants("text").forEach { t ->
                val key = t.attr("for") ?: return@forEach
                if (key in result) return@forEach
                val spans = t.descendants("span").mapNotNull { span ->
                    val text = span.textContent.collapse()
                    val begin = span.time("begin")
                    if (text.isEmpty() || begin == null) null else Span(begin, text)
                }
                if (spans.isNotEmpty()) result[key] = spans
            }
        }
        return result
    }

    private fun Element.descendants(name: String): List<Element> {
        val result = mutableListOf<Element>()
        fun visit(node: Node) {
            for (i in 0 until node.childNodes.length) {
                val child = node.childNodes.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    if ((child as Element).tag() == name) result.add(child)
                    visit(child)
                }
            }
        }
        visit(this)
        return result
    }

    private fun Element.tag(): String = localName ?: tagName.substringAfter(':')

    // Matches prefixed attributes too (itunes:key, ttm:agent)
    private fun Element.attr(name: String): String? {
        for (i in 0 until attributes.length) {
            val a = attributes.item(i)
            if (a.localName == name || a.nodeName.substringAfter(':') == name) {
                return a.nodeValue.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun Element.time(name: String): Float? = attr(name)?.toMillis()

    // "37.500", "2:21.055", "0:02:21.055", "37.5s"
    private fun String.toMillis(): Float? {
        val parts = trim().removeSuffix("s").split(':')
        val nums = parts.map { it.toFloatOrNull() ?: return null }
        val seconds = when (nums.size) {
            1 -> nums[0]
            2 -> nums[0] * 60f + nums[1]
            3 -> nums[0] * 3600f + nums[1] * 60f + nums[2]
            else -> return null
        }
        return seconds * 1000f
    }

    private fun String.collapse() = replace(WHITESPACE, " ").trim()

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val TT_ROOT = Regex("<\\s*tt(\\s|>|$)")

        fun isTtml(text: String): Boolean {
            val t = text.trimStart()
            return t.startsWith("<") && TT_ROOT.containsMatchIn(t)
        }
    }
}
