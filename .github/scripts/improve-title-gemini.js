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

let stats = {
  total: 0,
  skipped_pr: 0,
  skipped_no_body: 0,
  skipped_short_body: 0,
  skipped_no_change: 0,
  updated: 0,
  failed: 0,
};

/* ---------------- Gemini ---------------- */

async function callGemini(prompt) {
  console.log("[gemini] sending request (SDK)");

  try {
    const response = await ai.models.generateContent({
      model: "gemini-1.5-pro", // or gemini-1.5-pro / flash
      contents: prompt,
    });

    const text = response.text?.trim();

    console.log("[gemini] response received");
    return text || null;
  } catch (err) {
    console.error("[gemini] SDK error:", err.message);
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
    return;
  }

  console.log("[mode] bulk (workflow_dispatch)");
  console.log(`[repo] ${owner}/${repo}`);

  const issues = await octokit.paginate(
    octokit.rest.issues.listForRepo,
    {
      owner,
      repo,
      state: "open", // IMPORTANT for manual runs
      per_page: 100,
    }
  );

  console.log(`[fetch] fetched ${issues.length} issues`);

  issues.sort((a, b) => a.number - b.number);

  for (const issue of issues) {
    try {
      await improveIssue(issue);
    } catch (e) {
      stats.failed++;
      console.error(`[error] #${issue.number}`, e.message);
    }
  }

  console.log("[summary]");
  console.log(stats);
})();
