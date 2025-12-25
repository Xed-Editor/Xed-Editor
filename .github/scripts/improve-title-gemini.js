const https = require("https");
const github = require("@actions/github");

const token = process.env.GITHUB_TOKEN;
const octokit = github.getOctokit(token);

const geminiKey = process.env.GEMINI_API_KEY;
const [owner, repo] = process.env.REPOSITORY.split("/");

const issueNumber = Number(process.env.ISSUE_NUMBER);
const currentTitle = process.env.ISSUE_TITLE || "";
const body = process.env.ISSUE_BODY || "";


if (!body || body.length < 50) {
  console.log("Not enough body content to improve title");
  process.exit(0);
}

function callGemini(prompt) {
  const data = JSON.stringify({
    contents: [
      {
        parts: [{ text: prompt }],
      },
    ],
  });

  const options = {
    hostname: "generativelanguage.googleapis.com",
    path: `/v1beta/models/gemini-1.5-flash:generateContent?key=${geminiKey}`,
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Content-Length": Buffer.byteLength(data),
    },
  };

  return new Promise((resolve, reject) => {
    const req = https.request(options, (res) => {
      let body = "";
      res.on("data", (d) => (body += d));
      res.on("end", () => {
        try {
          const json = JSON.parse(body);
          const text =
            json.candidates?.[0]?.content?.parts?.[0]?.text;
          resolve(text?.trim());
        } catch (e) {
          reject(e);
        }
      });
    });

    req.on("error", reject);
    req.write(data);
    req.end();
  });
}

(async () => {
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
    console.log("No better title generated");
    return;
  }

  console.log(`Updating title → ${newTitle}`);

  await octokit.rest.issues.update({
    owner,
    repo,
    issue_number: issueNumber,
    title: newTitle,
  });
})();
