package com.rk.lsp

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

class LspFrameLogger(private val onFrame: ((String) -> Unit)? = null) {
    private val buffer = ByteArrayOutputStream()
    private val headerRegex = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE)

    fun append(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        buffer.write(bytes, offset, length)
        process()
    }

    private fun process() {
        while (true) {
            val bytes = buffer.toByteArray()

            val (bodyStart, separatorLen) = findSeparator(bytes) ?: return

            val header = String(bytes, 0, bodyStart, Charsets.US_ASCII)
            val contentLength = headerRegex.find(header)?.groupValues?.get(1)?.toIntOrNull() ?: return

            val totalLength = bodyStart + separatorLen + contentLength
            if (bytes.size < totalLength) return

            val json = String(bytes, bodyStart + separatorLen, contentLength, Charsets.UTF_8)
            onFrame?.invoke(json)

            buffer.reset()
            buffer.write(bytes, totalLength, bytes.size - totalLength)
        }
    }

    // Usual LSP frame structure: HEADER\r\n\r\nBODY
    // -> see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#baseProtocol
    private fun findSeparator(bytes: ByteArray): Pair<Int, Int>? {
        val cr = '\r'.code.toByte()
        val lf = '\n'.code.toByte()
        val size = bytes.size

        for (i in 0 until size - 1) {
            // 1. Standard CRLF separator: \r\n\r\n
            if (i + 3 < size && bytes[i] == cr && bytes[i + 1] == lf && bytes[i + 2] == cr && bytes[i + 3] == lf) {
                return i to 4
            }

            // 2. Fallback LF separator: \n\n
            if (bytes[i] == lf && bytes[i + 1] == lf) {
                return i to 2
            }
        }
        return null
    }
}

class LoggingInputStream(input: InputStream, onFrame: ((String) -> Unit)? = null) : FilterInputStream(input) {
    private val logger = LspFrameLogger(onFrame)

    override fun read(): Int {
        val byte = super.read()
        if (byte != -1) {
            logger.append(byteArrayOf(byte.toByte()))
        }
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) {
            logger.append(b, off, count)
        }
        return count
    }
}

class LoggingOutputStream(output: OutputStream, onFrame: ((String) -> Unit)? = null) : FilterOutputStream(output) {
    private val logger = LspFrameLogger(onFrame)

    override fun write(b: Int) {
        logger.append(byteArrayOf(b.toByte()))
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        logger.append(b, off, len)
        out.write(b, off, len)
    }

    override fun write(b: ByteArray) = write(b, 0, b.size)
}
