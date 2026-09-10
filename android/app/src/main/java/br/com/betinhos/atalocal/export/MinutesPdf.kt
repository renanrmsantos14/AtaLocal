package br.com.betinhos.atalocal.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File

fun exportMinutesPdf(context: Context, minutes: br.com.betinhos.atalocal.summarization.Minutes, fileName: String = "ata.pdf"): File {
    val file = File(context.cacheDir, fileName)
    val document = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = android.graphics.Color.BLACK }
    val pageWidth = 595
    val pageHeight = 842
    var pageNumber = 1
    var y = 42f
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())

    fun writeLine(line: String) {
        if (y > pageHeight - 36) {
            document.finishPage(page)
            pageNumber += 1
            y = 42f
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        }
        page.canvas.drawText(line.take(95), 36f, y, paint)
        y += 18f
    }

    minutesToMarkdown(minutes).lineSequence().forEach(::writeLine)
    document.finishPage(page)
    file.outputStream().use(document::writeTo)
    document.close()
    return file
}

fun sharePdf(context: Context, pdf: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", pdf)
    return Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
