const https = require("https");
const github = require("@actions/github");

const { GoogleGenAI } = require("@google/genai");

const ai = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY,
});

console.log("[start] Gemini issue title improver");

const token = process.env.GITHUB_TOKEN;
const octokit = github.getOctokit(token);

const geminiKey = process.env.GEMINI_API_KEY;
const [owner, repo] = process.env.REPOSITORY.split("/");

const issueNumberEnv = process.env.ISSUE_NUMBER;
const singleIssueMode = Boolean(issueNumberEnv);

// Track rate limit status
let rateLimitExceeded = false;

let stats = {
  total: 0,
  skipped_pr: 0,
  skipped_no_body: 0,
  skipped_short_body: 0,
  skipped_no_change: 0,
  skipped_already_improved: 0,
  skipped_rate_limit: 0,
  updated: 0,
  failed: 0,
};

/* ---------------- Helper Functions ---------------- */

// Check if title appears to be already improved by AI
function isAlreadyImproved(title, body) {
  // Markers of AI-improved titles
  const aiMarkers = [
    /:/,  // Contains colon (common in structured titles)
    /\b(add|implement|fix|support|feature|issue|error|crash|bug)\b/i,
  ];

  // Check for technical/structured format
  const isTechnical = aiMarkers.some(marker => marker.test(title));

  // Check title length and specificity
  const isDetailed = title.length > 40;

  // Check if title contains key terms from body
  const bodyWords = body.toLowerCase().split(/\s+/).slice(0, 50);
  const titleWords = title.toLowerCase().split(/\s+/);
  const hasRelevantTerms = titleWords.some(word =>
    word.length > 4 && bodyWords.includes(word)
  );

  return isTechnical && isDetailed && hasRelevantTerms;
}

/* ---------------- Gemini ---------------- */

async function callGemini(prompt) {
  // Don't make API calls if rate limit exceeded
  if (rateLimitExceeded) {
    console.log("[gemini] skipping call - rate limit exceeded");
    return null;
  }

  console.log("[gemini] sending request (SDK)");

  try {
    const response = await ai.models.generateContent({
      model: "gemini-2.5-flash-lite",
      contents: prompt,
    });

    const text = response.text?.trim();

    console.log("[gemini] response received");
    return text || null;
  } catch (err) {
    console.error("[gemini] SDK error:", err.message);

    // Check if error is rate limit related
    if (err.message && (
      err.message.includes("429") ||
      err.message.includes("quota") ||
      err.message.includes("RESOURCE_EXHAUSTED") ||
      err.message.includes("rate limit")
    )) {
      console.log("[gemini] RATE LIMIT EXCEEDED - stopping further API calls");
      rateLimitExceeded = true;
    }

    return null;
  }
}

/* ---------------- Title Improvement ---------------- */

async function improveIssue(issue) {
  stats.total++;

  if (issue.pull_request) {
    stats.skipped_pr++;
    console.log(`[skip] #${issue.number} is a PR`);
    return;
  }

  const currentTitle = issue.title || "";
  const body = issue.body || "";

  if (!body) {
    stats.skipped_no_body++;
    console.log(`[skip] #${issue.number} has no body`);
    return;
  }

  if (body.length < 50) {
    stats.skipped_short_body++;
    console.log(`[skip] #${issue.number} body too short (${body.length})`);
    return;
  }

  // Check if title is already improved
  if (isAlreadyImproved(currentTitle, body)) {
    stats.skipped_already_improved++;
    console.log(`[skip] #${issue.number} title appears already improved`);
    return;
  }

  // Check if rate limit exceeded before making API call
  if (rateLimitExceeded) {
    stats.skipped_rate_limit++;
    console.log(`[skip] #${issue.number} rate limit exceeded`);
    return;
  }

  console.log(`[process] #${issue.number}: "${currentTitle}"`);

  const prompt = `
You are improving GitHub issue titles for a developer.

Rules:
- Max 80 characters
- Be specific and technical
- Mention crash type, component, or error
- Do NOT add emojis
- Do NOT quote text
- Output ONLY the title

Current title:
"${currentTitle}"

Issue body:
${body}
`;

  const newTitle = await callGemini(prompt);

  if (!newTitle || newTitle === currentTitle) {
    stats.skipped_no_change++;
    console.log(`[skip] #${issue.number} Gemini returned no improvement`);
    return;
  }

  console.log(`[update] #${issue.number}`);
  console.log(`  old → ${currentTitle}`);
  console.log(`  new → ${newTitle}`);

  await octokit.rest.issues.update({
    owner,
    repo,
    issue_number: issue.number,
    title: newTitle,
  });

  stats.updated++;
}

/* ---------------- Main ---------------- */

(async () => {
  if (singleIssueMode) {
    console.log("[mode] single issue");

    await improveIssue({
      number: Number(issueNumberEnv),
      title: process.env.ISSUE_TITLE,
      body: process.env.ISSUE_BODY,
    });

    console.log("[done] single issue processed");
    console.log("[summary]");
    console.log(stats);
    return;
  }

  console.log("[mode] bulk (workflow_dispatch)");
  console.log(`[repo] ${owner}/${repo}`);

  const issues = await octokit.paginate(
    octokit.rest.issues.listForRepo,
    {
      owner,
      repo,
      state: "open",
      per_page: 100,
    }
  );

  console.log(`[fetch] fetched ${issues.length} issues`);

  issues.sort((a, b) => a.number - b.number);

  for (const issue of issues) {
    try {
      await improveIssue(issue);

      // Stop processing if rate limit exceeded
      if (rateLimitExceeded) {
        console.log("[stop] rate limit exceeded - stopping bulk processing");
        break;
      }
    } catch (e) {
      stats.failed++;
      console.error(`[error] #${issue.number}`, e.message);
    }
  }

  console.log("[summary]");
  console.log(stats);

  if (rateLimitExceeded) {
    console.log("\n⚠️  Rate limit reached. Remaining issues were not processed.");
    console.log("Please wait for quota reset or upgrade your API plan.");
  }
})();