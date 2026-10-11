# Configurar Firebase (10 minutos, lo hace Álvaro)

La gestión remota usa Firebase **Authentication (anónima)** y **Realtime Database**. No hay servidor propio.

1. https://console.firebase.google.com → **Añadir proyecto** "facil" (sin Analytics).
2. **Authentication → Sign-in method → Anónimo → Habilitar.**
3. **Realtime Database → Crear base de datos** → ubicación **europe-west1 (Bélgica)** → modo bloqueado.
4. Pestaña **Reglas** → pega el contenido de `firebase/database.rules.json` → Publicar.
5. **Configuración del proyecto → General → Clave de API web** (empieza por `AIza`).
6. En `apps/facil/local.properties`:
   ```
   facil.firebaseApiKey=AIza...
   facil.firebaseDbUrl=https://facil-xxxx-default-rtdb.europe-west1.firebasedatabase.app
   ```
7. Google Cloud Console → **APIs y servicios → Credenciales** → restringe la clave a "Identity Toolkit API" y "Token Service API" (y a la app Android `app.facil.launcher` con la huella SHA-1 de tu firma).
8. Activa **App Check** más adelante (Play Integrity) si ves abuso; las reglas ya limitan lo que cada usuario puede tocar.

La clave de API de Firebase no es un secreto (va en cualquier app), la protección son las **reglas** y las restricciones de la clave.

## Coste
Plan Spark (gratis) aguanta miles de móviles: cada móvil escribe ~100 KB/día. Si creces, plan Blaze con alertas de presupuesto.
