const https = require("https");
const github = require("@actions/github");

const token = process.env.GITHUB_TOKEN;
const octokit = github.getOctokit(token);

const geminiKey = process.env.GEMINI_API_KEY;
const [owner, repo] = process.env.REPOSITORY.split("/");

const issueNumberEnv = process.env.ISSUE_NUMBER;
const singleIssueMode = Boolean(issueNumberEnv);

/* ---------------- Gemini ---------------- */

function callGemini(prompt) {
  const data = JSON.stringify({
    contents: [{ parts: [{ text: prompt }] }],
  });

  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        hostname: "generativelanguage.googleapis.com",
        path: `/v1beta/models/gemini-1.5-flash:generateContent?key=${geminiKey}`,
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(data),
        },
      },
      (res) => {
        let body = "";
        res.on("data", (d) => (body += d));
        res.on("end", () => {
          try {
            const json = JSON.parse(body);
            resolve(
              json.candidates?.[0]?.content?.parts?.[0]?.text?.trim()
            );
          } catch (e) {
            reject(e);
          }
        });
      }
    );

    req.on("error", reject);
    req.write(data);
    req.end();
  });
}

/* ---------------- Title Improvement ---------------- */

async function improveIssue(issue) {
  if (issue.pull_request) return;

  const currentTitle = issue.title || "";
  const body = issue.body || "";

  if (!body || body.length < 50) return;

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

  if (!newTitle || newTitle === currentTitle) return;

  console.log(`✔ #${issue.number}: "${currentTitle}" → "${newTitle}"`);

  await octokit.rest.issues.update({
    owner,
    repo,
    issue_number: issue.number,
    title: newTitle,
  });
}

/* ---------------- Main ---------------- */

(async () => {
  if (singleIssueMode) {
    console.log("Running in single-issue mode");

    await improveIssue({
      number: Number(issueNumberEnv),
      title: process.env.ISSUE_TITLE,
      body: process.env.ISSUE_BODY,
    });

    return;
  }

  console.log("Running in bulk mode (workflow_dispatch)");

  const issues = await octokit.paginate(
    octokit.rest.issues.listForRepo,
    {
      owner,
      repo,
      state: "open",
      per_page: 100,
    }
  );

  // Oldest first = more stable results
  issues.sort((a, b) => a.number - b.number);

  for (const issue of issues) {
    try {
      await improveIssue(issue);
    } catch (e) {
      console.error(`Failed on #${issue.number}`, e.message);
    }
  }
})();
