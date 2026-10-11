# Revisión de seguridad

| Área | Medida |
|---|---|
| Red | Permisos `INTERNET`, `ACCESS_NETWORK_STATE` y `AD_ID` eliminados con `tools:node="remove"`: la app no puede conectarse. |
| Datos en reposo | Biblioteca en `filesDir` (privado). `allowBackup=false` y reglas de extracción que excluyen todo de copias en la nube y traspasos. |
| Compartir | FileProvider no exportado, solo la carpeta temporal `cache/compartir`; permisos de lectura puntuales; la carpeta se limpia al iniciar. |
| Entrada | Solo se aceptan `content://` de otras apps (nunca `file://` ni nuestro propio provider). Imágenes con tope de píxeles; PDFs de hasta 150 MB y 100 páginas; PDFs cifrados se rechazan con aviso. |
| Rutas | Los ids de documento se validan como UUID antes de tocar el disco; nombres de archivo y entradas ZIP se sanean (sin `/`, `..` ni caracteres de control, evita Zip Slip). |
| Pantalla | Modo privado con `FLAG_SECURE`; bloqueo con huella/cara/PIN (BiometricPrompt, `BIOMETRIC_WEAK` o credencial del dispositivo). |
| Portapapeles | El texto copiado se marca como sensible en Android 13+. |
| Compras | La firma de cada compra se verifica en el dispositivo con la clave de licencia de Play; las compras se reconocen (acknowledge) y las reembolsadas retiran Pro. Las compilaciones de release no se generan sin la clave. |
| Código | R8 con minimización y recorte de recursos; los `Log.d/i/v` se eliminan en release. Sin WebViews, sin `exported` extra, sin ejecución dinámica. |
| Dependencias | Solo Jetpack, ML Kit, Play Billing, PdfBox-Android (Apache 2.0) y kotlinx. Dependabot recomendado. |

Límites conocidos: un móvil con root puede leer el almacenamiento privado; el modo seguro no impide fotografiar la pantalla con otro dispositivo; las compras reembolsadas se detectan al abrir la app.
