package com.rk.lsp

import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

class LspFrameLogger(private val onFrame: ((String) -> Unit)? = null, private val charset: Charset = Charsets.UTF_8) {
    private val buffer = StringBuilder()
    private val headerRegex = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE)

    fun append(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        buffer.append(String(bytes, offset, length, charset))
        process()
    }

    private fun process() {
        while (true) {
            // LSP frame structure: HEADER\r\n\r\nBODY
            val bodyStart =
                buffer.indexOf("\r\n\r\n").takeIf { it >= 0 }?.plus(4)
                    ?: buffer
                        .indexOf("\n\n") // Tolerate servers that use bare LF
                        .takeIf { it >= 0 }
                        ?.plus(2)
                    ?: return

            val header = buffer.substring(0, bodyStart).trim()
            val contentLength = headerRegex.find(header)?.groupValues?.get(1)?.toIntOrNull() ?: return

            if (buffer.length < bodyStart + contentLength) return

            val json = buffer.substring(bodyStart, bodyStart + contentLength)
            onFrame?.invoke(json)

            buffer.delete(0, bodyStart + contentLength)
        }
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
