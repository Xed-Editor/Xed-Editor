/**
 * Copyright (c) 2022 Sebastian Thomschke and others.
 *
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
 * - Sebastian Thomschke - translation and adaptation to Java
 */
package org.eclipse.tm4e.core.internal.grammar.dependencies;

import static org.eclipse.tm4e.core.internal.utils.NullSafetyHelper.castNonNull;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.TMException;
import org.eclipse.tm4e.core.internal.grammar.dependencies.AbsoluteRuleReference.TopLevelRepositoryRuleReference;
import org.eclipse.tm4e.core.internal.grammar.dependencies.AbsoluteRuleReference.TopLevelRuleReference;
import org.eclipse.tm4e.core.internal.grammar.raw.IRawGrammar;
import org.eclipse.tm4e.core.internal.grammar.raw.IRawRepository;
import org.eclipse.tm4e.core.internal.grammar.raw.IRawRule;
import org.eclipse.tm4e.core.internal.registry.IGrammarRepository;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/grammar/grammarDependencies.ts#L59">
 *      github.com/microsoft/vscode-textmate/blob/main/src/grammar/grammarDependencies.ts</a>
 */
public final class ScopeDependencyProcessor {

	private static class ExternalReferenceCollector {

		final Deque<AbsoluteRuleReference> references = new ArrayDeque<>();
		final Deque<String> seenReferenceKeys = new ArrayDeque<>();

		final Set<IRawRule> visitedRule = new HashSet<>();

		void add(final AbsoluteRuleReference reference) {
			final var key = reference.toKey();
			if (this.seenReferenceKeys.contains(key)) {
				return;
			}
			this.seenReferenceKeys.push(key);
			this.references.push(reference);
		}
	}

	public final Set<String /*ScopeName*/> seenFullScopeRequests = new HashSet<>();
	final Set<String> seenPartialScopeRequests = new HashSet<>();
	public Deque<AbsoluteRuleReference> Q = new ArrayDeque<>();

	public final IGrammarRepository repo;
	public final String initialScopeName;

	public ScopeDependencyProcessor(final IGrammarRepository repo, final String initialScopeName) {
		this.repo = repo;
		this.initialScopeName = initialScopeName;
		this.seenFullScopeRequests.add(initialScopeName);
		this.Q.add(new TopLevelRuleReference(initialScopeName));
	}

	public void processQueue() {
		final var q = Q;
		Q = new ArrayDeque<>();

		final var deps = new ExternalReferenceCollector();
		for (final var dep : q) {
			collectReferencesOfReference(dep, this.initialScopeName, this.repo, deps);
		}

		for (final var dep : deps.references) {
			if (dep instanceof TopLevelRuleReference) {
				if (this.seenFullScopeRequests.contains(dep.scopeName)) {
					// already processed
					continue;
				}
				this.seenFullScopeRequests.add(dep.scopeName);
				this.Q.push(dep);
			} else {
				if (this.seenFullScopeRequests.contains(dep.scopeName)) {
					// already processed in full
					continue;
				}
				if (this.seenPartialScopeRequests.contains(dep.toKey())) {
					// already processed
					continue;
				}
				this.seenPartialScopeRequests.add(dep.toKey());
				this.Q.push(dep);
			}
		}
	}

	private void collectReferencesOfReference(
			final AbsoluteRuleReference reference,
			final String baseGrammarScopeName,
			final IGrammarRepository repo,
			final ExternalReferenceCollector result) {

		final var selfGrammar = repo.lookup(reference.scopeName);
		if (selfGrammar == null) {
			if (reference.scopeName.equals(baseGrammarScopeName)) {
				throw new TMException("No grammar provided for <" + initialScopeName + ">");
			}
			return;
		}

		final var baseGrammar = castNonNull(repo.lookup(baseGrammarScopeName));

		if (reference instanceof TopLevelRuleReference) {
			collectExternalReferencesInTopLevelRule(new Context(baseGrammar, selfGrammar), result);
		} else if (reference instanceof final TopLevelRepositoryRuleReference ref) {
			collectExternalReferencesInTopLevelRepositoryRule(
					ref.ruleName,
					new ContextWithRepository(baseGrammar, selfGrammar, selfGrammar.getRepository()),
					result);
		}

		final var injections = repo.injections(reference.scopeName);
		if (injections != null) {
			for (final var injection : injections) {
				result.add(new TopLevelRuleReference(injection));
			}
		}
	}

	private static class Context {
		final IRawGrammar baseGrammar;
		final IRawGrammar selfGrammar;

		Context(final IRawGrammar baseGrammar, final IRawGrammar selfGrammar) {
			this.baseGrammar = baseGrammar;
			this.selfGrammar = selfGrammar;
		}
	}

	private static final class ContextWithRepository extends Context {
		@Nullable
		final IRawRepository repository;

		ContextWithRepository(final Context context, @Nullable final IRawRepository repository) {
			super(context.baseGrammar, context.selfGrammar);
			this.repository = repository;
		}

		ContextWithRepository(final IRawGrammar baseGrammar, final IRawGrammar selfGrammar, @Nullable final IRawRepository repository) {
			super(baseGrammar, selfGrammar);
			this.repository = repository;
		}
	}

	private void collectExternalReferencesInTopLevelRepositoryRule(final String ruleName, final ContextWithRepository context,
			final ExternalReferenceCollector result) {

		if (context.repository != null) {
			final var rule = context.repository.getRule(ruleName);
			if (rule != null) {
				collectExternalReferencesInRules(List.of(rule), context, result);
			}
		}
	}

	private void collectExternalReferencesInTopLevelRule(final Context context, final ExternalReferenceCollector result) {
		final var patterns = context.selfGrammar.getPatterns();
		if (patterns != null) {
			collectExternalReferencesInRules(patterns, new ContextWithRepository(context, context.selfGrammar.getRepository()), result);
		}
		final var injections = context.selfGrammar.getInjections();
		if (injections != null) {
			collectExternalReferencesInRules(
					injections.values(),
					new ContextWithRepository(context, context.selfGrammar.getRepository()),
					result);
		}
	}

	private void collectExternalReferencesInRules(
			final Collection<IRawRule> rules,
			final ContextWithRepository context,
			final ExternalReferenceCollector result) {

		for (final var rule : rules) {
			if (result.visitedRule.contains(rule)) {
				continue;
			}
			result.visitedRule.add(rule);

			final var patternRepository = rule.getRepository() == null
					? context.repository
					: IRawRepository.merge(context.repository, rule.getRepository());

			final var patternPatterns = rule.getPatterns();
			if (patternPatterns != null) {
				collectExternalReferencesInRules(patternPatterns, new ContextWithRepository(context, patternRepository), result);
			}

			final var include = rule.getInclude();
			if (include == null) {
				continue;
			}

			final var reference = IncludeReference.parseInclude(include);

			switch (reference.kind) {
				case Base:
					collectExternalReferencesInTopLevelRule(new Context(context.baseGrammar, context.baseGrammar), result);
					break;
				case Self:
					collectExternalReferencesInTopLevelRule(context, result);
					break;
				case RelativeReference:
					collectExternalReferencesInTopLevelRepositoryRule(reference.ruleName,
							new ContextWithRepository(context, patternRepository), result);
					break;
				case TopLevelReference:
				case TopLevelRepositoryReference:
					@Nullable
					final IRawGrammar selfGrammar = reference.scopeName.equals(context.selfGrammar.getScopeName())
							? context.selfGrammar
							: reference.scopeName.equals(context.baseGrammar.getScopeName())
									? context.baseGrammar
									: null;

					if (selfGrammar != null) {
						final var newContext = new ContextWithRepository(context.baseGrammar, selfGrammar, patternRepository);
						if (reference.kind == IncludeReference.Kind.TopLevelRepositoryReference) {
							collectExternalReferencesInTopLevelRepositoryRule(reference.ruleName, newContext, result);
						} else {
							collectExternalReferencesInTopLevelRule(newContext, result);
						}
					} else {
						if (reference.kind == IncludeReference.Kind.TopLevelRepositoryReference) {
							result.add(new TopLevelRepositoryRuleReference(reference.scopeName, reference.ruleName));
						} else {
							result.add(new TopLevelRuleReference(reference.scopeName));
						}
					}
					break;
			}
		}
	}
}
