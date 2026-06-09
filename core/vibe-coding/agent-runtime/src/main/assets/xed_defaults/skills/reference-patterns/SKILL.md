---
name: reference-patterns
description: Reference implementations and patterns from opencode and Claude Code AI coding tools
---

# Reference Patterns

This skill provides access to reference AI coding tool implementations that can be studied for patterns and approaches.

## Available References

Reference implementations are available at the app's internal help_resources directory. These contain real-world patterns from production AI coding tools.

### opencode Patterns (available at help_resources/opencode-dev/)
- **Command definitions**: `.opencode/command/*.md` — markdown files with YAML frontmatter
- **Agent definitions**: `.opencode/agent/*.md` — sub-agents with model, color, tool restrictions
- **Skill definitions**: `.opencode/skills/*/SKILL.md` — reusable skill modules
- **Custom tools**: `.opencode/tool/*.ts` — TypeScript tool implementations
- **Configuration**: `.opencode/opencode.jsonc` — provider, permission, MCP, tool config
- **Project guidelines**: `AGENTS.md` — project-specific AI instructions with code style, testing, conventions

### Claude Code Patterns (available at help_resources/claude-code-main/)
- **Plugin structure**: `plugins/*/.claude-plugin/plugin.json` — plugin metadata format
- **Workflow commands**: `plugins/feature-dev/commands/feature-dev.md` — 7-phase guided workflow
- **Specialized agents**: `plugins/feature-dev/agents/*.md` — focused sub-agents with frontmatter
- **Code review workflow**: `plugins/code-review/` — parallel agent review with confidence scoring

## Usage

When implementing new features in the VibeCoding system:
1. Reference these patterns for command/agent file format
2. Follow the same frontmatter conventions for new commands and agents
3. Use the phased workflow approach for complex tasks
