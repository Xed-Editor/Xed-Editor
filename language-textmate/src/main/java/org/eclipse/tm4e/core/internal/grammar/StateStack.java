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
package org.eclipse.tm4e.core.internal.grammar;

import static org.eclipse.tm4e.core.internal.utils.NullSafetyHelper.castNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.grammar.IStateStack;
import org.eclipse.tm4e.core.internal.rule.IRuleRegistry;
import org.eclipse.tm4e.core.internal.rule.Rule;
import org.eclipse.tm4e.core.internal.rule.RuleId;

/**
 * Represents a "pushed" state on the stack (as a linked list element).
 *
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/grammar/grammar.ts#L592">
 *      github.com/microsoft/vscode-textmate/blob/main/src/grammar/grammar.ts</a>
 */
public final class StateStack implements IStateStack {

	@NonNullByDefault({}) // https://github.com/eclipse-jdt/eclipse.jdt.core/issues/233
	record Frame(
			RuleId ruleId,
			@Nullable Integer enterPos,
			@Nullable Integer anchorPos,
			boolean beginRuleCapturedEOL,
			@Nullable String endRule,
			List<AttributedScopeStack.Frame> nameScopesList,
			/** on top of nameScopesList */
			List<AttributedScopeStack.Frame> contentNameScopesList) {
	}

	public static final StateStack NULL = new StateStack(
			null,
			RuleId.NO_RULE,
			0,
			0,
			false,
			null,
			null,
			null);

	/**
	 * The position on the current line where this state was pushed.
	 * This is relevant only while tokenizing a line, to detect endless loops.
	 * Its value is meaningless across lines.
	 */
	private int _enterPos;

	/**
	 * The captured anchor position when this stack element was pushed.
	 * This is relevant only while tokenizing a line, to restore the anchor position when popping.
	 * Its value is meaningless across lines.
	 */
	private int _anchorPos;

	/**
	 * The depth of the stack.
	 */
	private final int depth;

	/**
	 * The previous state on the stack (or null for the root state).
	 */
	@Nullable
	private final StateStack parent;

	/**
	 * The state (rule) that this element represents.
	 */
	private final RuleId ruleId;

	/**
	 * The state has entered and captured \n. This means that the next line should have an anchorPosition of 0.
	 */
	final boolean beginRuleCapturedEOL;

	/**
	 * The "pop" (end) condition for this state in case that it was dynamically generated through captured text.
	 */
	@Nullable
	final String endRule;

	/**
	 * The list of scopes containing the "name" for this state.
	 */
	@Nullable
	final AttributedScopeStack nameScopesList;

	/**
	 * The list of scopes containing the "contentName" (besides "name") for this state.
	 * This list **must** contain as an element `scopeName`.
	 */
	@Nullable
	final AttributedScopeStack contentNameScopesList;

	StateStack(
			@Nullable final StateStack parent,
			final RuleId ruleId,
			final int enterPos,
			final int anchorPos,
			final boolean beginRuleCapturedEOL,
			@Nullable final String endRule,
			@Nullable final AttributedScopeStack nameScopesList,
			@Nullable final AttributedScopeStack contentNameScopesList) {

		this.parent = parent;
		this.ruleId = ruleId;
		depth = this.parent != null ? this.parent.depth + 1 : 1;
		_enterPos = enterPos;
		this._anchorPos = anchorPos;
		this.beginRuleCapturedEOL = beginRuleCapturedEOL;
		this.endRule = endRule;
		this.nameScopesList = nameScopesList;
		this.contentNameScopesList = contentNameScopesList;
	}

	@Override
	public boolean equals(@Nullable final Object other) {
		if (other instanceof final StateStack otherState) {
			return _equals(this, otherState);
		}
		return false;
	}

	private static boolean _equals(final StateStack a, final StateStack b) {
		if (a == b) {
			return true;
		}
		if (!_structuralEquals(a, b)) {
			return false;
		}
		return AttributedScopeStack.equals(a.contentNameScopesList, b.contentNameScopesList);
	}

	/**
	 * A structural equals check. Does not take into account `scopes`.
	 */
	private static boolean _structuralEquals(
			@Nullable StateStack a,
			@Nullable StateStack b) {
		do {
			if (a == b) {
				return true;
			}

			if (a == null && b == null) {
				// End of list reached for both
				return true;
			}

			if (a == null || b == null) {
				// End of list reached only for one
				return false;
			}

			if (a.depth != b.depth
					|| !Objects.equals(a.ruleId, b.ruleId)
					|| !Objects.equals(a.endRule, b.endRule)) {
				return false;
			}

			// Go to previous pair
			a = a.parent;
			b = b.parent;
		} while (true);
	}

	@Override
	public int getDepth() {
		return depth;
	}

	@Override
	public int hashCode() {
		int result = 31 + Objects.hashCode(contentNameScopesList);
		result = 31 * result + Objects.hashCode(endRule);
		result = 31 * result + Objects.hashCode(parent);
		result = 31 * result + Objects.hashCode(ruleId);
		return 31 * result + depth;
	}

	void reset() {
		StateStack el = this;
		while (el != null) {
			el._enterPos = -1;
			el._anchorPos = -1;
			el = el.parent;
		}
	}

	@Nullable
	StateStack pop() {
		return parent;
	}

	StateStack safePop() {
		if (parent != null)
			return parent;
		return this;
	}

	StateStack push(
			final RuleId ruleId,
			final int enterPos,
			final int anchorPos,
			final boolean beginRuleCapturedEOL,
			@Nullable final String endRule,
			@Nullable final AttributedScopeStack nameScopesList,
			@Nullable final AttributedScopeStack contentNameScopesList) {
		return new StateStack(
				this,
				ruleId,
				enterPos,
				anchorPos,
				beginRuleCapturedEOL,
				endRule,
				nameScopesList,
				contentNameScopesList);
	}

	int getEnterPos() {
		return _enterPos;
	}

	int getAnchorPos() {
		return _anchorPos;
	}

	Rule getRule(final IRuleRegistry grammar) {
		return grammar.getRule(ruleId);
	}

	@Override
	public String toString() {
		final var r = new ArrayList<String>();
		_writeString(r);
		return '[' + String.join(", ", r) + ']';
	}

	private void _writeString(final List<String> res) {
		if (parent != null) {
			parent._writeString(res);
		}
		res.add("(" + ruleId + ", " + this.nameScopesList + ", " + this.contentNameScopesList + ")");
	}

	StateStack withContentNameScopesList(final @Nullable AttributedScopeStack contentNameScopesList) {
		if (Objects.equals(this.contentNameScopesList, contentNameScopesList)) {
			return this;
		}
		return castNonNull(this.parent).push(this.ruleId,
				this._enterPos,
				this._anchorPos,
				this.beginRuleCapturedEOL,
				this.endRule,
				this.nameScopesList,
				contentNameScopesList);
	}

	StateStack withEndRule(final String endRule) {
		if (this.endRule != null && this.endRule.equals(endRule)) {
			return this;
		}
		return new StateStack(
				this.parent,
				this.ruleId,
				this._enterPos,
				this._anchorPos,
				this.beginRuleCapturedEOL,
				endRule,
				this.nameScopesList,
				this.contentNameScopesList);
	}

	/**
	 * Used to warn of endless loops
	 */
	boolean hasSameRuleAs(final StateStack other) {
		var el = this;
		while (el != null && el._enterPos == other._enterPos) {
			if (el.ruleId == other.ruleId) {
				return true;
			}
			el = el.parent;
		}
		return false;
	}

	Frame toStateStackFrame() {
		final var nameScopesList = this.nameScopesList;
		final var contentNameScopesList = this.contentNameScopesList;
		final var parent = this.parent;
		return new Frame(
				this.ruleId,
				null,
				null,
				this.beginRuleCapturedEOL,
				this.endRule,
				nameScopesList != null
						? nameScopesList.getExtensionIfDefined(parent != null ? parent.nameScopesList : null)
						: Collections.emptyList(),
				contentNameScopesList != null
						? contentNameScopesList.getExtensionIfDefined(this.nameScopesList)
						: Collections.emptyList());
	}

	public static StateStack pushFrame(@Nullable final StateStack self, final Frame frame) {
		final var namesScopeList = AttributedScopeStack.fromExtension(self == null ? null : self.nameScopesList,
				frame.nameScopesList);
		final var enterPos = frame.enterPos;
		final var anchorPos = frame.anchorPos;
		return new StateStack(
				self,
				frame.ruleId,
				enterPos == null ? -1 : enterPos,
				anchorPos == null ? -1 : anchorPos,
				frame.beginRuleCapturedEOL,
				frame.endRule,
				namesScopeList,
				AttributedScopeStack.fromExtension(namesScopeList, frame.contentNameScopesList));
	}
}
