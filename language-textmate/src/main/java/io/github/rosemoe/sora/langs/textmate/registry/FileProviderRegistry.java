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
package io.github.rosemoe.sora.langs.textmate.registry;

import org.eclipse.jdt.annotation.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver;

public class FileProviderRegistry {


    private final List<FileResolver> allFileResolvers = new ArrayList<>();

    private static FileProviderRegistry fileProviderRegistry;

    private FileProviderRegistry() {
        allFileResolvers.add(FileResolver.DEFAULT);
    }

    public static synchronized FileProviderRegistry getInstance() {
        if (fileProviderRegistry == null)
            fileProviderRegistry = new FileProviderRegistry();
        return fileProviderRegistry;
    }

    public synchronized void addFileProvider(FileResolver fileResolver) {
        if (fileResolver != FileResolver.DEFAULT) {
            allFileResolvers.add(fileResolver);
        }
    }

    public synchronized void removeFileProvider(FileResolver fileResolver) {
        if (fileResolver != FileResolver.DEFAULT) {
            allFileResolvers.remove(fileResolver);
        }
    }

    @Nullable
    public InputStream tryGetInputStream(String path) {
        for (var provider : allFileResolvers) {
            var stream = provider.resolveStreamByPath(path);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    public void dispose() {
        for (var provider : allFileResolvers) {
            provider.dispose();
        }

        allFileResolvers.clear();
    }

}
