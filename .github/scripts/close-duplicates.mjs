// .github/scripts/close-duplicates.mjs
import crypto from "crypto";
import github from "@actions/github";

const token = process.env.GITHUB_TOKEN;
// Note: In ESM, we use the default export or specific named ones
const octokit = github.getOctokit(token);

const [owner, repo] = process.env.GITHUB_REPOSITORY.split("/");

function extractStacktrace(body) {
  if (typeof body !== "string") return null;
  const marker = "Error StackTrace :";
  const idx = body.indexOf(marker);
  if (idx === -1) return null;

  let trace = body.substring(idx + marker.length);
  trace = trace
    .replace(/:\d+\)/g, ")")   // remove line numbers
    .replace(/\s+/g, " ")
    .trim();
  return trace.length > 50 ? trace : null;
}

function hash(text) {
  return crypto.createHash("sha256").update(text).digest("hex");
}

(async () => {
  try {
    const issues = await octokit.paginate(
      octokit.rest.issues.listForRepo,
      {
        owner,
        repo,
        state: "open",
        per_page: 100,
      }
    );

    issues.sort((a, b) => a.number - b.number);
    const seen = new Map();

    for (const issue of issues) {
      if (issue.pull_request) continue;

      const stacktrace = extractStacktrace(issue.body);
      if (!stacktrace) continue;

      const h = hash(stacktrace);

      if (!seen.has(h)) {
        seen.set(h, issue.number);
        continue;
      }

      const original = seen.get(h);
      console.log(`Closing duplicate #${issue.number} → #${original}`);

      await octokit.rest.issues.createComment({
        owner,
        repo,
        issue_number: issue.number,
        body: `🔁 **Duplicate issue**\n\nThis crash has the same stacktrace as #${original}.`,
      });

      await octokit.rest.issues.update({
        owner,
        repo,
        issue_number: issue.number,
        state: "closed",
        state_reason: "duplicate",
      });
    }
  } catch (error) {
    console.error("Error processing issues:", error.message);
    process.exit(1);
  }
})();
