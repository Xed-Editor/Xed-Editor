# Xed-Editor AI Agent Instructions

Welcome to the Xed-Editor codebase. To work effectively and efficiently in this environment, you MUST follow these high-performance tooling patterns.

## 🚀 High-Efficiency Workflow

### 1. Initial Orientation
**NEVER** start by walking the entire file tree with `listFiles` or `getProjectStructure`. 
- **DO**: Use `getProjectSummary` as your very first tool call. It provides the project architecture, core configuration files, and git status in a single turn.

### 2. Context Gathering
Instead of reading files one by one:
- **DO**: Use `readFiles` to ingest multiple related files (e.g., an interface and its implementation) in a single turn.
- **DO**: Use `searchSymbols` instead of `searchCode` when looking for definitions of classes or functions. It is much more precise.

### 3. Applying Changes
- **DO**: Use `applyBatchEdits` for any change that affects more than one file. This ensures atomic updates and significantly reduces user review friction.
- **DO**: Rely on **Proactive Diagnostics**. After you write a file, the IDE will automatically send you a notification (`ide/diagnosticsUpdated`) if your change introduced any errors. You do not need to call `getDiagnostics` manually after every write.

### 4. Handling Errors
If a file path is not found, pay attention to the error message. The IDE provides **"Did you mean?"** suggestions to help you correct typos in one turn.

## 🛠 Tooling Principles
- **Minimize Turns**: Maximize the information gathered and actions taken in every turn.
- **Stay Semantic**: Prefer LSP-backed tools (`findDefinitions`, `searchSymbols`) over raw text tools (`runCommand`, `searchCode`).
- **Trust the IDE**: The IDE's native tools are faster and more reliable than running shell commands via the terminal for standard development tasks.
