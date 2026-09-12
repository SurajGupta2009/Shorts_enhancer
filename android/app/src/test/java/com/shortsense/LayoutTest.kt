package com.shortsense

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the two mistakes in a layout that only show up on a real phone.
 *
 * A view without layout_width/layout_height is a hard crash while inflating
 * ("Binary XML file line #N: You must supply a layout_width attribute") and an id that
 * is looked up but never defined is a NullPointerException on the first tap. Both shipped
 * once; both are visible from the XML alone, so they are tested here without Robolectric.
 */
class LayoutTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun layouts(): List<File> {
        val dir = RepoFiles.findDir("android/app/src/main/res/layout")
            ?: error("could not find android/app/src/main/res/layout")
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }!!.sortedBy { it.name }
    }

    private fun elements(el: Element): List<Element> =
        (0 until el.childNodes.length).mapNotNull { el.childNodes.item(it) as? Element }

    /**
     * Reads an android: attribute whichever way the parser filed it. A DOM built without
     * namespace awareness keeps attributes under their qualified name, and one built with
     * it keeps them under namespace + local name, so both are tried.
     */
    private fun androidAttr(el: Element, name: String): String {
        val qualified = el.getAttribute("android:$name")
        if (qualified.isNotEmpty()) return qualified
        return el.getAttributeNS(androidNs, name)
    }

    @Test
    fun `every view supplies the layout size the inflater requires`() {
        val missing = ArrayList<String>()
        for (file in layouts()) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            fun walk(el: Element) {
                val where = "${file.name}: <${el.tagName}>"
                if (androidAttr(el, "layout_width").isEmpty()) {
                    missing += "$where has no android:layout_width"
                }
                if (androidAttr(el, "layout_height").isEmpty()) {
                    missing += "$where has no android:layout_height"
                }
                elements(el).forEach(::walk)
            }
            walk(doc.documentElement)
        }
        assertTrue(
            "a layout like this crashes the app the moment it is opened:\n" + missing.joinToString("\n"),
            missing.isEmpty()
        )
    }

    @Test
    fun `every id the code looks up is defined in a layout`() {
        val defined = HashSet<String>()
        for (file in layouts()) {
            Regex("@\\+?id/(\\w+)").findAll(file.readText()).forEach { defined += it.groupValues[1] }
        }
        assertTrue("no ids found in the layouts at all", defined.size > 10)

        // android.R.id.* is the platform's, not ours, so it is not expected in res/layout.
        val used = Regex("(?<!android\\.)R\\.id\\.(\\w+)")
        val missing = LinkedHashMap<String, String>()
        for (source in RepoFiles.kotlinSources()) {
            used.findAll(source.readText()).forEach { m ->
                val id = m.groupValues[1]
                if (id !in defined) missing[id] = source.name
            }
        }
        assertTrue(
            "findViewById would return null for: " + missing.entries.joinToString(", ") { "${it.key} (${it.value})" },
            missing.isEmpty()
        )
    }
}
