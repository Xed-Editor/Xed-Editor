# Gemini Integration Phase 1 Checklist

This checklist is used as the baseline regression pass for Gemini CLI ↔ Xed bridge.

## A. Home open flow
- [ ] Open app at home (no file tab active)
- [ ] Tap AI button
- [ ] Gemini sheet appears and terminal session starts
- [ ] Home screen remains clickable when sheet is minimized
- [ ] Hide button closes sheet UI without killing session

## B. Editor open flow
- [ ] Open a project folder
- [ ] Open any file
- [ ] Open Gemini from editor action
- [ ] Existing session is reused when cwd is same/compatible
- [ ] Restart creates a fresh session

## C. Reopen same session
- [ ] Open Gemini sheet and send a prompt
- [ ] Hide sheet
- [ ] Open sheet again from same context
- [ ] Previous terminal history/session is still available

## D. WriteFile with emoji/newline
- [ ] Ask Gemini to update file containing emojis
- [ ] Apply through IDE bridge path
- [ ] Emojis remain intact (no replacement chars)
- [ ] Validate LF/CRLF no-op message is accurate

## E. openDiff / apply / reject
- [ ] Trigger openDiff
- [ ] Reject once and confirm no file write
- [ ] Apply once and confirm file + editor content updated
- [ ] Verify diffAccepted / diffRejected notifications in logs

## F. /ide enable + reconnect
- [ ] Run `/ide enable`
- [ ] Confirm bridge is connected
- [ ] Restart Gemini session and reconnect
- [ ] Ensure no stale/disconnected SSE issue

## Debug logs expected (DEBUG builds)
- `GeminiBridge`: bridge start/reuse, requests, tool calls
- `GeminiAssistantSheet`: start/reuse/stop from editor flow
- `HomeGeminiSheet`: start/reuse/stop from home flow

