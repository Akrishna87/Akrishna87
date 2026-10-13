package io.github.akrishna87.pakkam.pdf

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a plain Word document (.docx) from the text of each PDF page: paragraphs are rebuilt
 * from the lines, and each PDF page starts a new page. Pictures and exact layout are not kept.
 */
object Docx {

    fun write(title: String, pages: List<String>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", RELS)
            put("word/_rels/document.xml.rels", DOC_RELS)
            put("word/styles.xml", STYLES)
            put("docProps/core.xml", core(title))
            put("word/document.xml", document(pages))
        }
    }

    /** Joins a page's lines back into paragraphs. */
    fun paragraphs(page: String): List<String> {
        val lines = page.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.trimEnd() }
        val lengths = lines.filter { it.isNotBlank() }.map { it.length }.sorted()
        val typical = if (lengths.isEmpty()) 0 else lengths[lengths.size * 3 / 4]
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotBlank()) out += cur.toString().trim()
            cur.clear()
        }
        for (line in lines) {
            if (line.isBlank()) {
                flush()
                continue
            }
            if (cur.isNotEmpty()) {
                if (cur.endsWith("-") && cur.length > 1 && cur[cur.length - 2].isLetter()) cur.setLength(cur.length - 1) else cur.append(' ')
            }
            cur.append(line.trim())
            // A clearly short line ends its paragraph (the last line of a paragraph, a heading…),
            // and so does a somewhat short one that finishes a sentence.
            val t = line.trim()
            val hyphenated = t.length > 1 && t.last() == '-' && t[t.length - 2].isLetter()
            if (hyphenated) continue
            if (t.length < typical * 0.6 || (t.length < typical * 0.85 && t.last() in ".!?:")) flush()
        }
        flush()
        return out
    }

    private fun document(pages: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
        for ((i, page) in pages.withIndex()) {
            if (i > 0) append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            for (p in paragraphs(page)) {
                append("<w:p><w:r><w:t xml:space=\"preserve\">").append(escape(p)).append("</w:t></w:r></w:p>")
            }
        }
        append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>""")
        append("</w:body></w:document>")
    }

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                // XML 1.0 can't hold most control characters.
                c < ' ' && c != '\t' -> Unit
                c == '￾' || c == '￿' -> Unit
                else -> append(c)
            }
        }
    }

    private fun core(title: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>${escape(title)}</dc:title><dc:creator>Pakkam</dc:creator></cp:coreProperties>"""

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>"""

    private const val DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style></w:styles>"""
}
