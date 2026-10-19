# Beta — Opción 2: bloqueador DNS/ads

Package: `com.datgarscan.app.beta`
Versión: 1.9.9.6-beta-dns (versionCode 23)

## Comportamiento
- Al abrir el **lector**, se evalúa bloqueo en segundo plano.
- Si Pro o sin anuncios → no hace nada.
- Si sin red → no bloquea (offline OK).
- Si detecta bloqueo → overlay a pantalla completa (no se cierra con atrás sobre el panel).
- Botones: Tienda · Ajustes DNS · Ya lo desactivé (re-evalúa).

## Cómo probar
1. Instalar APK beta.
2. Sin Pro.
3. Activar DNS privado (dns.adguard-dns.com) o app AdGuard.
4. Abrir un capítulo online → debe salir el overlay.
5. Desactivar bloqueador → "Ya lo desactivé" → debe dejar leer.

## Archivos
- ads/DetectorBloqueo.kt
- res/layout/dialog_bloqueo_ads.xml
- lector/LectorActivity.kt (integración)
