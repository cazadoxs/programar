import Anthropic from "@anthropic-ai/sdk";
import { ProviderError, runTool, type AgentRequest, type AgentResult, type AiProvider, type ToolAction } from "./types";

export function anthropicProvider(apiKey: string, model: string): AiProvider {
  const client = new Anthropic({ apiKey });
  return {
    id: "anthropic",
    model,
    async run(req: AgentRequest): Promise<AgentResult> {
      const tools: Anthropic.Beta.BetaTool[] = req.tools.map((t) => ({ name: t.name, description: t.description, input_schema: { ...t.input_schema } }));
      const messages: Anthropic.Beta.BetaMessageParam[] = req.history.map((t) => ({ role: t.role, content: t.content }));
      const actions: ToolAction[] = [];

      for (let step = 0; step < (req.maxSteps ?? 8); step++) {
        let res: Anthropic.Beta.BetaMessage;
        try {
          res = await client.beta.messages.create({
            model,
            max_tokens: 16000,
            system: req.system,
            tools,
            messages,
            output_config: { effort: "medium" },
            // If a safety classifier declines, the API retries on a suitable model instead of stopping.
            betas: ["server-side-fallback-2026-07-01"],
            fallbacks: "default",
          });
        } catch (e) {
          if (e instanceof Anthropic.AuthenticationError) throw new ProviderError("La clave de Anthropic no es válida.", 400);
          if (e instanceof Anthropic.RateLimitError) throw new ProviderError("Anthropic: límite de uso alcanzado, prueba en un momento.", 429);
          if (e instanceof Anthropic.APIError) throw new ProviderError(`Anthropic: ${e.message}`);
          throw e;
        }

        if (res.stop_reason === "refusal") return { text: "No puedo ayudar con eso.", actions };
        // Append the whole content (thinking blocks included) unchanged.
        messages.push({ role: "assistant", content: res.content });
        if (res.stop_reason === "pause_turn") continue;

        const toolUses = res.content.filter((b): b is Anthropic.Beta.BetaToolUseBlock => b.type === "tool_use");
        if (toolUses.length === 0) {
          const text = res.content.flatMap((b) => (b.type === "text" ? [b.text] : [])).join("\n").trim();
          return { text, actions };
        }
        const results = await Promise.all(
          toolUses.map(async (tu): Promise<Anthropic.Beta.BetaToolResultBlockParam> => {
            const r = await runTool(req, tu.name, tu.input, actions);
            return r.ok
              ? { type: "tool_result", tool_use_id: tu.id, content: JSON.stringify(r.result ?? null) }
              : { type: "tool_result", tool_use_id: tu.id, content: r.error, is_error: true };
          }),
        );
        messages.push({ role: "user", content: results });
      }
      return { text: "He hecho varios pasos y me he detenido para no alargarme. ¿Sigo?", actions };
    },
  };
}
