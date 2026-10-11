# Avisos propios

`avisos.json` se descarga desde la app (cada ~30 min) y se muestra en la pestaña Avisos. Sirve para publicar
desvíos, obras o huelgas de **bus y metro**, que no tienen un feed de avisos abierto, sin sacar una versión nueva.

Ejemplo:

```json
{
  "version": 1,
  "notices": [
    {
      "id": "2026-10-linea-9-desvio",
      "network": "bus",
      "title": { "es": "Línea 9 desviada por obras", "ca": "Línia 9 desviada per obres", "en": "Line 9 diverted for roadworks" },
      "body":  { "es": "Desde el lunes no pasa por Colón." },
      "lines": ["9"],
      "stops": [],
      "start": 1791676800,
      "end": 1792281600,
      "url": "https://www.emtvalencia.es/"
    }
  ]
}
```

- `network`: `bus`, `metro` o `rail`. `lines`: nombres de línea tal como salen en la app. `stops`: claves como `bus:508`.
- `start`/`end`: segundos Unix (opcionales). Pasado `end` el aviso desaparece solo.
- Solo se aceptan enlaces `https://`. Cita siempre la fuente oficial; no copies textos con copyright.
