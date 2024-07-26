/**
 * Copyright (c) 2015-2017 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Initial code from https://github.com/microsoft/vscode-textmate/
 * Initial copyright Copyright (C) Microsoft Corporation. All rights reserved.
 * Initial license: MIT
 *
 * Contributors:
 * - Microsoft Corporation: Initial code, written in TypeScript, licensed under MIT license
 * - Angelo Zerr <angelo.zerr@gmail.com> - translation and adaptation to Java
 */
package org.eclipse.tm4e.core.internal.grammar.raw;

import java.util.List;

import org.eclipse.tm4e.core.internal.parser.PropertySettable;
import org.eclipse.tm4e.core.internal.parser.TMParser;
import org.eclipse.tm4e.core.internal.parser.TMParser.ObjectFactory;
import org.eclipse.tm4e.core.internal.parser.TMParserJSON;
import org.eclipse.tm4e.core.internal.parser.TMParserPList;
import org.eclipse.tm4e.core.internal.parser.TMParserYAML;
import org.eclipse.tm4e.core.registry.IGrammarSource;

/**
 * TextMate Grammar reader utilities.
 */
public final class RawGrammarReader {

	public static final ObjectFactory<RawGrammar> OBJECT_FACTORY = new ObjectFactory<>() {
		@Override
		public RawGrammar createRoot() {
			return new RawGrammar();
		}

		@Override
		public PropertySettable<?> createChild(final TMParser.PropertyPath path, final Class<?> sourceType) {
			return switch (path.last().toString()) {
				case RawRule.REPOSITORY -> new RawRepository();
				case RawRule.BEGIN_CAPTURES, RawRule.CAPTURES, RawRule.END_CAPTURES, RawRule.WHILE_CAPTURES -> new RawCaptures();
				default -> List.class.isAssignableFrom(sourceType)
						? new PropertySettable.ArrayList<>()
						: new RawRule();
			};
		}
	};

	public static RawGrammar readGrammar(final IGrammarSource source) throws Exception {
		try (var reader = source.getReader()) {
			return switch (source.getContentType()) {
				case JSON -> TMParserJSON.INSTANCE.parse(reader, OBJECT_FACTORY);
				case YAML -> TMParserYAML.INSTANCE.parse(reader, OBJECT_FACTORY);
				default -> TMParserPList.INSTANCE.parse(reader, OBJECT_FACTORY);
			};
		}
	}

	/**
	 * methods should be accessed statically
	 */
	private RawGrammarReader() {
	}
}
