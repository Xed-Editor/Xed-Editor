package com.rk.ai.agent.prompts

internal val DEFAULT_SUGGESTION_PROMPT = """
    I will provide you with some chat content in the `<content>` block, including conversations between the User and the AI assistant.
    You need to act as the **User** to reply to the assistant, generating 3~5 appropriate and contextually relevant responses to the assistant.

    Rules:
    1. Reply directly with suggestions, do not add any formatting, and separate suggestions with newlines, no need to add markdown list formats.
    2. Use {locale} language.
    3. Ensure each suggestion is valid.
    4. **Do not limit the length of each suggestion** – generate full useful suggestions.
    5. Imitate the user's previous conversational style.
    6. Act as a User, not an Assistant!

    Output must be a JSON array of objects with the following fields:
    - "text": the suggestion string
    - "confidence": optional float confidence (0‑1)
    - "source": optional short identifier (e.g., "diagnostic", "user", "model")
    - "diagnostic": optional object with keys "file", "line", "message" if the suggestion comes from a diagnostic

    <content>
    {content}
    </content>
""".trimIndent()
