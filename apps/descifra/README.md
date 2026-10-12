# Descifra

App Android que explica cartas y documentos oficiales españoles (Hacienda, Seguridad Social, DGT, ayuntamientos, juzgados, bancos, caseros): qué son, qué piden, cuánto y para cuándo. Todo se procesa en el móvil; la IA en la nube es opcional y con la clave del propio usuario.

- Android (minSdk 26, targetSdk 36), Kotlin y Jetpack Compose.
- Sin cuentas, sin servidores, sin anuncios, sin analítica.
- Gratis en lo esencial; **Descifra Pro** es un pago único (`descifra_pro`).

## Cómo funciona

```
foto ──ML Kit Document Scanner──► imágenes ──ML Kit OCR (en el móvil)──► texto
                                                                           │
                    revisión del texto + fecha de recepción (usuario) ◄────┘
                                                                           │
         reglas locales (siempre) ──► Analysis ◄── IA del móvil (Gemini Nano, ML Kit GenAI Prompt API)
                                          ▲        o IA en la nube con la clave del usuario (Pro)
                                          │
                       AiMerge: la IA no puede inventar fechas ni importes
```

1. **Reglas locales** (`core/.../rules`): tipo de carta, remitente, fechas con su rol (plazo, emisión), periodos («10 días hábiles») resueltos con el calendario legal español, importes, NIF/NIE/CIF/IBAN validados, expediente, CSV, matrícula. Es el análisis de referencia y la red de seguridad.
2. **IA** (`core/.../ai`, `app/.../ml`): produce el resumen y los pasos. Todo lo que afirma sobre fechas e importes se contrasta con el texto de la carta (`AiMerge`): las fechas cuya cita no aparece en el original se marcan «No está en la carta»; los importes que no están en el texto se descartan.
3. **Privacidad antes de la nube** (`privacy/Redactor.kt`): NIF, IBAN, teléfonos, emails, nombres y direcciones se sustituyen por marcadores y se restituyen en la respuesta.
4. **Original siempre visible**: panel «Carta original» con las citas resaltadas (en tablets, a la derecha).

## Estructura

| Carpeta | Contenido |
|---|---|
| `core/` | Kotlin puro (sin Android): reglas, plazos legales, redacción de datos personales, prompts, clientes de IA y fusión de resultados. 12 tests unitarios |
| `app/` | Interfaz Compose «Papel y sello», escáner, OCR, IA del móvil, almacenamiento cifrado, avisos y compras |
| `publicar/` | Ficha de Play Store, logo, política de privacidad, guía de publicación, capturas, guía de vídeos y publicidad |
| `SEGURIDAD.md` | Revisión de seguridad y decisiones |

## Compilar y probar

```bash
./gradlew -p core test          # lógica, sin Android SDK
./gradlew :app:assembleDebug    # APK de depuración (necesita Android SDK 36)
./gradlew :app:lintDebug
```

La integración continua (`.github/workflows/descifra.yml`) ejecuta tests, APK de depuración, lint y un `bundleRelease` con R8.

## Diseño

«Papel y sello»: papel crema, azul tinta y un único color de acento, el **sello bermellón**, reservado a lo que requiere atención. Titulares en Fraunces, texto en Atkinson Hyperlegible (diseñada para baja visión, pensada para textos densos). Modo oscuro «tinta». Las tarjetas son hojas con la esquina doblada y la urgencia se muestra como un sello de caucho.

## Limitaciones conocidas

- Los plazos «en días hábiles» solo conocen festivos nacionales; por eso se marcan como estimados.
- Gemini Nano solo está en algunos móviles (Pixel 9/10, Galaxy S25, Xiaomi 15…). El API `genai-prompt` está en beta.
- El OCR en el móvil no lee escritura a mano.
- No detecta cartas fraudulentas.
