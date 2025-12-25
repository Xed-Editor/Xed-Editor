// .github/scripts/close-duplicates.js
const crypto = require("crypto");
const github = require("@actions/github");

const token = process.env.GITHUB_TOKEN;
const octokit = github.getOctokit(token);

const [owner, repo] = process.env.GITHUB_REPOSITORY.split("/");

function extractStacktrace(body = "") {
  const marker = "Error StackTrace :";
  const idx = body.indexOf(marker);
  if (idx === -1) return null;

  let trace = body.substring(idx + marker.length);

  // Normalize stacktrace
  trace = trace
    .replace(/:\d+\)/g, ")")        // remove line numbers
    .replace(/\s+/g, " ")           // normalize whitespace
    .trim();

  return trace.length > 50 ? trace : null;
}

function hash(text) {
  return crypto.createHash("sha256").update(text).digest("hex");
}

(async () => {
  const issues = await octokit.paginate(
    octokit.rest.issues.listForRepo,
    {
      owner,
      repo,
      state: "open",
      per_page: 100,
    }
  );

  const seen = new Map(); // hash → canonical issue number

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

    // Optional but recommended: explain why it was closed
    await octokit.rest.issues.createComment({
      owner,
      repo,
      issue_number: issue.number,
      body: `🔁 **Duplicate issue**\n\nThis crash has the same stacktrace as #${original}.`,
    });

    // Native "Close as duplicate"
    await octokit.rest.issues.update({
      owner,
      repo,
      issue_number: issue.number,
      state: "closed",
      state_reason: "duplicate",
    });
  }
})();
