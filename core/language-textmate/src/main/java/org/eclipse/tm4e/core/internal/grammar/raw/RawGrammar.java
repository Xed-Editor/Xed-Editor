/**
 * Copyright (c) 2015-2019 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 * Pierre-Yves B. - Issue #221 NullPointerException when retrieving fileTypes
 */
package org.eclipse.tm4e.core.internal.grammar.raw;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.parser.PropertySettable;

public final class RawGrammar extends PropertySettable.HashMap<@Nullable Object> implements IRawGrammar {

	private static final String FILE_TYPES = "fileTypes";
	private static final String FIRST_LINE_MATCH = "firstLineMatch";
	private static final String INJECTIONS = "injections";
	private static final String INJECTION_SELECTOR = "injectionSelector";
	private static final String NAME = "name";
	private static final String PATTERNS = "patterns";
	public static final String SCOPE_NAME = "scopeName";

	private static final long serialVersionUID = 1L;

	@Nullable
	private transient List<String> fileTypes;

	@Override
	public Collection<String> getFileTypes() {
		List<String> result = fileTypes;
		if (result == null) {
			result = new ArrayList<>();
			final Collection<?> unparsedFileTypes = (Collection<?>) get(FILE_TYPES);
			if (unparsedFileTypes != null) {
				for (final Object o : unparsedFileTypes) {
					String str = Objects.toString(o);
					// #202
					if (str.startsWith(".")) {
						str = str.substring(1);
					}
					result.add(str);
				}
			}
			fileTypes = result;
		}
		return result;
	}

	@Override
	@Nullable
	public String getFirstLineMatch() {
		return (String) get(FIRST_LINE_MATCH);
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, IRawRule> getInjections() {
		return (Map<String, IRawRule>) get(INJECTIONS);
	}

	@Nullable
	@Override
	public String getInjectionSelector() {
		return (String) get(INJECTION_SELECTOR);
	}

	@Nullable
	@Override
	public String getName() {
		return (String) get(NAME);
	}

	@SuppressWarnings("unchecked")
	@Override
	public @Nullable Collection<IRawRule> getPatterns() {
		return (Collection<IRawRule>) get(PATTERNS);
	}

	@Override
	public IRawRepository getRepository() {
		var repo = (IRawRepository) get(RawRule.REPOSITORY);
		if (repo == null) {
			repo = new RawRepository();
			setRepository(repo);
		}
		return repo;
	}

	private Object getSafe(@Nullable final Object key) {
		@SuppressWarnings("unlikely-arg-type")
		final var obj = get(key);
		if (obj == null) {
			throw new NoSuchElementException("Key '" + key + "' does not exit for grammar '" + getName() + '"');
		}
		return obj;
	}

	@Override
	public String getScopeName() {
		return (String) getSafe(SCOPE_NAME);
	}

	@Nullable
	@Override
	public Object put(final String key, @Nullable final Object value) {
		if (FILE_TYPES.equals(key))
			fileTypes = null;

		return super.put(key, value);
	}

	@Override
	@SuppressWarnings("unlikely-arg-type")
	public void putAll(@Nullable final Map<? extends String, ? extends @Nullable Object> m) {
		if (m != null && m.containsKey(FILE_TYPES))
			fileTypes = null;
		super.putAll(m);
	}

	@Override
	public void setRepository(final IRawRepository repository) {
		super.put(RawRule.REPOSITORY, repository);
	}

	@Override
	public IRawRule toRawRule() {
		return new RawRule() {
			private static final long serialVersionUID = 1L;

			@Override
			public @Nullable String getName() {
				return RawGrammar.this.getName();
			}

			@Override
			public @Nullable Collection<IRawRule> getPatterns() {
				return RawGrammar.this.getPatterns();
			}

			@Override
			public @Nullable IRawRepository getRepository() {
				return RawGrammar.this.getRepository();
			}
		};
	}
}
