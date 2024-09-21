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
package io.github.rosemoe.sora.lang.completion.snippet;


import androidx.annotation.NonNull;

import io.github.rosemoe.sora.text.TextUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public class CodeSnippet implements Cloneable {

    private final List<SnippetItem> items;
    private final List<PlaceholderDefinition> placeholders;

    public CodeSnippet(@NonNull List<SnippetItem> items, @NonNull List<PlaceholderDefinition> placeholders) {
        this.items = items;
        this.placeholders = placeholders;
    }

    public boolean checkContent() {
        int index = 0;
        for (var item : items) {
            if (item.getStartIndex() != index) {
                return false;
            }
            if (item instanceof PlaceholderItem) {
                if (!placeholders.contains(((PlaceholderItem) item).getDefinition())) {
                    return false;
                }
            }
            index = item.getEndIndex();
        }
        var set = new TreeSet<Integer>();
        for (var placeholder : placeholders) {
            if (!set.contains(placeholder.getId())) {
                set.add(placeholder.getId());
            } else {
                return false;
            }
        }
        return true;
    }

    public List<SnippetItem> getItems() {
        return items;
    }

    public List<PlaceholderDefinition> getPlaceholderDefinitions() {
        return placeholders;
    }

    @NonNull
    @Override
    public CodeSnippet clone() {
        var defs = new ArrayList<PlaceholderDefinition>(placeholders.size());
        var map = new HashMap<PlaceholderDefinition, PlaceholderDefinition>();
        for (PlaceholderDefinition placeholder : placeholders) {
            var n = new PlaceholderDefinition(placeholder.getId(), placeholder.getChoices(), placeholder.getElements(), placeholder.getTransform());
            defs.add(n);
            map.put(placeholder, n);
        }
        var itemsClone = new ArrayList<SnippetItem>(items.size());
        for (SnippetItem item : items) {
            var n = item.clone();
            itemsClone.add(n);
            if (n instanceof PlaceholderItem) {
                if (map.get(((PlaceholderItem) n).getDefinition()) != null) {
                    ((PlaceholderItem) n).setDefinition(map.get(((PlaceholderItem) n).getDefinition()));
                }
            }
        }
        return new CodeSnippet(itemsClone, defs);
    }

    public static class Builder {

        private final List<PlaceholderDefinition> definitions;
        private List<SnippetItem> items = new ArrayList<>();
        private int index;

        public Builder() {
            this(new ArrayList<>());
        }

        public Builder(@NonNull List<PlaceholderDefinition> definitions) {
            this.definitions = definitions;
        }

        public Builder addPlainText(String text) {
            if (!items.isEmpty() && items.get(items.size() - 1) instanceof PlainTextItem) {
                // Merge plain texts
                var item = (PlainTextItem) items.get(items.size() - 1);
                item.setText(item.getText() + text);
                item.setIndex(item.getStartIndex(), item.getEndIndex() + text.length());
                index += text.length();
                return this;
            }
            items.add(new PlainTextItem(text, index));
            index += text.length();
            return this;
        }

        public Builder addInterpolatedShell(String shell) {
            items.add(new InterpolatedShellItem(shell, index));
            return this;
        }

        public Builder addPlaceholder(int id) {
            return addPlaceholder(id, (String) null);
        }

        public Builder addPlaceholder(int id, List<String> choices) {
            if (choices.isEmpty()) {
                return addPlaceholder(id);
            } else if (choices.size() == 1) {
                return addPlaceholder(id, choices.get(0));
            }
            addPlaceholder(id, choices.get(0));
            PlaceholderDefinition def = null;
            for (var definition : definitions) {
                if (definition.getId() == id) {
                    def = definition;
                    break;
                }
            }
            Objects.requireNonNull(def).setChoices(choices);
            return this;
        }

        public Builder addPlaceholder(int id, Transform transform) {
            if (transform == null) {
                return addPlaceholder(id);
            }
            addPlaceholder(id);
            PlaceholderDefinition def = null;
            for (var definition : definitions) {
                if (definition.getId() == id) {
                    def = definition;
                    break;
                }
            }
            Objects.requireNonNull(def).setTransform(transform);
            return this;
        }

        public Builder addPlaceholder(int id, String defaultValue) {
            final var elements = new ArrayList<PlaceHolderElement>();
            if (!android.text.TextUtils.isEmpty(defaultValue)) {
                elements.add(new PlainPlaceholderElement(defaultValue));
            }
            return addComplexPlaceholder(id, elements);
        }

        public Builder addComplexPlaceholder(int id, List<PlaceHolderElement> elements) {
            PlaceholderDefinition def = null;
            for (var definition : definitions) {
                if (definition.getId() == id) {
                    def = definition;
                    break;
                }
            }
            if (def == null) {
                def = new PlaceholderDefinition(id);
                definitions.add(def);
            }

            def.getElements().addAll(elements);

            var item = new PlaceholderItem(def, index);
            items.add(item);
            return this;
        }

        public Builder addVariable(String name, String defaultValue) {
            items.add(new VariableItem(index, name, defaultValue));
            return this;
        }

        public Builder addVariable(String name, Transform transform) {
            items.add(new VariableItem(index, name, null, transform));
            return this;
        }

        public Builder addVariable(VariableItem item) {
            item.setIndex(index);
            items.add(item);
            return this;
        }

        public CodeSnippet build() {
            return new CodeSnippet(items, definitions);
        }

    }

}
