# Session Changes Documentation - December 16, 2025

This document describes all changes made during this development session, including CI/CD fixes, feature implementation, and testing.

---

## Table of Contents
1. [CI Workflow Fixes](#ci-workflow-fixes)
2. [GitHub Secrets Configuration](#github-secrets-configuration)
3. [Telegram Notification Setup](#telegram-notification-setup)
4. [Undo/Redo Feature Implementation](#undoredo-feature-implementation)
5. [Code Cleanup](#code-cleanup)
6. [Testing](#testing)

---

## 1. CI Workflow Fixes

### Problem
The CI workflow (`android.yml`) was failing due to:
1. **ktfmt formatting violations** - Code files didn't pass formatting checks
2. **Missing signing configuration** - GitHub secrets weren't set up correctly

### Solution

#### Step 1: Fix ktfmt Formatting
```bash
# Run formatter to auto-fix all Kotlin files
./gradlew ktfmtFormat --no-daemon

# Verify formatting passes
./gradlew ktfmtCheck --no-daemon --quiet
```

#### Step 2: Commit Formatted Files
```bash
git add <files>
git commit -m "fix: Apply ktfmt formatting"
git push
```

### Key Learning
- **ktfmt** is a Kotlin code formatter that enforces consistent style
- The `ktfmt-check.yml` workflow runs on every push to `main` branch
- Always run `./gradlew ktfmtFormat` before committing Kotlin changes

---

## 2. GitHub Secrets Configuration

### Problem
Build failed with error:
```
SigningConfig "release" is missing required property "storePassword"
```

### Solution
The workflow decodes base64-encoded secrets to `/tmp/` for signing:

```yaml
# From android.yml
- name: Decode and create secrets
  run: |
    echo "${{ secrets.KEYSTORE }}" | base64 -d > /tmp/xed.keystore
    echo "${{ secrets.PROP }}" | base64 -d > /tmp/signing.properties
```

#### Setting Up Secrets (PowerShell)
```powershell
# Encode and set PROP secret (signing.properties)
$prop = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes("signing.properties"))
echo $prop | gh secret set PROP

# Encode and set KEYSTORE secret (keystore file)
$keystore = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes("release-keystore.jks"))
echo $keystore | gh secret set KEYSTORE
```

### Key Learning
- GitHub Actions can't access local files, so sensitive data must be stored as **Secrets**
- Secrets are stored as base64-encoded strings and decoded during workflow execution
- Use `gh secret set SECRET_NAME` to set secrets via GitHub CLI

---

## 3. Telegram Notification Setup

### Problem
APKs weren't being sent to Telegram because:
1. Old chat_id pointed to original repo owner's group
2. Condition `github.event.head_commit.message != ''` fails for manual triggers

### Solution
Updated `.github/workflows/android.yml`:

```yaml
# BEFORE (skipped for manual triggers)
- name: Send APK to Telegram
  if: ${{ success() && github.event.head_commit.message != '' }}
  run: |
    CAPTION=$(jq -R . <<< "${{ github.event.head_commit.message }}")
    curl -X POST ".../sendDocument" \
    -F chat_id="-1002408175863" \
    -F message_thread_id="582" \
    ...

# AFTER (always runs on success)
- name: Send APK to Telegram
  if: ${{ success() }}
  run: |
    CAPTION="${{ github.event.head_commit.message || format('{0} @ {1}', github.ref_name, env.COMMIT_HASH) }}"
    curl -X POST ".../sendDocument" \
    -F chat_id="590244502" \
    ...
```

### Key Changes
| Setting | Before | After |
|---------|--------|-------|
| Condition | `success() && message != ''` | `success()` |
| chat_id | `-1002408175863` (group) | `590244502` (your user ID) |
| message_thread_id | `582` | Removed (not needed for DMs) |
| Caption | Commit message only | Commit message OR `branch @ hash` |

### How to Get Your Telegram User ID
1. Message `@userinfobot` on Telegram
2. It will reply with your user ID

### Key Learning
- `workflow_dispatch` (manual triggers) don't have `head_commit.message`
- Use GitHub's `format()` function for fallback values
- Bot must have been messaged first before it can send to a user

---

## 4. Undo/Redo Feature Implementation

### Goal
Add undo/redo buttons to the Project Search & Replace dialog.

### Architecture Analysis
The backend already existed in `ProjectReplaceManager.kt`:
```kotlin
object ProjectReplaceManager {
    private val undoStack = ArrayDeque<ReplaceOperation>()
    private val redoStack = ArrayDeque<ReplaceOperation>()
    
    val canUndo = mutableStateOf(false)  // Observable state
    val canRedo = mutableStateOf(false)
    
    suspend fun undoLastReplace(): Boolean { ... }
    suspend fun redoLastReplace(): Boolean { ... }
}
```

### UI Implementation
Added to `ProjectSearchReplaceDialog.kt`:

```kotlin
// Bottom actions with Undo/Redo
Row(...) {
    // Undo button
    val canUndoState by ProjectReplaceManager.canUndo
    OutlinedButton(
        onClick = {
            scope.launch(Dispatchers.IO) {
                ProjectReplaceManager.undoLastReplace()
                // Refresh search results after undo
                searchResults.clear()
                if (searchQuery.isNotEmpty()) {
                    searchInProject(viewModel, scope, projectFile, searchQuery, options, searchResults)
                }
            }
        },
        enabled = canUndoState,  // Button enabled when undo available
    ) {
        Icon(painter = painterResource(drawables.undo), ...)
        Text("Undo")
    }
    
    // Similar for Redo button...
}
```

### Key Learning
- Use `by` delegation with Compose `mutableStateOf` for automatic recomposition
- Launch IO operations with `scope.launch(Dispatchers.IO) { }`
- Refresh UI state after operations complete

---

## 5. Code Cleanup

### Problem
Found obsolete code referencing non-existent `ProjectReplaceDialog`:

```kotlin
// GlobalActions.kt - ERROR: ProjectReplaceDialog doesn't exist!
if (projectReplaceDialog && currentTab is FileTreeTab) {
    ProjectReplaceDialog(...)  // Unresolved reference
}
```

### Solution
Removed obsolete code from two files:

#### GlobalActions.kt
```diff
- var projectReplaceDialog by mutableStateOf(false)
  var projectSearchReplaceDialog by mutableStateOf(false)

- if (projectReplaceDialog && currentTab is FileTreeTab) {
-     ProjectReplaceDialog(...)
- }
```

#### CommandProvider.kt
```diff
- import com.rk.components.projectReplaceDialog

- Command(
-     id = "project.replace_in_files",
-     action = { _, _ -> projectReplaceDialog = true },  // Removed
- ),
```

### Key Learning
- When refactoring, search entire codebase for references: `grep -r "functionName" src/`
- Compile errors often reveal leftover references
- Consolidate duplicate functionality into single components

---

## 6. Testing

### Unit Tests Created
`core/main/src/test/java/com/rk/searchreplace/ProjectReplaceManagerTest.kt`:

```kotlin
class ProjectReplaceManagerTest {
    @Test
    fun buildSearchRegex_defaultOptions_matchesCaseInsensitive() {
        val options = ProjectReplaceManager.SearchOptions()
        val regex = ProjectReplaceManager.buildSearchRegex("test", options)
        
        assertTrue(regex.containsMatchIn("test"))
        assertTrue(regex.containsMatchIn("TEST"))  // Case insensitive
    }
    
    @Test
    fun escapeReplacement_nonRegexMode_literalDollarSign() {
        val replacement = ProjectReplaceManager.escapeReplacement("price: $50", useRegex = false)
        val output = "item".replace(Regex("item"), replacement)
        assertEquals("price: \$50", output)  // $ is literal, not backreference
    }
}
```

### Adding Test Dependencies
`core/main/build.gradle.kts`:
```kotlin
dependencies {
    // ... other dependencies
    
    // Testing
    testImplementation("junit:junit:4.13.2")
}
```

### Running Tests
```bash
./gradlew :core:main:testDebugUnitTest --no-daemon
```

### Key Learning
- Pure functions (no Android dependencies) can be tested with JUnit
- Add `testImplementation` for test-only dependencies
- Test edge cases like regex special characters

---

## Command Reference

```bash
# Git operations
git checkout <branch>
git stash push -m "message"
git stash pop

# Gradle tasks
./gradlew ktfmtFormat    # Format Kotlin code
./gradlew ktfmtCheck     # Verify formatting
./gradlew assembleDebug  # Build debug APK
./gradlew :module:testDebugUnitTest  # Run unit tests

# GitHub CLI
gh secret set SECRET_NAME    # Set a repository secret
gh workflow run <workflow>   # Manually trigger workflow
gh run list                  # List recent workflow runs
gh run watch <id>            # Monitor a workflow run
```

---

## Summary of Files Changed

| File | Change |
|------|--------|
| `.github/workflows/android.yml` | Fixed Telegram notification |
| `core/main/build.gradle.kts` | Added JUnit test dependency |
| `ProjectSearchReplaceDialog.kt` | Added Undo/Redo buttons |
| `GlobalActions.kt` | Removed obsolete projectReplaceDialog |
| `CommandProvider.kt` | Removed obsolete command |
| `ProjectReplaceManagerTest.kt` | New unit tests |
