import { ProviderError, runTool, type AgentRequest, type AgentResult, type AiProvider, type ToolAction } from "./types";

interface Part {
  text?: string;
  functionCall?: { name: string; args?: Record<string, unknown> };
  functionResponse?: { name: string; response: Record<string, unknown> };
  inline_data?: { mime_type: string; data: string };
}
interface Content {
  role: "user" | "model";
  parts: Part[];
}

const BASE = "https://generativelanguage.googleapis.com/v1beta/models";

/** Gemini accepts an OpenAPI subset: no additionalProperties, and no empty objects. */
function toGeminiSchema(schema: { properties: Record<string, unknown>; required: string[] }) {
  if (Object.keys(schema.properties).length === 0) return undefined;
  return { type: "object", properties: schema.properties, required: schema.required };
}

async function generate(apiKey: string, model: string, body: unknown, fetchImpl: typeof fetch) {
  const res = await fetchImpl(`${BASE}/${encodeURIComponent(model)}:generateContent`, {
    method: "POST",
    headers: { "x-goog-api-key": apiKey, "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (res.status === 400 || res.status === 403) {
    const text = await res.text();
    if (/API key/i.test(text)) throw new ProviderError("La clave de Google no es válida.", 400);
    throw new ProviderError(`Google ${res.status}: ${text.slice(0, 300)}`);
  }
  if (!res.ok) throw new ProviderError(`Google ${res.status}: ${(await res.text()).slice(0, 300)}`);
  const json = (await res.json()) as { candidates?: Array<{ content?: Content }> };
  const content = json.candidates?.[0]?.content;
  if (!content) throw new ProviderError("Google devolvió una respuesta vacía.");
  return content;
}

export function googleProvider(apiKey: string, model: string, fetchImpl: typeof fetch = fetch): AiProvider {
  return {
    id: "google",
    model,
    async run(req: AgentRequest): Promise<AgentResult> {
      const contents: Content[] = req.history.map((t) => ({ role: t.role === "assistant" ? "model" : "user", parts: [{ text: t.content }] }));
      const tools = [{ functionDeclarations: req.tools.map((t) => ({ name: t.name, description: t.description, parameters: toGeminiSchema(t.input_schema) })) }];
      const actions: ToolAction[] = [];

      for (let step = 0; step < (req.maxSteps ?? 8); step++) {
        const content = await generate(apiKey, model, { systemInstruction: { parts: [{ text: req.system }] }, contents, tools }, fetchImpl);
        contents.push({ role: "model", parts: content.parts });
        const calls = content.parts.filter((p) => p.functionCall);
        if (calls.length === 0) return { text: content.parts.map((p) => p.text ?? "").join("").trim(), actions };
        const responses: Part[] = [];
        for (const p of calls) {
          const { name, args } = p.functionCall!;
          const r = await runTool(req, name, args ?? {}, actions);
          responses.push({ functionResponse: { name, response: r.ok ? { result: r.result ?? null } : { error: r.error } } });
        }
        contents.push({ role: "user", parts: responses });
      }
      return { text: "He hecho varios pasos y me he detenido para no alargarme. ¿Sigo?", actions };
    },
  };
}

export async function googleTranscribe(apiKey: string, model: string, audio: Blob, language?: string, fetchImpl: typeof fetch = fetch): Promise<string> {
  const data = Buffer.from(await audio.arrayBuffer()).toString("base64");
  const prompt = `Transcribe este audio literalmente${language ? ` (idioma: ${language})` : ""}. Devuelve solo el texto.`;
  const content = await generate(
    apiKey,
    model,
    { contents: [{ role: "user", parts: [{ text: prompt }, { inline_data: { mime_type: audio.type || "audio/m4a", data } }] }] },
    fetchImpl,
  );
  return content.parts.map((p) => p.text ?? "").join("").trim();
}
