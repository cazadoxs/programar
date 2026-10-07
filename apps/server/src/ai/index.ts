import { anthropicProvider } from "./anthropic";
import { googleProvider } from "./google";
import { openaiProvider } from "./openai";
import { DEFAULT_MODELS, type AiProvider, type ProviderId } from "./types";

export * from "./types";

export type ProviderFactory = (id: ProviderId, apiKey: string, model?: string | null) => AiProvider;

export const createProvider: ProviderFactory = (id, apiKey, model) => {
  const m = model || DEFAULT_MODELS[id];
  switch (id) {
    case "anthropic":
      return anthropicProvider(apiKey, m);
    case "openai":
      return openaiProvider(apiKey, m);
    case "google":
      return googleProvider(apiKey, m);
  }
};
