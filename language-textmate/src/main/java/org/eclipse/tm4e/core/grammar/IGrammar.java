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
package org.eclipse.tm4e.core.grammar;

import java.time.Duration;
import java.util.Collection;

import org.eclipse.jdt.annotation.Nullable;

/**
 * TextMate grammar API.
 *
 * @see <a href="https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/main.ts#L200">
 *      github.com/microsoft/vscode-textmate/blob/main/src/main.ts</a>
 */
public interface IGrammar {

	/**
	 * Returns the name of the grammar.
	 *
	 * @return the name of the grammar.
	 */
	@Nullable
	String getName();

	/**
	 * Returns the scope name of the grammar.
	 *
	 * @return the scope name of the grammar.
	 */
	String getScopeName();

	/**
	 * Returns the supported file types and null otherwise.
	 *
	 * @return the supported file types and null otherwise.
	 */
	Collection<String> getFileTypes();

	/**
	 * Tokenize `lineText`.
	 *
	 * @param lineText the line text to tokenize.
	 *
	 * @return the result of the tokenization.
	 */
	ITokenizeLineResult<IToken[]> tokenizeLine(String lineText);

	/**
	 * Tokenize `lineText` using previous line state `prevState`.
	 *
	 * @param lineText the line text to tokenize.
	 * @param prevState previous line state.
	 * @param timeLimit duration after which tokenization is aborted, in which case the returned result will have
	 *            {@link ITokenizeLineResult#isStoppedEarly()} set to <code>true</code>
	 *
	 * @return the result of the tokenization.
	 */
	ITokenizeLineResult<IToken[]> tokenizeLine(String lineText, @Nullable IStateStack prevState, @Nullable Duration timeLimit);

	/**
	 * Tokenize `lineText`.
	 * <p>
	 * The result contains the tokens in binary format. Each token occupies two array indices. For token <code>i</code>:
	 * <ul>
	 * <li>at offset <code>2*i</code> => startIndex
	 * <li>at offset <code>2*i + 1</code> => metadata
	 * </ul>
	 * The metadata in binary format contains the following information:
	 *
	 * <pre>
	 * - language
	 * - token type (regex, string, comment, other)
	 * - font style
	 * - foreground color
	 * - background color
	 * </pre>
	 *
	 * e.g. for getting the languageId:
	 * <code>(token & EncodedTokenDataConsts.LANGUAGEID_MASK) >>> EncodedTokenDataConsts.LANGUAGEID_OFFSET</code>
	 */
	ITokenizeLineResult<int[]> tokenizeLine2(String lineText);

	/**
	 * Tokenize `lineText` using previous line state `prevState`.
	 * <p>
	 * The result contains the tokens in binary format. Each token occupies two array indices. For token <code>i</code>:
	 * <ul>
	 * <li>at offset <code>2*i</code> => startIndex
	 * <li>at offset <code>2*i + 1</code> => metadata
	 * </ul>
	 * The metadata in binary format contains the following information:
	 *
	 * <pre>
	 * - language
	 * - token type (regex, string, comment, other)
	 * - font style
	 * - foreground color
	 * - background color
	 * </pre>
	 *
	 * e.g. for getting the languageId:
	 * <code>(token[2*i+1] & EncodedTokenDataConsts.LANGUAGEID_MASK) >>> EncodedTokenDataConsts.LANGUAGEID_OFFSET</code>
	 *
	 * @param lineText the line text to tokenize.
	 * @param prevState previous line state.
	 * @param timeLimit duration after which tokenization is aborted, in which case the returned result will have
	 *            {@link ITokenizeLineResult#isStoppedEarly()} set to <code>true</code>
	 */
	ITokenizeLineResult<int[]> tokenizeLine2(String lineText, @Nullable IStateStack prevState, @Nullable Duration timeLimit);
}
