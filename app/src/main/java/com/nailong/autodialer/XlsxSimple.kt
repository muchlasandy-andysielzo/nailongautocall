package com.nailong.autodialer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Lightweight OOXML reader/writer for a simple workbook whose phone numbers are in column A.
 * It intentionally avoids third-party spreadsheet dependencies on Android.
 */
object XlsxSimple {
    fun readFirstColumn(input: InputStream): List<String> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) entries[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        val shared = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { bytes ->
            val p = Xml.newPullParser(); p.setInput(bytes.inputStream(), "UTF-8")
            var inSi = false; val sb = StringBuilder()
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "si") { inSi = true; sb.setLength(0) }
                else if (inSi && p.eventType == XmlPullParser.START_TAG && p.name == "t") sb.append(p.nextText())
                else if (p.eventType == XmlPullParser.END_TAG && p.name == "si") { shared.add(sb.toString()); inSi = false }
                p.next()
            }
        }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("Sheet pertama tidak ditemukan")
        val values = mutableListOf<String>()
        val parser = Xml.newPullParser(); parser.setInput(sheet.inputStream(), "UTF-8")
        var cellRef = ""; var cellType = ""; var inValue = false; var inInline = false
        val inlineText = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> { cellRef = parser.getAttributeValue(null, "r") ?: ""; cellType = parser.getAttributeValue(null, "t") ?: ""; inlineText.setLength(0) }
                    "v" -> if (cellRef.startsWith("A")) inValue = true
                    "is" -> if (cellRef.startsWith("A") && cellType == "inlineStr") inInline = true
                    "t" -> if (inInline) inlineText.append(parser.nextText())
                }
                XmlPullParser.TEXT -> if (inValue && cellRef.startsWith("A")) {
                    val raw = parser.text.trim()
                    val v = if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1) ?: "" else raw
                    if (v.isNotBlank() && !(values.isEmpty() && v.lowercase() in listOf("nomor", "no", "phone", "nomor telepon"))) values.add(v)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "is" -> {
                        inInline = false
                        val v = inlineText.toString().trim()
                        if (v.isNotBlank() && !(values.isEmpty() && v.lowercase() in listOf("nomor", "no", "phone", "nomor telepon"))) values.add(v)
                    }
                }
            }
            parser.next()
        }
        return values
    }

    fun write(output: OutputStream, contacts: List<ContactCall>) {
        ZipOutputStream(output).use { z ->
            fun entry(name: String, body: String) {
                z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Laporan" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val rows = StringBuilder()
            fun cell(ref: String, value: String) = """<c r="$ref" t="inlineStr"><is><t>${xml(value)}</t></is></c>"""
            rows.append("<row r=\"1\">")
            listOf("Nomor Telepon", "Jumlah Percobaan", "Status", "Waktu Terakhir").forEachIndexed { i, s -> rows.append(cell("${'A'+i}1", s)) }
            rows.append("</row>")
            contacts.forEachIndexed { i, c ->
                val r = i + 2
                rows.append("<row r=\"$r\">")
                listOf(c.number, c.attempts.toString(), c.status, c.lastUpdate).forEachIndexed { j, s -> rows.append(cell("${'A'+j}$r", s)) }
                rows.append("</row>")
            }
            entry("xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>""")
        }
    }
    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
