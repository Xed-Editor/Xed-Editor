/**
 * Copyright (c) 2022 Sebastian Thomschke and others.
 * <p>
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.tm4e.core.registry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jdt.annotation.Nullable;

public interface IThemeSource {

    /**
     * Supported theme content types
     */
    enum ContentType {
        JSON,
        YAML,
        XML
    }

    private static ContentType guessFileFormat(final String fileName) {
        final String extension = fileName.substring(fileName.lastIndexOf('.') + 1).trim().toLowerCase();

        return switch (extension) {
            case "json" -> ContentType.JSON;
            case "yaml", "yaml-tmtheme", "yml" -> ContentType.YAML;
            case "plist", "tmtheme", "xml" -> ContentType.XML;
            default -> throw new IllegalArgumentException("Unsupported file type: " + fileName);
        };
    }

    static IThemeSource fromInputStream(InputStream stream, @Nullable final String fileName, @Nullable final Charset charset) {

        final var contentType1 = guessFileFormat(fileName);


        try (var reader = new BufferedReader(new InputStreamReader(stream, charset == null ? StandardCharsets.UTF_8 : charset))) {

            var builder = new StringBuilder();

            var buffer = new char[8192 * 2];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (count > 0) {
                    builder.append(buffer, 0, count);
                }
            }

            return new IThemeSource() {
                @Override
                public Reader getReader() {
                    return new StringReader(builder.toString());
                }

                @Override
                public String getFilePath() {
                    return fileName;
                }

                @Override
                public ContentType getContentType() {
                    return contentType1;
                }

            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }


    static IThemeSource fromFile(final File file) {
        return fromFile(file, null, null);
    }

    static IThemeSource fromFile(final File file, @Nullable final ContentType contentType, @Nullable final Charset charset) {

        final var filePath = file.getPath();
        final var contentType1 = contentType == null ? guessFileFormat(filePath) : contentType;
        return new IThemeSource() {
            @Override
            public Reader getReader() throws IOException {
                // use low level api
                return new BufferedReader(new InputStreamReader(new FileInputStream(file), charset == null ? StandardCharsets.UTF_8 : charset));
            }

            @Override
            public String getFilePath() {
                return filePath;
            }

            @Override
            public ContentType getContentType() {
                return contentType1;
            }
        };
    }

    /**
     * @throws IllegalArgumentException if the content type is unsupported or cannot be determined
     */
    static IThemeSource fromResource(final Class<?> clazz, final String resourceName) {
        return fromResource(clazz, resourceName, null, null);
    }

    /**
     * @throws IllegalArgumentException if the content type is unsupported or cannot be determined
     */
    static IThemeSource fromResource(final Class<?> clazz, final String resourceName, @Nullable final ContentType contentType,
                                     @Nullable final Charset charset) {

        final var contentType1 = contentType == null ? guessFileFormat(resourceName) : contentType;
        return new IThemeSource() {
            @Override
            public Reader getReader() {
                return new BufferedReader(new InputStreamReader(
                        clazz.getResourceAsStream(resourceName),
                        charset == null ? StandardCharsets.UTF_8 : charset));
            }

            @Override
            public String getFilePath() {
                return resourceName;
            }

            @Override
            public ContentType getContentType() {
                return contentType1;
            }
        };
    }

    static IThemeSource fromString(final ContentType contentType, final String content) {

        return new IThemeSource() {
            @Override
            public Reader getReader() {
                return new StringReader(content);
            }

            @Override
            public String getFilePath() {
                return "string." + contentType.name().toLowerCase();
            }

            @Override
            public ContentType getContentType() {
                return contentType;
            }
        };
    }

    default ContentType getContentType() {
        return guessFileFormat(getFilePath());
    }

    String getFilePath();

    Reader getReader() throws IOException;
}
