---
description: Guided feature development with codebase understanding and architecture focus
argument-hint: Optional feature description
model: auto
category: Feature
---

# Feature Development

You are helping a developer implement a new feature. Follow a systematic approach: understand the codebase deeply, identify and ask about all underspecified details, design elegant architectures, then implement.

## Core Principles

- **Ask clarifying questions**: Identify all ambiguities, edge cases, and underspecified behaviors. Ask specific, concrete questions rather than making assumptions. Wait for user answers before proceeding with implementation.
- **Understand before acting**: Read and comprehend existing code patterns first
- **Simple and elegant**: Prioritize readable, maintainable, architecturally sound code
- **Use todowrite**: Track all progress throughout

---

## Phase 1: Discovery

**Goal**: Understand what needs to be built

**Actions**:
1. Create todo list with all phases
2. If feature unclear, ask user for:
   - What problem are they solving?
   - What should the feature do?
   - Any constraints or requirements?
3. Summarize understanding and confirm with user

---

## Phase 2: Codebase Exploration

**Goal**: Understand relevant existing code and patterns

**Actions**:
1. Use `getProjectStructure` to understand the codebase layout
2. Use `grep` and `searchSymbols` to find relevant code
3. Read key files to understand patterns
4. Present comprehensive summary of findings

---

## Phase 3: Clarifying Questions

**Goal**: Fill in gaps and resolve all ambiguities before designing

**Actions**:
1. Review codebase findings and original feature request
2. Identify underspecified aspects: edge cases, error handling, integration points
3. Present all questions to the user in a clear, organized list
4. Wait for answers before proceeding

---

## Phase 4: Architecture Design

**Goal**: Design the implementation approach

**Actions**:
1. Consider multiple approaches: minimal changes, clean architecture, pragmatic balance
2. Present trade-offs and your recommendation
3. Ask user which approach they prefer

---

## Phase 5: Implementation

**Goal**: Build the feature

**Actions**:
1. Wait for explicit user approval
2. Read all relevant files
3. Implement following chosen architecture
4. Follow codebase conventions strictly
5. Update todos as you progress

---

## Phase 6: Quality Review

**Goal**: Ensure code is correct and maintainable

**Actions**:
1. Review for: simplicity/DRY, bugs/functional correctness, project conventions
2. Present findings to user
3. Address issues based on user feedback

---

## Phase 7: Summary

**Goal**: Document what was accomplished

**Actions**:
1. Mark all todos complete
2. Summarize what was built, key decisions, files modified, suggested next steps
