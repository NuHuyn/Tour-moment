/**
 * Thin wrapper around the DeepSeek chat-completions endpoint (OpenAI-compatible shape).
 * Uses Node 20's built-in global fetch - no extra HTTP dependency needed.
 *
 * Model choice: deepseek-v4-flash, not deepseek-v4-pro - see feasibility_report.md §2/§3.
 * The legacy "deepseek-chat"/"deepseek-reasoner" aliases are retired (July 24, 2026), so the
 * model name is always passed explicitly.
 */

const DEEPSEEK_BASE_URL = process.env.DEEPSEEK_BASE_URL || "https://api.deepseek.com";
const DEEPSEEK_MODEL = process.env.DEEPSEEK_MODEL || "deepseek-v4-flash";

// Basic cost/latency ceiling on every call - keeps a single chatbot turn bounded regardless of
// how verbose the model tries to be. Not a substitute for real rate limiting (out of scope for
// this phase - see feasibility_report.md §4.4), just a cheap per-call cap.
//
// 800, not 500: v4-flash defaults to "thinking" mode, where hidden reasoning tokens are billed
// out of this same budget (confirmed empirically - a 500-token cap got fully consumed by
// ~200-400 reasoning tokens before any visible reply was written, finish_reason "length", empty
// content -> chatController's FALLBACK_REPLY). Thinking is turned off below for this assistant
// (tool-routed lookups over a small fixed dataset don't need multi-step reasoning, and it's
// cheaper/faster without it), but 500 was already too tight for a full multi-tour answer with
// waypoints even without the reasoning tax, so the budget is raised too.
const MAX_OUTPUT_TOKENS = 800;

/**
 * @param {Array<object>} messages - OpenAI-style chat messages (system/user/assistant/tool).
 * @param {Array<object>} [tools] - OpenAI-style tool/function schemas.
 * @param {string} [toolChoice] - "auto" | "none" | { type: "function", function: { name } }.
 * @returns {Promise<object>} the raw DeepSeek chat-completion response body.
 */
async function createChatCompletion({ messages, tools, toolChoice = "auto" }) {
  const apiKey = process.env.DEEPSEEK_API_KEY;
  if (!apiKey) {
    throw new Error("DEEPSEEK_API_KEY is missing in environment variables");
  }

  const body = {
    model: DEEPSEEK_MODEL,
    messages,
    max_tokens: MAX_OUTPUT_TOKENS,
    // Lower than the 1.0 default - this assistant should stick closely to tool data (see the
    // price/fee-fabrication rule in chatController's SHARED_RULES) rather than embellish.
    temperature: 0.2,
    // See MAX_OUTPUT_TOKENS comment above - thinking mode's hidden reasoning tokens were eating
    // the output budget unpredictably and this assistant (fixed tools, small dataset) doesn't
    // need multi-step reasoning to route a lookup.
    thinking: { type: "disabled" },
  };
  if (tools && tools.length > 0) {
    body.tools = tools;
    body.tool_choice = toolChoice;
  }

  const res = await fetch(`${DEEPSEEK_BASE_URL}/chat/completions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const errText = await res.text().catch(() => "");
    throw new Error(`DeepSeek API error ${res.status}: ${errText}`);
  }

  return res.json();
}

module.exports = { createChatCompletion, DEEPSEEK_MODEL };
