package com.rk.libcommons

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import org.davidmoten.text.utils.WordWrap
import java.io.FileOutputStream

class Printer(private val context: Context, private val content: String) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val builder =
            PrintDocumentInfo.Builder("output1.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()

        callback?.onLayoutFinished(builder, true)
    }

    override fun onWrite(
        pages: Array<PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?,
    ) {
        val output = FileOutputStream(destination?.fileDescriptor)
        val pdfDocument = PdfDocument()

        // Define page width and height
        val pageWidth = 612
        val pageHeight = 792

        var yPos = 40f // Starting Y position
        var pageNumber = 0 // Start page number from 0

        // Declare variables for page and canvas, initialize first page.
        var page =
            pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
        var canvas = page.canvas

        // Configure the text paint.
        val paint =
            Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 13f
                color = Color.BLACK
            }

        val maxWidth = pageWidth - 80f // Leave some margin (40f on each side)
        
        content.split("\n").forEach { line ->
            val wrappedLines = WordWrap.from(line).maxWidth(maxWidth/6).wrap().split("\n")
            wrappedLines.forEach { wrappedLine ->
                // Check if the text will go out of the page bounds, create a new page if needed
                if (yPos + paint.textSize > pageHeight) {
                    pdfDocument.finishPage(page) // Finish the current page
                    
                    // Start a new page and reinitialize the canvas
                    pageNumber++
                    page =
                        pdfDocument.startPage(
                            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        )
                    canvas = page.canvas
                    yPos = 40f // Reset Y position for the new page
                }
                
                // Draw the text line on the canvas
                canvas.drawText(wrappedLine, 40f, yPos, paint)
                yPos += paint.textSize + 4 // Move to next line with a small gap
            }
        }
        
        // Finish the last page
        pdfDocument.finishPage(page)

        // Write the document to the file.
        try {
            pdfDocument.writeTo(output)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to generate PDF file.", Toast.LENGTH_SHORT).show()
        }

        // Close the document.
        pdfDocument.close()
        output.close()

        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }

    companion object {
        @JvmStatic
        fun print(context: Context, text: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            val jobName = "PrintJob"
            printManager?.print(jobName, Printer(context, text), null)
        }
    }
}
