const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");
const { GoogleGenerativeAI } = require("@google/generative-ai");

const apiKey = process.env.GEMINI_API_KEY;
if (!apiKey) {
  console.error("Missing GEMINI_API_KEY environment variable.");
  process.exit(1);
}

const genAI = new GoogleGenerativeAI(apiKey);

// Helper to safely run git commands and get output string
function runGit(command) {
  try {
    return execSync(command, { encoding: "utf8" }).trim();
  } catch (error) {
    return "";
  }
}

async function main() {
  const eventName = process.env.GITHUB_EVENT_NAME;
  let baseDiffTarget = "";

  if (eventName === "pull_request") {
    const baseRef = process.env.GITHUB_BASE_REF;
    runGit(`git fetch origin ${baseRef}`);
    baseDiffTarget = `origin/${baseRef}...HEAD`;
  } else {
    let beforeSha = process.env.GITHUB_BEFORE_SHA;
    const currentSha = process.env.GITHUB_CURRENT_SHA;
    
    if (!beforeSha || beforeSha === "0000000000000000000000000000000000000000") {
      beforeSha = "HEAD~1";
    }
    baseDiffTarget = `${beforeSha} ${currentSha}`;
  }

  // 1. Get list of changed files ending with strings.xml
  const rawFiles = runGit(`git diff --name-only ${baseDiffTarget}`);
  if (!rawFiles) {
    console.log("No file changes detected.");
    return;
  }

  const files = rawFiles.split("\n").filter(file => file.endsWith("strings.xml"));

  if (files.length === 0) {
    console.log("No strings.xml changes detected.");
    return;
  }

  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  let workflowFailed = false;

  // 2. Loop through each modified strings.xml file and scan its diff
  for (const file of files) {
    const diff = runGit(`git diff ${baseDiffTarget} -- "${file}"`);
    if (!diff.trim()) continue;

    const prompt = [
      "You are a translation moderation system.",
      "",
      "Analyze this Android strings.xml git diff.",
      "",
      "Check ONLY newly added or modified translations.",
      "",
      "Detect:",
      "- profanity",
      "- slurs",
      "- hate speech",
      "- sexual content",
      "- abusive language",
      "- offensive slang",
      "",
      "Ignore:",
      "- XML syntax",
      "- placeholders like %s or %d",
      "- harmless casual language",
      "",
      "Reply ONLY with:",
      "SAFE",
      "",
      "or",
      "",
      "UNSAFE: <reason>",
      "",
      "Git diff:",
      diff
    ].join("\n");

    try {
      const result = await model.generateContent(prompt);
      const text = result.response.text().trim();

      console.log(`\n=== Scanning: ${file} ===`);
      console.log(text);

      if (!text.startsWith("SAFE")) {
        workflowFailed = true;
      }
    } catch (err) {
      console.error(`Error scanning ${file}:`, err.message);
      workflowFailed = true;
    }
  }

  if (workflowFailed) {
    console.error("\nTranslation moderation failed. Unsafe content detected.");
    process.exit(1);
  }

  console.log("\nAll translations are safe.");
}

main().catch((err) => {
  console.error("Execution error:", err);
  process.exit(1);
});
