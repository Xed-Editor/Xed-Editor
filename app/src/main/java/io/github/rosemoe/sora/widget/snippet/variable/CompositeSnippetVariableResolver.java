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
package io.github.rosemoe.sora.widget.snippet.variable;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CompositeSnippetVariableResolver implements ISnippetVariableResolver {

    private final Map<String, ISnippetVariableResolver> resolverMap;

    public CompositeSnippetVariableResolver() {
        resolverMap = new HashMap<>();
    }

    public void addResolver(@NonNull ISnippetVariableResolver resolver) {
        if (resolver instanceof CompositeSnippetVariableResolver) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(resolver, "resolver must not be null");
        for (var name : resolver.getResolvableNames()) {
            resolverMap.put(name, resolver);
        }
    }

    public void removeResolver(@NonNull ISnippetVariableResolver resolver) {
        for (var name : resolver.getResolvableNames()) {
            if (resolverMap.get(name) == resolver) {
                resolverMap.remove(name);
            }
        }
    }

    @NonNull
    @Override
    public String[] getResolvableNames() {
        return new String[0];
    }

    public boolean canResolve(@NonNull String name) {
        return resolverMap.containsKey(name);
    }

    @NonNull
    @Override
    public String resolve(@NonNull String name) {
        var resolver = resolverMap.get(name);
        if (resolver != null) {
            return resolver.resolve(name);
        }
        return "";
    }

}
