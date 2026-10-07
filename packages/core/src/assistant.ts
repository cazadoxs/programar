/**
 * Provider-neutral definition of what the AI assistant can do. The server
 * translates these into each provider's tool format (Anthropic, OpenAI,
 * Google) and executes the calls against the user's synced data.
 */

export interface JsonSchema {
  type: "object";
  properties: Record<string, unknown>;
  required: string[];
  additionalProperties: false;
}

export interface ToolDef {
  name: string;
  description: string;
  input_schema: JsonSchema;
}

const obj = (properties: Record<string, unknown>, required: string[] = []): JsonSchema => ({
  type: "object",
  properties,
  required,
  additionalProperties: false,
});
const iso = { type: "string", description: "Fecha y hora ISO 8601 con zona, p. ej. 2026-10-08T17:00:00+02:00" };

export const ASSISTANT_TOOLS: ToolDef[] = [
  {
    name: "list_events",
    description: "Lista los eventos de todos los calendarios del usuario (Google, Outlook, iCloud, móvil, Foco…) en un intervalo.",
    input_schema: obj({ from: iso, to: iso }, ["from", "to"]),
  },
  {
    name: "create_event",
    description: "Crea un evento en el calendario de Foco. Usa focus=true para bloques de trabajo/estudio que deben activar el bloqueo de apps.",
    input_schema: obj(
      {
        title: { type: "string" },
        start: iso,
        end: iso,
        location: { type: "string" },
        notes: { type: "string" },
        focus: { type: "boolean" },
      },
      ["title", "start", "end"],
    ),
  },
  {
    name: "move_event",
    description: "Cambia la hora de un evento de Foco existente.",
    input_schema: obj({ id: { type: "string" }, start: iso, end: iso }, ["id", "start", "end"]),
  },
  {
    name: "delete_event",
    description: "Borra un evento de Foco. Pide confirmación al usuario antes de usarla.",
    input_schema: obj({ id: { type: "string" } }, ["id"]),
  },
  {
    name: "list_tasks",
    description: "Lista las tareas del usuario (agenda del día y bandeja general).",
    input_schema: obj({ includeDone: { type: "boolean" } }),
  },
  {
    name: "create_task",
    description: "Crea una tarea. due puede ser una fecha (YYYY-MM-DD) o fecha y hora ISO; sin due va a la bandeja general.",
    input_schema: obj(
      { title: { type: "string" }, due: { type: "string" }, estimateMinutes: { type: "integer" }, notes: { type: "string" } },
      ["title"],
    ),
  },
  {
    name: "complete_task",
    description: "Marca una tarea como hecha.",
    input_schema: obj({ id: { type: "string" } }, ["id"]),
  },
  {
    name: "find_free_slots",
    description: "Busca huecos libres en todos los calendarios entre dos instantes.",
    input_schema: obj({ from: iso, to: iso, minMinutes: { type: "integer" } }, ["from", "to"]),
  },
  {
    name: "start_focus_session",
    description:
      "Empieza una sesión de foco que bloquea apps. kind: pomodoro | study | manual. appIds: instagram, tiktok, youtube, snapchat, facebook, x, reddit, whatsapp. surfaces opcional: app, reels, stories, explore, messages, feed (por defecto app completa).",
    input_schema: obj(
      {
        kind: { type: "string", enum: ["pomodoro", "study", "manual"] },
        minutes: { type: "integer" },
        title: { type: "string" },
        appIds: { type: "array", items: { type: "string" } },
        surfaces: { type: "array", items: { type: "string", enum: ["app", "reels", "stories", "explore", "messages", "feed"] } },
      },
      ["kind", "minutes", "appIds"],
    ),
  },
  {
    name: "stop_focus_session",
    description: "Para la sesión de foco activa (solo si no es estricta).",
    input_schema: obj({}),
  },
];

export interface AssistantPreset {
  id: string;
  label: string;
  prompt: string;
}

export const ASSISTANT_PRESETS: AssistantPreset[] = [
  {
    id: "plan_day",
    label: "Planifica mi día",
    prompt:
      "Mira mi calendario y mis tareas de hoy y propón un plan realista con bloques de foco en los huecos libres. Cuando te diga que sí, crea los bloques.",
  },
  {
    id: "reschedule",
    label: "Reorganiza lo que no hice",
    prompt: "Busca las tareas atrasadas o sin hacer y propón cuándo hacerlas esta semana según mis huecos libres.",
  },
  {
    id: "week_review",
    label: "Resumen de la semana",
    prompt: "Hazme un resumen de esta semana: eventos importantes, tareas hechas y pendientes, y qué debería priorizar.",
  },
  {
    id: "tomorrow",
    label: "Prepárame para mañana",
    prompt: "¿Qué tengo mañana? Dime qué preparar hoy y si hay algún hueco para adelantar tareas.",
  },
  {
    id: "focus_now",
    label: "Quiero concentrarme ya",
    prompt: "Quiero concentrarme ahora mismo. Elige la tarea más importante, empieza un pomodoro bloqueando redes sociales y dime por dónde empezar.",
  },
];

export type AssistantMode = "chat" | "work";

export function assistantSystemPrompt(ctx: { now: Date; timeZone: string; userName?: string; mode: AssistantMode }): string {
  const lines = [
    "Eres el asistente de Foco, una app para dejar de procrastinar que une calendario, tareas y bloqueo de apps distractoras.",
    `Hablas con ${ctx.userName ?? "el usuario"}. Responde en su idioma (por defecto español), breve y práctico.`,
    `Ahora es ${ctx.now.toISOString()} y la zona horaria del usuario es ${ctx.timeZone}. Usa siempre fechas ISO con su zona en las herramientas.`,
    "Usa las herramientas para leer o cambiar el calendario, las tareas y las sesiones de foco en lugar de inventar datos.",
    "Antes de borrar algo o de crear más de tres eventos de golpe, pide confirmación.",
  ];
  if (ctx.mode === "work") {
    lines.push(
      "Modo Trabajo: ayuda al usuario a ejecutar. Propón bloques de foco concretos con hora, y cuando acepte créalos con focus=true. Si dice que quiere empezar ya, inicia una sesión de foco.",
    );
  }
  return lines.join("\n");
}
