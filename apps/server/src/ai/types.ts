import type { ToolDef } from "@foco/core";

export const PROVIDERS = ["anthropic", "openai", "google"] as const;
export type ProviderId = (typeof PROVIDERS)[number];

/** Editable per user; these are only the defaults. */
export const DEFAULT_MODELS: Record<ProviderId, string> = {
  anthropic: "claude-opus-5-5",
  openai: "gpt-5",
  google: "gemini-2.5-flash",
};

export interface ChatTurn {
  role: "user" | "assistant";
  content: string;
}

export interface ToolAction {
  tool: string;
  input: unknown;
  result: unknown;
  error?: string;
}

export interface AgentRequest {
  system: string;
  history: ChatTurn[];
  tools: ToolDef[];
  execute: (name: string, input: unknown) => Promise<unknown>;
  maxSteps?: number;
}

export interface AgentResult {
  text: string;
  actions: ToolAction[];
}

export interface AiProvider {
  id: ProviderId;
  model: string;
  run(req: AgentRequest): Promise<AgentResult>;
}

export class ProviderError extends Error {
  constructor(message: string, readonly status = 502) {
    super(message);
  }
}

/** Runs one tool call, turning thrown errors into a result the model can read. */
export async function runTool(req: AgentRequest, name: string, input: unknown, actions: ToolAction[]) {
  try {
    const result = await req.execute(name, input);
    actions.push({ tool: name, input, result });
    return { ok: true as const, result };
  } catch (e) {
    const error = e instanceof Error ? e.message : String(e);
    actions.push({ tool: name, input, result: null, error });
    return { ok: false as const, error };
  }
}
