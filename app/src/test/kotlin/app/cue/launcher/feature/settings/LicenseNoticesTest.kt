package app.cue.launcher.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LicenseNoticesTest {

    @Test
    fun `every notice the app lists is a file the build packages`() {
        LicenseNotice.entries.forEach { notice ->
            assertTrue(sourceOf(notice.asset).isFile) { "${notice.asset} is neither in the root nor in LICENSES/" }
        }
    }

    @Test
    fun `every license text in LICENSES is listed in the app`() {
        val listed = LicenseNotice.entries.map { it.asset }.toSet()
        val packaged = File(REPO_ROOT, "LICENSES").listFiles().orEmpty()
            .map { it.name }
            .filterNot { it.startsWith(".") }
            .toSet()

        assertEquals(emptySet<String>(), packaged - listed) { "license texts the app does not offer" }
    }

    @Test
    fun `the notice declares the license as GPL-3 or later with the section 7 terms`() {
        val notice = File(REPO_ROOT, "NOTICE").readText().replace(Regex("\\s+"), " ")

        assertTrue(notice.contains("SPDX-License-Identifier: GPL-3.0-or-later"))
        assertTrue(notice.contains("either version 3 of the License, or (at your option) any later version"))
        assertTrue(notice.contains("section 7(c)") && notice.contains("section 7(e)"))
    }

    @Test
    fun `hard-wrapped lines of a paragraph are joined`() {
        assertEquals(
            listOf("Permission is hereby granted, free of charge, to any person."),
            reflowLicense("Permission is hereby granted,\n   free of charge, to any person.\n"),
        )
    }

    @Test
    fun `list items, numbered sections and paragraphs keep their own lines`() {
        val text = "TERMS\r\n-----\r\n\r\n  0. Definitions.\r\n\r\n  a) The work must\r\n  carry notices.\r\n" +
            "  b) Second.\r\n\r\n- one\r\n- two\r\n"

        assertEquals(
            listOf("TERMS", "0. Definitions.", "a) The work must carry notices.\nb) Second.", "- one\n- two"),
            reflowLicense(text),
        )
    }

    @Test
    fun `a number that a wrap puts at the start of a line stays in its sentence`() {
        val text = "    b) The work must carry prominent notices stating that it is\n" +
            "    released under this License and any conditions added under section\n" +
            "    7.  This requirement modifies the requirement in section 4 to\n" +
            "    \"keep intact all notices\".\n"

        assertEquals(
            listOf(
                "b) The work must carry prominent notices stating that it is released under this License and " +
                    "any conditions added under section 7.  This requirement modifies the requirement in " +
                    "section 4 to \"keep intact all notices\".",
            ),
            reflowLicense(text),
        )
    }

    @Test
    fun `an enumeration after a colon stays in its sentence`() {
        val text = "  Developers that use the GNU GPL protect your rights with two steps:\n" +
            "(1) assert copyright on the software, and (2) offer you this License\n" +
            "giving you legal permission to copy, distribute and/or modify it.\n"

        assertEquals(
            listOf(
                "Developers that use the GNU GPL protect your rights with two steps: (1) assert copyright on " +
                    "the software, and (2) offer you this License giving you legal permission to copy, " +
                    "distribute and/or modify it.",
            ),
            reflowLicense(text),
        )
    }

    @Test
    fun `a box of asterisks is taken apart into heading and paragraph`() {
        val text = """
            ************************************************************************
            *                                                                      *
            *  6. Disclaimer of Warranty                                           *
            *  -------------------------                                           *
            *                                                                      *
            *  Covered Software is provided under this License on an "as is"       *
            *  basis, without warranty of any kind, either expressed, implied, or  *
            *  statutory.                                                          *
            *                                                                      *
            ************************************************************************
        """.trimIndent()

        assertEquals(
            listOf(
                "6. Disclaimer of Warranty",
                "Covered Software is provided under this License on an \"as is\" basis, without warranty of " +
                    "any kind, either expressed, implied, or statutory.",
            ),
            reflowLicense(text),
        )
    }

    @Test
    fun `addresses keep their own lines and so does the text after them`() {
        val text = "- Material Design Icons (MDI) by Pictogrammers.\n" +
            "  https://pictogrammers.com/library/mdi/\n" +
            "  https://github.com/Templarian/MaterialDesign-Webfont\n" +
            "  Covered by the Pictogrammers Free License, which places the fonts\n" +
            "  under the Apache License 2.0.\n"

        assertEquals(
            listOf(
                "- Material Design Icons (MDI) by Pictogrammers.\n" +
                    "https://pictogrammers.com/library/mdi/\n" +
                    "https://github.com/Templarian/MaterialDesign-Webfont\n" +
                    "Covered by the Pictogrammers Free License, which places the fonts under the Apache License 2.0.",
            ),
            reflowLicense(text),
        )
    }

    @Test
    fun `siblings of a hanging-indent list split without a full stop`() {
        val text = "(a) the first item, which wraps\n    onto a second line\n(b) the second item\n"

        assertEquals(
            listOf("(a) the first item, which wraps onto a second line\n(b) the second item"),
            reflowLicense(text),
        )
    }

    @Test
    fun `copyright lines and markdown headings keep their own lines`() {
        val text = "SLF4J API:\nCopyright (c) 2004-2022 QOS.ch Sarl (Switzerland)\nAll rights reserved.\n\n" +
            "# Code: MIT (https://opensource.org/licenses/MIT)\nThe MIT license applies to all non-font files.\n"

        assertEquals(
            listOf(
                "SLF4J API:\nCopyright (c) 2004-2022 QOS.ch Sarl (Switzerland)\nAll rights reserved.",
                "# Code: MIT (https://opensource.org/licenses/MIT)\nThe MIT license applies to all non-font files.",
            ),
            reflowLicense(text),
        )
    }

    @Test
    fun `a copyright line that continues a sentence stays in it`() {
        assertEquals(
            listOf("SLF4J API. MIT License, Copyright (c) 2004-2022 QOS.ch Sarl (Switzerland)."),
            reflowLicense("SLF4J API. MIT License,\n  Copyright (c) 2004-2022 QOS.ch Sarl (Switzerland).\n"),
        )
    }

    @Test
    fun `the real license texts reflow without box debris or split sentences`() {
        val texts = LicenseNotice.entries.associate { it.asset to reflowLicense(sourceOf(it.asset).readText()) }
        val lines = texts.values.flatten().flatMap { it.lines() }

        assertTrue(lines.none { line -> line.isNotBlank() && line.trim().all { it == '*' } }) { "a line of asterisks" }
        assertTrue(lines.none { it.startsWith("7.  This requirement") }) { "GPL §5(b) split from its paragraph" }
        assertTrue(lines.none { it.startsWith("(1) assert copyright") }) { "the GPL preamble split" }
        val mpl = texts.getValue(LicenseNotice.MPL.asset)
        val warranty = mpl.indexOf("6. Disclaimer of Warranty")
        assertTrue(warranty >= 0) { "MPL §6 heading" }
        assertTrue(mpl[warranty + 1].startsWith("Covered Software is provided") && '\n' !in mpl[warranty + 1]) {
            "MPL §6 is one paragraph: ${mpl[warranty + 1]}"
        }
    }

    private fun sourceOf(asset: String): File =
        File(REPO_ROOT, asset).takeIf(File::isFile) ?: File(REPO_ROOT, "LICENSES/$asset")

    private companion object {

        val REPO_ROOT: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "LICENSES").isDirectory && File(it, "NOTICE").isFile }
    }
}
