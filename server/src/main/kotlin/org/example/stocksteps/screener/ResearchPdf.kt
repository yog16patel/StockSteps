package org.example.stocksteps.screener

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Company Comparison Phase 4 research report (StockSteps+), rendered on the server into PDF bytes that
 * are streamed to the entitled caller and never stored. A small dependency-free writer: US Letter pages,
 * the standard Helvetica fonts (WinAnsi text), word wrapping, multi-page flow and page footers.
 */
object ResearchPdf {
    private const val WIDTH = 612.0
    private const val HEIGHT = 792.0
    private const val MARGIN = 54.0
    private const val FOOTER = 36.0
    private val cp1252: Charset = Charset.forName("windows-1252")

    /** Helvetica glyph widths (1/1000 em) for ASCII 32–126 (Adobe AFM); bold is approximated slightly wider. */
    private val WIDTHS = intArrayOf(
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556,
        278, 278, 584, 584, 584, 556, 1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722, 667,
        611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556, 333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833,
        556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584)

    private fun width(text: String, size: Double, bold: Boolean): Double =
        text.sumOf { c -> (if (c.code in 32..126) WIDTHS[c.code - 32] else 556).toDouble() } * size / 1000 * (if (bold) 1.06 else 1.0)

    /** Characters outside WinAnsi are replaced with readable ASCII. */
    fun clean(text: String): String = text.replace('−', '-').replace("≈", "~").replace("≤", "<=").replace("≥", ">=").replace("→", "->")
        .replace("ⓘ", "").replace(" ", " ").filter { it == '\n' || cp1252.newEncoder().canEncode(it) }

    private class Line(val text: String, val size: Double, val bold: Boolean, val indent: Double, val gray: Boolean, val gapBefore: Double)

    private class Layout {
        val pages = mutableListOf<MutableList<Pair<Double, Line>>>(mutableListOf())
        var y = HEIGHT - MARGIN
        fun add(line: Line) {
            val height = line.size * 1.35
            y -= line.gapBefore
            if (y - height < MARGIN + FOOTER) { pages.add(mutableListOf()); y = HEIGHT - MARGIN }
            y -= height
            pages.last().add(y to line)
        }
    }

    private fun wrap(text: String, size: Double, bold: Boolean, maxWidth: Double): List<String> = text.split('\n').flatMap { paragraph ->
        val lines = mutableListOf<String>(); var current = ""
        for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (width(candidate, size, bold) <= maxWidth || current.isEmpty()) current = candidate else { lines += current; current = word }
        }
        lines + current
    }

    /** Builds the report. Every value comes from [summary] (server-built from reported data) or the user's own notes. */
    fun render(summary: ResearchSummary, generatedFor: String = "StockSteps"): ByteArray {
        val layout = Layout()
        fun text(value: String, size: Double = 10.0, bold: Boolean = false, indent: Double = 0.0, gray: Boolean = false, gap: Double = 0.0) {
            wrap(clean(value), size, bold, WIDTH - 2 * MARGIN - indent).forEachIndexed { i, line -> layout.add(Line(line, size, bold, indent, gray, if (i == 0) gap else 0.0)) }
        }
        text(summary.title, 20.0, bold = true)
        text("Company comparison research report", 11.0, gray = true, gap = 2.0)
        text("Companies: ${summary.symbols.joinToString(", ")}", 10.0, gap = 10.0)
        text("Report generated: ${summary.generatedAt.take(16).replace('T', ' ')} UTC", 10.0)
        text("Financial data as of: ${summary.dataAsOf?.take(10) ?: "not reported"} (values are not live)", 10.0)
        text("Checklist progress: ${summary.progress.reviewed} of ${summary.progress.total} reviewed, ${summary.progress.needsMore} need more research, " +
            "${summary.progress.notReviewed} not reviewed", 10.0)
        if (summary.sampleData) text("Sample data for development, not real financial data.", 10.0, bold = true, gap = 4.0)
        for (section in summary.sections) {
            text(section.title, 13.0, bold = true, gap = 14.0)
            for (item in section.items) {
                text("${item.kind.label}" + (item.attribution?.let { " - $it" } ?: ""), 7.5, gray = true, indent = 10.0, gap = 4.0)
                text("- ${item.text}", 10.0, indent = 10.0)
            }
        }
        val pages = layout.pages.filter { it.isNotEmpty() }
        return write(pages, summary.title, "Education, not investment advice. $generatedFor research report; past results don't predict future results.")
    }

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    private fun n(value: Double) = String.format(java.util.Locale.US, "%.2f", value)

    private fun write(pages: List<List<Pair<Double, Line>>>, title: String, footer: String): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun obj(body: ByteArray) { offsets += out.size(); out.write("${offsets.size} 0 obj\n".toByteArray(cp1252)); out.write(body); out.write("\nendobj\n".toByteArray(cp1252)) }
        fun obj(body: String) = obj(body.toByteArray(cp1252))
        out.write("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        val pageCount = pages.size
        // 1 catalog, 2 pages, 3 Helvetica, 4 Helvetica-Bold, 5 info, then (page, content) pairs.
        val kids = (0 until pageCount).joinToString(" ") { "${6 + it * 2} 0 R" }
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [$kids] /Count $pageCount >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        obj("<< /Title (${escape(clean(title))}) /Producer (StockSteps) >>")
        pages.forEachIndexed { index, lines ->
            obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(WIDTH)} ${n(HEIGHT)}] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${7 + index * 2} 0 R >>")
            val content = StringBuilder()
            for ((y, line) in lines) {
                content.append(if (line.gray) "0.42 0.47 0.53 rg\n" else "0.07 0.09 0.12 rg\n")
                content.append("BT /${if (line.bold) "F2" else "F1"} ${n(line.size)} Tf ${n(MARGIN + line.indent)} ${n(y)} Td (${escape(line.text)}) Tj ET\n")
            }
            content.append("0.80 0.84 0.88 RG 0.5 w ${n(MARGIN)} ${n(MARGIN + 14)} m ${n(WIDTH - MARGIN)} ${n(MARGIN + 14)} l S\n")
            content.append("0.42 0.47 0.53 rg BT /F1 7.5 Tf ${n(MARGIN)} ${n(MARGIN)} Td (${escape(clean(footer))}) Tj ET\n")
            content.append("BT /F1 7.5 Tf ${n(WIDTH - MARGIN - 50)} ${n(MARGIN)} Td (Page ${index + 1} of $pageCount) Tj ET\n")
            val bytes = content.toString().toByteArray(cp1252)
            obj("<< /Length ${bytes.size} >>\nstream\n".toByteArray(cp1252) + bytes + "endstream".toByteArray(cp1252))
        }
        val xref = out.size()
        val table = StringBuilder("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { table.append(String.format("%010d 00000 n \n", it)) }
        table.append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R /Info 5 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.write(table.toString().toByteArray(cp1252))
        return out.toByteArray()
    }
}
