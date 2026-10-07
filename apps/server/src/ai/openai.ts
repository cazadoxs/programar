import { ProviderError, runTool, type AgentRequest, type AgentResult, type AiProvider, type ToolAction } from "./types";

interface OaiMessage {
  role: "system" | "user" | "assistant" | "tool";
  content: string | null;
  tool_calls?: Array<{ id: string; type: "function"; function: { name: string; arguments: string } }>;
  tool_call_id?: string;
}

export function openaiProvider(apiKey: string, model: string, fetchImpl: typeof fetch = fetch): AiProvider {
  return {
    id: "openai",
    model,
    async run(req: AgentRequest): Promise<AgentResult> {
      const messages: OaiMessage[] = [{ role: "system", content: req.system }, ...req.history];
      const tools = req.tools.map((t) => ({ type: "function", function: { name: t.name, description: t.description, parameters: t.input_schema } }));
      const actions: ToolAction[] = [];

      for (let step = 0; step < (req.maxSteps ?? 8); step++) {
        const res = await fetchImpl("https://api.openai.com/v1/chat/completions", {
          method: "POST",
          headers: { authorization: `Bearer ${apiKey}`, "content-type": "application/json" },
          body: JSON.stringify({ model, messages, tools }),
        });
        if (res.status === 401) throw new ProviderError("La clave de OpenAI no es válida.", 400);
        if (!res.ok) throw new ProviderError(`OpenAI ${res.status}: ${(await res.text()).slice(0, 300)}`);
        const body = (await res.json()) as { choices: Array<{ message: OaiMessage }> };
        const msg = body.choices[0]?.message;
        if (!msg) throw new ProviderError("OpenAI devolvió una respuesta vacía.");
        messages.push(msg);
        if (!msg.tool_calls?.length) return { text: (msg.content ?? "").trim(), actions };
        for (const call of msg.tool_calls) {
          let input: unknown;
          try {
            input = JSON.parse(call.function.arguments || "{}");
          } catch {
            messages.push({ role: "tool", tool_call_id: call.id, content: "Error: argumentos JSON inválidos" });
            continue;
          }
          const r = await runTool(req, call.function.name, input, actions);
          messages.push({ role: "tool", tool_call_id: call.id, content: r.ok ? JSON.stringify(r.result ?? null) : `Error: ${r.error}` });
        }
      }
      return { text: "He hecho varios pasos y me he detenido para no alargarme. ¿Sigo?", actions };
    },
  };
}

export async function openaiTranscribe(apiKey: string, audio: Blob, filename: string, language?: string, fetchImpl: typeof fetch = fetch): Promise<string> {
  const form = new FormData();
  form.append("file", audio, filename);
  form.append("model", "whisper-1");
  if (language) form.append("language", language);
  const res = await fetchImpl("https://api.openai.com/v1/audio/transcriptions", {
    method: "POST",
    headers: { authorization: `Bearer ${apiKey}` },
    body: form,
  });
  if (!res.ok) throw new ProviderError(`OpenAI ${res.status}: ${(await res.text()).slice(0, 300)}`);
  return ((await res.json()) as { text: string }).text;
}
