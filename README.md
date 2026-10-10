# Dat-Gar Scan (Android)

App de lectura de manga de **Dat-Gar Scanlation**.

- **Sitio / API:** https://datgarscanlation.xyz  
- **Repo:** https://github.com/babymetalradio/DatGarScanApp  
- **Desarrollador / contacto admin:** manumetal@datgarscanlation.xyz  

---

## Packages y ramas

| Rama | `applicationId` | Uso |
|------|-----------------|-----|
| **main** | `com.datgarscan.app` | Producción / releases |
| **beta** | `com.datgarscan.app.beta` | Pruebas mixtas |
| **test-unity** | `com.datgarscan.app.testunity` | Solo Unity Ads |
| **test-inmobi** | `com.datgarscan.app.testinmobi` | Solo InMobi Ads |

> Cada package se instala como app distinta en el teléfono (no se pisan entre sí), salvo que se use el mismo `applicationId`.

---

## Versiones recientes (main)

| versionName | versionCode | Notas |
|-------------|-------------|--------|
| 1.9.9.4 | 12 | Offline → ir a descargas; Unity |
| 1.9.9.3 | 11 | Unity banner/interstitial/rewarded (4/6) |
| 1.9.9.2 | 10 | Unity Ads |
| 1.9.9.5-inmobi / 1.9.9.5 | 13 | Prueba InMobi en main (si se subió) |

Releases: tags `v1.9.9.x` en GitHub → workflow **release** genera APK firmado.

---

## Anuncios

### main (producción típica)
- **Banner / interstitial / rewarded:** Unity Ads  
- Game ID: `6197188`  
- Placements: `Banner_Android`, `Interstitial_Android`, `Rewarded_Android`  
- Interstitial: cada **4** aperturas de capítulo / cada **6** salidas del lector  

### test-inmobi (solo InMobi)
| Formato | Placement ID |
|--------|----------------|
| Banner | `10000829296` |
| Interstitial | `10000829295` |
| Rewarded | `10000835637` |

- Account ID: `e8325a72173441c488213372e95da99c`  
- App key (panel): `10000236359`  
- SDK: `com.inmobi.monetization:inmobi-ads-kotlin:10.8.8`  
- Panel: https://sher.inmobi.com (cuenta puede estar en *Pending Verification*)  

### test-unity
- Solo Unity (mismos IDs que arriba)  
- Interstitial 4 / 6  

### Pro / sin anuncios
Si el usuario tiene **Pro** o **sin anuncios** activo, no se muestran anuncios de ninguna red.

---

## Firebase

- Proyecto: `datgarscanapp-8f8a6`  
- `google-services.json` debe incluir el `package_name` de cada variante (`app`, `.beta`, `.testunity`, `.testinmobi`)  

---

## Build (GitHub Actions)

- **Compilar APK** (`build-apk.yml`): debug por rama (push / workflow_dispatch)  
- **Release** (`release.yml`): al crear tag `v*` → APK firmado  

Firma debug fija (keystore en repo/secrets) para poder actualizar sin desinstalar en pruebas.

Requisitos de stack aproximados:
- AGP 8.4.1  
- Kotlin 2.1.0  
- minSdk 24 / targetSdk 34  
- Java 17  

---

## Funciones principales de la app

- Catálogo de mangas (API `/api`)  
- Lector, favoritos, historial, descargas offline  
- Login / sesión  
- Tienda “Garritas” (recompensas con rewarded)  
- Notificaciones FCM (abrir manga o link externo)  
- Reportes al correo del scan  
- Detección **sin conexión** → botón a **Mis descargas**  
- (Beta / experimentos) radio FAB, banner eventos, etc.  

---

## Backend (web)

- Base: PHP + MySQL en datgarscanlation.xyz  
- Admin: envío de anuncios FCM + opcional Telegram  
- Historial de notificaciones: tabla `notification_log` en `admin/enviar_notificacion.php`  
- Tokens: tabla `device_tokens`  

---

## Flujo de trabajo recomendado

1. Experimentos de ads → `test-unity` o `test-inmobi`  
2. Validar sin Pro varios días  
3. Pasar a **main** solo lo estable  
4. Tag `vX.Y.Z` + release  
5. **No** mezclar `applicationId` de prueba en main  

---

## Contacto / notas internas

- Correo reportes / admin: manumetal@datgarscanlation.xyz  
- Segundo correo (reportes): alexancz12@gmail.com (si está configurado en el endpoint)  
- StartApp App ID histórico: `207366634` (ya no es la red principal en main)  

---

## README – mantenimiento

Al añadir redes de anuncios, packages o releases, **actualizar este README** con:

- Rama / package  
- versionName y versionCode  
- IDs de ads (Game ID, Account, Placement)  
- Qué red usa banner / interstitial / rewarded  
