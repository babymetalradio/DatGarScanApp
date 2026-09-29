<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../includes/functions.php';
require_once __DIR__ . '/../includes/auth.php';
require_once __DIR__ . '/../includes/fcm.php';
require_once __DIR__ . '/../includes/telegram.php';

$user = requireRole(['admin', 'editor']);

try {
    $pdo->exec("CREATE TABLE IF NOT EXISTS device_tokens (
        id INT(11) PRIMARY KEY AUTO_INCREMENT,
        user_id INT(11),
        token VARCHAR(255),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY unique_token (token)
    )");
} catch (Exception $e) { /* si ya existe con otra forma, seguimos igual */ }

$error = '';
$success = '';
$detalleEnvios = [];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $titulo = trim($_POST['titulo'] ?? '');
    $mensaje = trim($_POST['mensaje'] ?? '');
    $mangaSlug = trim($_POST['manga_slug'] ?? '');
    $linkUrl = trim($_POST['link_url'] ?? '');

    // Validar URL si viene informada
    if ($linkUrl !== '' && !preg_match('#^https?://#i', $linkUrl)) {
        $linkUrl = 'https://' . $linkUrl;
    }

    if ($titulo === '' || $mensaje === '') {
        $error = 'Completa el título y el mensaje.';
    } else {
        try {
            $tokens = $pdo->query("SELECT DISTINCT token FROM device_tokens")->fetchAll(PDO::FETCH_COLUMN);
        } catch (Exception $e) {
            $tokens = [];
        }

        $enviadoApp = false;
        if (!empty($tokens)) {
            $datosExtra = [];
            // Prioridad: link externo > manga
            if ($linkUrl !== '') {
                $datosExtra['link_url'] = $linkUrl;
            } elseif ($mangaSlug !== '') {
                $datosExtra['manga_slug'] = $mangaSlug;
            }
            $detalleEnvios = enviarNotificacionATokensYLimpiar($pdo, $tokens, $titulo, $mensaje, $datosExtra);
            $exitosos = count(array_filter($detalleEnvios, fn($r) => $r['ok']));
            $limpiados = count(array_filter($detalleEnvios, fn($r) => $r['token_invalido']));
            $enviadoApp = true;
        }

        $enviadoTelegram = false;
        if (!empty($_POST['tambien_telegram'])) {
            $mensajeTelegram = trim($_POST['mensaje_telegram'] ?? '');
            $textoTelegram = $mensajeTelegram !== '' ? $mensajeTelegram : "$titulo\n$mensaje";
            $imagenTelegram = null;

            // Prioridad 1: imagen subida a mano
            if (!empty($_FILES['imagen_custom']['tmp_name']) && $_FILES['imagen_custom']['error'] === UPLOAD_ERR_OK) {
                $extension = strtolower(pathinfo($_FILES['imagen_custom']['name'], PATHINFO_EXTENSION));
                if (in_array($extension, ['jpg', 'jpeg', 'png', 'webp', 'gif'])) {
                    $carpetaAnuncios = __DIR__ . '/../uploads/anuncios';
                    if (!is_dir($carpetaAnuncios)) mkdir($carpetaAnuncios, 0755, true);

                    $nombreArchivo = 'anuncio_' . time() . '_' . mt_rand(1000, 9999) . '.' . $extension;
                    $destino = $carpetaAnuncios . '/' . $nombreArchivo;

                    if (move_uploaded_file($_FILES['imagen_custom']['tmp_name'], $destino)) {
                        $imagenTelegram = (defined('SITIO_URL') ? SITIO_URL : 'https://datgarscanlation.xyz') . "/uploads/anuncios/$nombreArchivo";
                    }
                }
            }

            // Prioridad 2: portada del manga seleccionado, si no se subió nada a mano
            if (!$imagenTelegram && $mangaSlug !== '') {
                $coverStmt = $pdo->prepare("SELECT cover FROM mangas WHERE slug = ?");
                $coverStmt->execute([$mangaSlug]);
                $cover = $coverStmt->fetchColumn();
                if ($cover) $imagenTelegram = (defined('SITIO_URL') ? SITIO_URL : 'https://datgarscanlation.xyz') . "/uploads/covers/$cover";
            }

            $enviadoTelegram = enviarTelegram($textoTelegram, $imagenTelegram, $detalleTelegram);
        }

        if (!$enviadoApp && !$enviadoTelegram) {
            $error = 'No había ningún dispositivo registrado y Telegram no estaba marcado (o falló). No se mandó nada.';
        } else {
            $partes = [];
            if ($enviadoApp) $partes[] = "Enviado a $exitosos de " . count($tokens) . " dispositivo(s)."
                . ($limpiados > 0 ? " Se limpiaron $limpiados token(s) de apps desinstaladas." : "");
            if (!empty($_POST['tambien_telegram'])) {
                $partes[] = $enviadoTelegram ? "Mandado a Telegram correctamente." : "Falló el envío a Telegram.";
            }
            $success = implode(' ', $partes);
        }
    }
}

$mangasParaSelector = $pdo->query("SELECT slug, title FROM mangas WHERE publication_status = 'published' ORDER BY title ASC")->fetchAll();

try {
    $totalDispositivos = (int) $pdo->query("SELECT COUNT(DISTINCT token) FROM device_tokens")->fetchColumn();
} catch (Exception $e) {
    $totalDispositivos = 0;
}

$pageTitle = 'Enviar anuncio';
require_once __DIR__ . '/../includes/header.php';
?>

<h1 class="page-title">Enviar anuncio a todos los usuarios</h1>

<p style="color:var(--text-muted);margin-bottom:20px;">
    Dispositivos registrados para notificaciones: <strong><?= $totalDispositivos ?></strong>
</p>

<?php if ($error): ?>
    <div style="background:#3a1a1a;border:1px solid #e63946;border-radius:8px;padding:14px;margin-bottom:20px;color:#fff;">
        <?= e($error) ?>
    </div>
<?php endif; ?>

<?php if ($success): ?>
    <div style="background:#1a3a2a;border:1px solid #2a9d8f;border-radius:8px;padding:14px;margin-bottom:20px;color:#fff;">
        <?= e($success) ?>
    </div>
<?php endif; ?>

<form method="POST" enctype="multipart/form-data" style="max-width:500px;">
    <div class="form-group" style="margin-bottom:16px;">
        <label>Título de la notificación</label>
        <input type="text" name="titulo" maxlength="80" required
               placeholder="Ej: Mantenimiento programado"
               style="width:100%;padding:10px;border-radius:8px;border:2px solid var(--border);background:var(--bg);color:var(--text);">
    </div>

    <div class="form-group" style="margin-bottom:16px;">
        <label>Mensaje</label>
        <textarea name="mensaje" rows="4" maxlength="200" required
                  placeholder="Ej: El sitio va a estar en mantenimiento esta noche de 12am a 2am."
                  style="width:100%;padding:10px;border-radius:8px;border:2px solid var(--border);background:var(--bg);color:var(--text);"></textarea>
    </div>

    <div class="form-group" style="margin-bottom:16px;">
        <label>Al tocar la notificación, abrir manga (opcional)</label>
        <select name="manga_slug"
                style="width:100%;padding:10px;border-radius:8px;border:2px solid var(--border);background:var(--bg);color:var(--text);">
            <option value="">Solo abrir la app (sin ir a ningún manga)</option>
            <?php foreach ($mangasParaSelector as $m): ?>
                <option value="<?= e($m['slug']) ?>"><?= e($m['title']) ?></option>
            <?php endforeach; ?>
        </select>
        <p style="color:var(--text-muted);font-size:0.8rem;margin-top:4px;margin-bottom:0;">
            Si también pones un link abajo, el link tiene prioridad sobre el manga.
        </p>
    </div>

    <div class="form-group" style="margin-bottom:16px;">
        <label>O abrir un link (opcional)</label>
        <input type="url" name="link_url" maxlength="500"
               placeholder="https://funticket.mx/evento/... o cualquier URL"
               style="width:100%;padding:10px;border-radius:8px;border:2px solid var(--border);background:var(--bg);color:var(--text);">
        <p style="color:var(--text-muted);font-size:0.8rem;margin-top:4px;margin-bottom:0;">
            Ejemplos: boletos, Discord, Instagram, tu web. Al tocar la notificación se abre en el navegador.
        </p>
    </div>

    <div class="form-group" style="margin-bottom:16px;">
        <label style="display:flex;align-items:center;gap:8px;font-weight:normal;">
            <input type="checkbox" name="tambien_telegram" value="1" id="checkTelegram" onchange="document.getElementById('panelTelegram').style.display = this.checked ? 'block' : 'none';">
            También publicar en el canal de Telegram
        </label>
    </div>

    <div id="panelTelegram" style="display:none;background:var(--bg-card);border:1px solid var(--border);border-radius:8px;padding:16px;margin-bottom:16px;">
        <p style="color:var(--text-muted);font-size:0.8rem;margin-top:0;margin-bottom:12px;">
            Opciones solo para la publicación en Telegram.
        </p>

        <div class="form-group" style="margin-bottom:16px;">
            <label>Mensaje para Telegram (opcional)</label>
            <textarea name="mensaje_telegram" rows="4" maxlength="1000"
                      placeholder="Si lo dejas vacío, se usa el título + mensaje de arriba."
                      style="width:100%;padding:10px;border-radius:8px;border:2px solid var(--border);background:var(--bg);color:var(--text);"></textarea>
        </div>

        <div class="form-group" style="margin-bottom:0;">
            <label>Imagen para Telegram (opcional)</label>
            <input type="file" name="imagen_custom" accept="image/*"
                   style="width:100%;padding:8px;border-radius:8px;border:2px solid var(--border);background:var(--bg);color:var(--text);">
            <p style="color:var(--text-muted);font-size:0.8rem;margin-top:4px;margin-bottom:0;">
                Si subes una imagen aquí, se usa esta en vez de la portada del manga seleccionado.
            </p>
        </div>
    </div>

    <button type="submit" class="btn btn-primary"
            onclick="return confirm('¿Seguro? Esto se manda a TODOS los usuarios con la app instalada.');">
        Enviar a todos
    </button>
</form>

<?php require_once __DIR__ . '/../includes/footer.php'; ?>
