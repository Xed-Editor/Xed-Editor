/**
 * Copyright (c) 2015-2017 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 * <p>
 * Initial code from https://github.com/microsoft/vscode-textmate/
 * Initial copyright Copyright (C) Microsoft Corporation. All rights reserved.
 * Initial license: MIT
 * <p>
 * Contributors:
 * - Microsoft Corporation: Initial code, written in TypeScript, licensed under MIT license
 * - Angelo Zerr <angelo.zerr@gmail.com> - translation and adaptation to Java
 */
package org.eclipse.tm4e.core.internal.rule;


import static org.eclipse.tm4e.core.internal.utils.NullSafetyHelper.*;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.grammar.dependencies.IncludeReference;
import org.eclipse.tm4e.core.internal.grammar.raw.IRawCaptures;
import org.eclipse.tm4e.core.internal.grammar.raw.IRawRepository;
import org.eclipse.tm4e.core.internal.grammar.raw.IRawRule;
import org.eclipse.tm4e.core.internal.grammar.raw.RawRule;

import io.github.rosemoe.sora.util.Logger;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/rule.ts#L381">
 *      github.com/microsoft/vscode-textmate/blob/main/src/rule.ts</a>
 */
public final class RuleFactory {

    private static final Logger LOGGER = Logger.instance(RuleFactory.class.getName());

    private static CaptureRule createCaptureRule(final IRuleFactoryHelper helper, @Nullable final String name,
                                                 @Nullable final String contentName, final RuleId retokenizeCapturedWithRuleId) {
        return helper.registerRule(id -> new CaptureRule(id, name, contentName, retokenizeCapturedWithRuleId));
    }

    public static RuleId getCompiledRuleId(final IRawRule desc, final IRuleFactoryHelper helper, final IRawRepository repository) {
        if (desc.getId() == null) {
            helper.registerRule(ruleId -> {
                desc.setId(ruleId);

                final var ruleMatch = desc.getMatch();
                if (ruleMatch != null) {
                    return new MatchRule(
                            ruleId,
                            desc.getName(),
                            ruleMatch,
                            _compileCaptures(desc.getCaptures(), helper, repository));
                }

                final var begin = desc.getBegin();
                if (begin == null) {
                    final var repository1 = desc.getRepository() == null
                            ? repository
                            : IRawRepository.merge(repository, desc.getRepository());
                    var patterns = desc.getPatterns();
                    if (patterns == null && desc.getInclude() != null) {
                        patterns = List.of(new RawRule().setInclude(desc.getInclude()));
                    }
                    return new IncludeOnlyRule(
                            ruleId,
                            desc.getName(),
                            desc.getContentName(),
                            _compilePatterns(patterns, helper, repository1));
                }

                final String ruleWhile = desc.getWhile();
                if (ruleWhile != null) {
                    return new BeginWhileRule(
                            ruleId,
                            desc.getName(),
                            desc.getContentName(),
                            begin, _compileCaptures(defaultIfNull(desc.getBeginCaptures(), desc.getCaptures()), helper,
                            repository),
                            ruleWhile, _compileCaptures(defaultIfNull(desc.getWhileCaptures(), desc.getCaptures()), helper,
                            repository),
                            _compilePatterns(desc.getPatterns(), helper, repository));
                }

                return new BeginEndRule(
                        ruleId,
                        desc.getName(),
                        desc.getContentName(),
                        begin, _compileCaptures(defaultIfNull(desc.getBeginCaptures(), desc.getCaptures()), helper, repository),
                        desc.getEnd(), _compileCaptures(defaultIfNull(desc.getEndCaptures(), desc.getCaptures()), helper, repository),
                        desc.isApplyEndPatternLast(),
                        _compilePatterns(desc.getPatterns(), helper, repository));
            });
        }
        return castNonNull(desc.getId());
    }

    private static List<@Nullable CaptureRule> _compileCaptures(@Nullable final IRawCaptures captures, final IRuleFactoryHelper helper,
                                                                final IRawRepository repository) {
        if (captures == null) {
            return Collections.emptyList();
        }

        // Find the maximum capture id
        int maximumCaptureId = 0;
        for (final String captureId : captures.getCaptureIds()) {
            final int numericCaptureId = parseInt(captureId, 10);
            if (numericCaptureId > maximumCaptureId) {
                maximumCaptureId = numericCaptureId;
            }
        }

        // Initialize result
        final var r = new ArrayList<@Nullable CaptureRule>(maximumCaptureId);
        for (int i = 0; i <= maximumCaptureId; i++) {
            r.add(null);
        }

        // Fill out result
        for (final String captureId : captures.getCaptureIds()) {
            final int numericCaptureId = parseInt(captureId, 10);
            final IRawRule rule = captures.getCapture(captureId);
            final RuleId retokenizeCapturedWithRuleId = rule.getPatterns() == null
                    ? RuleId.NO_RULE
                    : getCompiledRuleId(captures.getCapture(captureId), helper, repository);
            r.set(numericCaptureId, createCaptureRule(helper, rule.getName(), rule.getContentName(), retokenizeCapturedWithRuleId));
        }
        return r;
    }

    private static int parseInt(final String string, final int base) {
        try {
            return Integer.parseInt(string, base);
        } catch (final NumberFormatException ex) {
            return 0;
        }
    }

    private static CompilePatternsResult _compilePatterns(@Nullable final Collection<IRawRule> patterns, final IRuleFactoryHelper helper,
                                                          final IRawRepository repository) {
        if (patterns == null) {
            return new CompilePatternsResult(new RuleId[0], false);
        }

        final var r = new ArrayList<RuleId>();
        for (final IRawRule pattern : patterns) {
            RuleId ruleId = null;
            final var patternInclude = pattern.getInclude();
            if (patternInclude != null) {

                final var reference = IncludeReference.parseInclude(patternInclude);
                switch (reference.kind) {
                    case Base:
                        ruleId = getCompiledRuleId(repository.getBase(), helper, repository);
                        break;
                    case Self:
                        ruleId = getCompiledRuleId(repository.getSelf(), helper, repository);
                        break;

                    case RelativeReference:
                        // Local include found in `repository`
                        final var localIncludedRule = repository.getRule(reference.ruleName);
                        if (localIncludedRule != null) {
                            ruleId = getCompiledRuleId(localIncludedRule, helper, repository);
                        } else {
                            LOGGER.w("CANNOT find rule for scopeName [{0}]. I am [{1}]",
                                    patternInclude, repository.getBase().getName());
                        }
                        break;
                    case TopLevelReference, TopLevelRepositoryReference:

                        final var externalGrammarName = reference.scopeName;

                        // External include
                        final var externalGrammar = helper.getExternalGrammar(externalGrammarName, repository);

                        if (externalGrammar != null) {
                            final var externalGrammarRepo = externalGrammar.getRepository();
                            @Nullable final String externalGrammarInclude = reference.kind == IncludeReference.Kind.TopLevelRepositoryReference
                                    ? reference.ruleName
                                    : null;
                            if (externalGrammarInclude != null) {
                                final var externalIncludedRule = externalGrammarRepo.getRule(externalGrammarInclude);
                                if (externalIncludedRule != null) {
                                    ruleId = getCompiledRuleId(externalIncludedRule, helper, externalGrammarRepo);
                                } else {
                                    LOGGER.w("CANNOT find rule for scopeName [{0}]. I am [{1}]",
                                            patternInclude, repository.getBase().getName());
                                }
                            } else {
                                ruleId = getCompiledRuleId(externalGrammarRepo.getSelf(), helper, externalGrammarRepo);
                            }
                        } else {
                            LOGGER.w("CANNOT find grammar for scopeName [{0}]. I am [{1}]",
                                    patternInclude, repository.getBase().getName());
                        }
                        break;
                }
            } else {
                ruleId = getCompiledRuleId(pattern, helper, repository);
            }

            if (ruleId != null) {
                Rule rule;
                try {
                    rule = helper.getRule(ruleId);
                } catch (final IndexOutOfBoundsException ex) {
                    rule = null;
                    if (patternInclude != null) {
                        // TODO currently happens if an include rule references another not yet parsed rule
                    } else {
                        // should never happen
                        ex.printStackTrace();
                    }
                }
                boolean skipRule = false;

                if (rule instanceof final IncludeOnlyRule ior) {
                    if (ior.hasMissingPatterns && ior.patterns.length == 0) {
                        skipRule = true;
                    }
                } else if (rule instanceof final BeginEndRule ber) {
                    if (ber.hasMissingPatterns && ber.patterns.length == 0) {
                        skipRule = true;
                    }
                } else if (rule instanceof final BeginWhileRule bwr) {
                    if (bwr.hasMissingPatterns && bwr.patterns.length == 0) {
                        skipRule = true;
                    }
                }

                if (skipRule) {
                    LOGGER.w("REMOVING " + rule + " ENTIRELY DUE TO EMPTY PATTERNS THAT ARE MISSING");
                    continue;
                }

                r.add(ruleId);
            }
        }

        return new CompilePatternsResult(r.toArray(new RuleId[0]), patterns.size() != r.size());
    }

    private RuleFactory() {
    }
}
