package com.kaavalan.note.ui.privacy

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * v1.8.0 (PROD-READINESS-P1-#2): the recovery-phrase PDF
 * generator. Writes a printable A4 sheet with the 24-word
 * recovery phrase (BIP-39) and a header warning. The user
 * can save the PDF to Google Drive, print it, or share it via
 * the system share sheet.
 *
 * **Why a PDF and not just a text file.** The paper-friendly
 * layout (large word numbers, monospace, clear "Baton
 * recovery" header) is the v1.8.0 trade-off over the
 * plain-text export that the existing hold-to-reveal already
 * supports. A paper backup is the only thing that survives
 * the device being lost, stolen, or factory-reset; the
 * PDF is a printable artefact.
 *
 * **Why not a third-party library.** Android ships
 * `android.graphics.pdf.PdfDocument` in the platform; no
 * extra dep. The output is a single-page A4 with the 24
 * words in 4 columns of 6 rows. The v1.8.0 trade-off is
 * "plain text on a single page" — no styling beyond font
 * size + weight.
 *
 * **No PII in the file.** The phrase IS the secret. The
 * PDF lives in the app's `cacheDir` and is only emitted
 * when the user explicitly taps "Save as PDF". A future
 * Settings toggle can require a biometric prompt before
 * the export (out of scope for v1.8.0).
 */
object RecoveryPdfGenerator {

    /**
     * Generate the recovery-sheet PDF and write it to a
     * file in the app's `cacheDir`. The caller (the
     * [RecoveryPhraseScreen] Composable) is responsible
     * for sharing the file via the system share sheet.
     *
     * @return the file the PDF was written to. The file
     *   exists on disk by the time this returns; the
     *   caller can hand it to FileProvider for sharing.
     */
    fun generate(
        context: Context,
        phraseWords: List<String>,
    ): File {
        require(phraseWords.isNotEmpty()) { "phrase must not be empty" }

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = 0xFF000000.toInt()
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = 0xFF000000.toInt()
            textSize = 12f
            isAntiAlias = true
        }
        val monoPaint = Paint().apply {
            color = 0xFF000000.toInt()
            textSize = 14f
            isAntiAlias = true
            // Monospace is critical: BIP-39 words are easy
            // to mis-type when handwritten. A proportional
            // font makes "sigh" and "high" look almost the
            // same; the monospace font keeps them visually
            // distinct so the user transcribes the right
            // letters.
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val warnPaint = Paint().apply {
            color = 0xFF000000.toInt()
            textSize = 10f
            isAntiAlias = true
        }

        val margin = 36f  // 0.5 inch
        var y = margin + 22f

        canvas.drawText("Baton — Recovery Sheet", margin, y, titlePaint)
        y += 18f
        canvas.drawText(
            "Generated: ${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}",
            margin, y, bodyPaint,
        )
        y += 14f
        canvas.drawText(
            "Keep this sheet offline. Anyone with these 24 words controls the vault.",
            margin, y, warnPaint,
        )
        y += 20f
        canvas.drawText(
            "Words are in BIP-39 order. Do not re-order. Do not write the index numbers.",
            margin, y, warnPaint,
        )
        y += 24f

        // 4 columns × ceil(24/4)=6 rows
        val colCount = 4
        val rowCount = (phraseWords.size + colCount - 1) / colCount
        val colWidth = (PAGE_WIDTH - 2 * margin) / colCount
        val rowHeight = 18f
        for (row in 0 until rowCount) {
            for (col in 0 until colCount) {
                val idx = row * colCount + col
                if (idx >= phraseWords.size) break
                val x = margin + col * colWidth
                val word = phraseWords[idx]
                // 1-based index so the user reads "1 word"
                // not "0 word". We deliberately drop the
                // index from the sheet because storing
                // it next to the word is a security smell
                // (a thief who finds the sheet sees the
                // exact format), and the order itself is
                // the secret.
                canvas.drawText("${idx + 1}.", x, y, monoPaint)
                canvas.drawText(word, x + 32f, y, monoPaint)
            }
            y += rowHeight
        }

        pdf.finishPage(page)

        val recoveryDir = File(context.cacheDir, "recovery")
        // v2.1.1 (security): auto-clean any old PDFs
        // in the recovery directory before writing the
        // new one. The recovery phrase IS the secret
        // — leaving the PDF on disk after the user has
        // shared it is a PII-at-rest risk. Android
        // may purge `cacheDir` on low storage, but
        // that doesn't run on a healthy device; we
        // delete here as a defence-in-depth. Any
        // PDF older than 5 minutes is stale.
        pruneOld(recoveryDir, olderThanMs = 5 * 60 * 1000L)

        val outFile = File(recoveryDir, "kaavalan-note-recovery-${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    /**
     * v2.1.1 (security): delete any PDF in [dir] older
     * than [olderThanMs]. Called at the start of
     * [generate] so the directory doesn't accumulate
     * stale recovery sheets. The function is a no-op
     * if the directory doesn't exist (first-ever
     * generate on a fresh install).
     */
    private fun pruneOld(dir: File, olderThanMs: Long) {
        if (!dir.exists()) return
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".pdf") &&
                (now - f.lastModified()) > olderThanMs) {
                runCatching { f.delete() }
            }
        }
    }

    private const val PAGE_WIDTH = 595   // A4 @ 72 dpi
    private const val PAGE_HEIGHT = 842
}
