/*
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 */
package io.github.rosemoe.sora.text;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * Helper class for creating or saving {@link Content} objects, with minimal extra memory usage when
 *  processing.
 *
 * @author Rosemoe
 */
public class ContentIO {

    private final static int BUFFER_SIZE = 16384;

    /**
     * Create a {@link Content} from stream.
     * The stream will get closed if the operation is successfully done.
     * @param stream Source stream
     */
    @NonNull
    public static Content createFrom(@NonNull InputStream stream) throws IOException {
        return createFrom(stream, Charset.defaultCharset());
    }

    /**
     * Create a {@link Content} from stream.
     * The stream will get closed if the operation is successfully done.
     * @param stream Source stream
     * @param charset Charset for decoding the content
     */
    @NonNull
    public static Content createFrom(@NonNull InputStream stream, @NonNull Charset charset) throws IOException {
        return createFrom(new InputStreamReader(stream, charset));
    }

    /**
     * Create a {@link Content} from reader.
     * <p>
     * The reader will get closed if the operation is successfully done.
     */
    @NonNull
    public static Content createFrom(@NonNull Reader reader) throws IOException {
        var content = new Content();
        content.setUndoEnabled(false);
        var buffer = new char[BUFFER_SIZE];
        var wrapper = new CharArrayWrapper(buffer, 0);
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (count > 0) {
                if (buffer[count - 1] == '\r') {
                    var peek = reader.read();
                    if (peek == '\n') {
                        wrapper.setDataCount(count - 1);
                        var line = content.getLineCount() - 1;
                        content.insert(line, content.getColumnCount(line), wrapper);
                        line = content.getLineCount() - 1;
                        content.insert(line, content.getColumnCount(line), "\r\n");
                        continue;
                    } else if (peek != -1) {
                        wrapper.setDataCount(count);
                        var line = content.getLineCount() - 1;
                        content.insert(line, content.getColumnCount(line), wrapper);
                        line = content.getLineCount() - 1;
                        content.insert(line, content.getColumnCount(line), String.valueOf((char) peek));
                        continue;
                    }
                }
                wrapper.setDataCount(count);
                var line = content.getLineCount() - 1;
                content.insert(line, content.getColumnCount(line), wrapper);
            }
        }
        reader.close();
        content.setUndoEnabled(true);
        return content;
    }

    /**
     * Write the text to the given stream with default charset. Close the stream if {@code closeOnSucceed} is true.
     *
     * @param text Text to be written
     * @param stream Output stream
     * @param closeOnSucceed If true, the stream will be closed when operation is successfully
     */
    public static void writeTo(@NonNull Content text, @NonNull OutputStream stream, boolean closeOnSucceed) throws IOException {
        writeTo(text, stream, Charset.defaultCharset(), closeOnSucceed);
    }

    /**
     * Write the text to the given stream with given charset. Close the stream if {@code closeOnSucceed} is true.
     *
     * @param text Text to be written
     * @param stream Output stream
     * @param charset Charset of output bytes
     * @param closeOnSucceed If true, the stream will be closed when operation is successfully
     */
    public static void writeTo(@NonNull Content text, @NonNull OutputStream stream, @NonNull Charset charset, boolean closeOnSucceed) throws IOException {
        writeTo(text, new OutputStreamWriter(stream, charset), closeOnSucceed);
    }

    /**
     * Write the text to the given writer. Close the writer if {@code closeOnSucceed} is true.
     * <p>
     * If you use {@link BufferedWriter}, make sure you set an appropriate buffer size. We recommend using the default size (8192) or larger.
     *
     * @param text Text to be written
     * @param writer Output writer
     * @param closeOnSucceed If true, the stream will be closed when operation is successfully
     */
    public static void writeTo(@NonNull Content text, @NonNull Writer writer, boolean closeOnSucceed) throws IOException {
        // Use buffered writer to avoid frequently IO when there are a lot of short lines
        final var buffered = (writer instanceof BufferedWriter) ? (BufferedWriter)writer : new BufferedWriter(writer, BUFFER_SIZE);
        try {
            text.runReadActionsOnLines(0, text.getLineCount() - 1, (Content.ContentLineConsumer2) (index, line, flag) -> {
                try {
                    // Write line content
                    buffered.write(line.getBackingCharArray(), 0, line.length());
                    // Write line feed (the last line has empty line feed)
                    buffered.write(line.getLineSeparator().getChars());
                } catch (IOException e) {
                    // To be handled by outer code
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            var cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw e;
            }
        }
        buffered.flush();
        if (closeOnSucceed) {
            buffered.close();
        }
    }

}
