# MCP Server Bug & Issue Report

**Date:** 2026-06-23  
**Environment:** opencode CLI on Linux  

---

## Summary

| Server | Status | HTTP Code | Issue |
|--------|--------|-----------|-------|
| stitch | FAILED | 405 | Method Not Allowed |
| xed-ide | FAILED | 401 | Unauthorized |

---

## Server 1: stitch

**URL:** `https://stitch.googleapis.com/mcp`  
**Auth:** X-Goog-Api-Key header  

### Test Result
```
StatusCode: 405 Method Not Allowed (GET)
```

### Issues Found

1. **HTTP 405 - Method Not Allowed**
   - Server rejects GET requests
   - MCP protocol requires POST, but opencode may be using GET for health check
   - Server exists but endpoint behavior is non-standard

2. **Connection Timeout**
   - Earlier attempts: `Socket connection closed unexpectedly`
   - Socket drops before response received

3. **Auth Inconsistency**
   - Config changed from `Auth: configured` to `Auth: none` between checks
   - API key may not be valid or properly formatted

### Root Cause
Google's stitch MCP endpoint may not be publicly accessible or requires specific auth flow not supported by opencode's MCP client.

---

## Server 2: xed-ide

**URL:** `http://127.0.0.1:42175/mcp`  
**Auth:** Bearer token  

### Test Result
```
StatusCode: 401 Unauthorized (GET)
```

### Issues Found

1. **HTTP 401 - Unauthorized**
   - Server is running and responding
   - Bearer token is invalid or expired
   - Token: `ca8921bef8327c9375735c715022f88a1f4fe7d6a5c0b0b7`

2. **Local Server Running But Inaccessible**
   - Port 42175 is active
   - Auth middleware rejecting requests

### Root Cause
The Xed-IDE MCP server's auth token has likely expired or was regenerated without updating the opencode config.

---

## Recommendations

### For stitch
- Verify the correct endpoint URL
- Check if Google requires OAuth instead of API key
- Test with `curl -X POST` to confirm MCP protocol works
- Consider removing if not needed

### For xed-ide
- Regenerate the Bearer token in Xed-IDE settings
- Update `~/.config/opencode/opencode.json` with new token
- Restart opencode after updating

---

## Config Reference

**File:** `~/.config/opencode/opencode.json`

```json
{
  "mcp": {
    "stitch": {
      "type": "remote",
      "url": "https://stitch.googleapis.com/mcp",
      "headers": { "X-Goog-Api-Key": "..." }
    },
    "xed-ide": {
      "type": "remote",
      "url": "http://127.0.0.1:42175/mcp",
      "headers": { "Authorization": "Bearer ..." }
    }
  }
}
```

---

## Status: ALL MCP SERVERS NON-FUNCTIONAL

No MCP tools are currently available for use.
