// .github/scripts/close-duplicates.mjs
import crypto from "crypto";
import { getOctokit } from "@actions/github";

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

const DRY_RUN = process.env.DRY_RUN === "true";

/**
 * Package prefixes that are pure noise — Android OS, JVM, and Kotlin runtime
 * internals that appear in every crash regardless of the actual bug.
 * Frames matching any of these are excluded from the fingerprint.
 */
const FRAMEWORK_FRAME_PREFIXES = [
  // Android OS
  "android.",
  "com.android.",
  "dalvik.",
  // Java standard library
  "java.",
  "javax.",
  "sun.",
  // Kotlin runtime & coroutines
  "kotlin.",
  "kotlinx.",
];

// ---------------------------------------------------------------------------
// Startup validation
// ---------------------------------------------------------------------------

const token = process.env.GITHUB_TOKEN;
if (!token) {
  console.error("❌  GITHUB_TOKEN is not set.");
  process.exit(1);
}

const octokit = getOctokit(token);
const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? "").split("/");
if (!owner || !repo) {
  console.error("❌  GITHUB_REPOSITORY is not set or malformed.");
  process.exit(1);
}

// ---------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------

/**
 * Extract the raw stacktrace string that follows "Error StackTrace :" in the
 * issue body. Returns null if the marker isn't found.
 */
function extractRawStacktrace(body) {
  if (typeof body !== "string") return null;
  const marker = "Error StackTrace :";
  const idx = body.indexOf(marker);
  return idx === -1 ? null : body.substring(idx + marker.length).trim();
}

/**
 * Parse the raw stacktrace text into a structured object:
 *
 *   {
 *     exceptionType: "java.lang.IllegalThreadStateException",
 *     frames: [
 *       { className: "io.github.rosemoe…", method: "rerun" },
 *       …
 *     ],
 *   }
 *
 * Line numbers and file names are intentionally discarded — they change with
 * every build and must not affect duplicate detection.
 */
function parseStacktrace(raw) {
  const lines = raw.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  if (lines.length === 0) return null;

  // First non-empty line is the exception type (message after ":" is ignored)
  const exceptionType = lines[0].split(":")[0].trim();
  if (!exceptionType.includes(".")) return null; // must be a qualified class name

  const frames = [];

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];

    // Skip suppressed blocks entirely — they're coroutine context noise
    if (line.startsWith("Suppressed:") || line.startsWith("...")) continue;
    if (!line.startsWith("at ")) continue;

    // "at com.rk.editor.Editor$updateColors$1$1.invokeSuspend(Editor.kt:164)"
    //  → classAndMethod = "com.rk.editor.Editor$updateColors$1$1.invokeSuspend"
    const classAndMethod = line.replace(/^at /, "").split("(")[0];
    const lastDot = classAndMethod.lastIndexOf(".");
    const className = lastDot === -1 ? classAndMethod : classAndMethod.substring(0, lastDot);
    const method = lastDot === -1 ? "" : classAndMethod.substring(lastDot + 1);

    frames.push({ className, method });
  }

  return frames.length === 0 ? null : { exceptionType, frames };
}

// ---------------------------------------------------------------------------
// Fingerprinting
// ---------------------------------------------------------------------------

/**
 * Build a stable, build-agnostic fingerprint for a parsed stacktrace.
 *
 * Strategy
 * ─────────
 * 1. Always include the exception type (e.g. IllegalThreadStateException).
 * 2. Keep only frames whose class belongs to app / library code
 *    (matched against APP_FRAME_PREFIXES). Android OS / JVM / Kotlin runtime
 *    frames are discarded — they are present in every crash and carry no
 *    signal about *which* bug this is.
 * 3. If no app frames exist (shouldn't happen, but just in case) fall back to
 *    the top 5 frames so we always produce a non-trivial fingerprint.
 *
 * Result is a plain string; its SHA-256 hash is used as the map key.
 */
function buildFingerprint(parsed) {
  const appFrames = parsed.frames.filter((f) =>
    !FRAMEWORK_FRAME_PREFIXES.some((prefix) => f.className.startsWith(prefix))
  );

  // Fallback: if every frame is a framework frame (e.g. a raw java.io.IOException
  // thrown deep inside the JVM with no app code in the stack), use only the
  // top frame as the throw site. It's the most specific signal available and
  // avoids false-positive matches between unrelated crashes that happen to
  // share the same generic framework frames.
  const relevantFrames = appFrames.length > 0 ? appFrames : parsed.frames.slice(0, 1);

  const frameStr = relevantFrames
    .map((f) => `${f.className}.${f.method}`)
    .join("\n");

  return `${parsed.exceptionType}\n${frameStr}`;
}

function sha256(text) {
  return crypto.createHash("sha256").update(text).digest("hex");
}

// ---------------------------------------------------------------------------
// GitHub helpers
// ---------------------------------------------------------------------------

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Retry with exponential back-off; respects GitHub's Retry-After header. */
async function withRetry(fn, retries = 4) {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      return await fn();
    } catch (err) {
      const retryable = err.status === 429 || err.status === 403 || err.status >= 500;
      if (retryable && attempt < retries) {
        const after = parseInt(err.response?.headers?.["retry-after"] ?? "0", 10);
        const wait = after > 0 ? after * 1000 : 2 ** attempt * 1500;
        console.warn(`  [retry] HTTP ${err.status} — waiting ${wait}ms before attempt ${attempt + 1}…`);
        await sleep(wait);
      } else {
        throw err;
      }
    }
  }
}

/**
 * Close the issue as a duplicate of another issue.
 * Posting "Duplicate of #X" as the closing comment makes GitHub natively
 * recognize and display it as "closed as duplicate of #X" in the UI —
 * identical to manually selecting that option.
 */
async function closeAsDuplicate(issue_number, original_number) {
  if (DRY_RUN) {
    console.log(`  [dry-run] Would close #${issue_number} as duplicate of #${original_number}`);
    return;
  }

  // Posting this comment is what triggers GitHub's native duplicate UI.
  // GitHub will automatically close the issue and link it to the original.
  await withRetry(() =>
    octokit.rest.issues.createComment({
      owner, repo,
      issue_number,
      body: `Duplicate of #${original_number}`,
    })
  );
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

(async () => {
  try {
    if (DRY_RUN) console.log("🔍  DRY RUN — no changes will be made.\n");

    const allIssues = await octokit.paginate(
      octokit.rest.issues.listForRepo,
      { owner, repo, state: "open", per_page: 100 }
    );

    // Process oldest-first so the first occurrence is always the one kept open
    const issues = allIssues
      .filter((i) => !i.pull_request)
      .sort((a, b) => a.number - b.number);

    console.log(`Found ${issues.length} open (non-PR) issues.\n`);

    const seen = new Map(); // fingerprint hash → oldest issue number
    let closed = 0;
    let noTrace = 0;

    for (const issue of issues) {
      const raw = extractRawStacktrace(issue.body);
      if (!raw) { noTrace++; continue; }

      const parsed = parseStacktrace(raw);
      if (!parsed) { noTrace++; continue; }

      const fingerprint = buildFingerprint(parsed);
      const h = sha256(fingerprint);

      if (seen.has(h)) {
        const original = seen.get(h);
        console.log(`#${issue.number} → duplicate of #${original}`);
        console.log(`  exception        : ${parsed.exceptionType}`);
        console.log(`  fingerprint hash : ${h.substring(0, 12)}…\n`);
        await closeAsDuplicate(issue.number, original);
        closed++;
      } else {
        seen.set(h, issue.number);
      }
    }

    console.log("─".repeat(60));
    console.log(`✅  Unique crashes  : ${seen.size}`);
    console.log(`🔁  Closed as dupes : ${closed}`);
    console.log(`⏭️  No stacktrace   : ${noTrace}`);
  } catch (err) {
    console.error("Fatal:", err.message);
    process.exit(1);
  }
})();