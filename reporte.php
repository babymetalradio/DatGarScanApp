<?php
/**
 * api/reporte.php
 * Recibe un reporte desde la app (asunto, manga, reporte)
 * y lo envía por correo a manumetal@datgarscanlation.xyz
 *
 * NO requiere sesión. Si el usuario está logueado, se incluye su info.
 */
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Authorization, Content-Type');
header('Access-Control-Allow-Methods: POST, OPTIONS');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../includes/functions.php';
require_once __DIR__ . '/../includes/auth.php';

// Sesión opcional: si hay token válido, se anota quién reportó
$user = currentUser(); // puede ser null

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['success' => false, 'message' => 'Método no permitido']);
    exit;
}

$input = json_decode(file_get_contents('php://input'), true);
if (!is_array($input)) {
    echo json_encode(['success' => false, 'message' => 'JSON inválido']);
    exit;
}

$asunto  = trim((string)($input['asunto']  ?? ''));
$manga   = trim((string)($input['manga']   ?? ''));
$reporte = trim((string)($input['reporte'] ?? ''));

if ($asunto === '' || $reporte === '') {
    echo json_encode(['success' => false, 'message' => 'Asunto y reporte son obligatorios']);
    exit;
}

if (mb_strlen($asunto) > 200) {
    $asunto = mb_substr($asunto, 0, 200);
}
if (mb_strlen($manga) > 200) {
    $manga = mb_substr($manga, 0, 200);
}
if (mb_strlen($reporte) > 4000) {
    $reporte = mb_substr($reporte, 0, 4000);
}

if ($user) {
    $username = $user['username'] ?? 'desconocido';
    $userId   = (int)($user['id'] ?? 0);
    $userMail = $user['email'] ?? '';
    $quien = "Usuario: {$username} (ID: {$userId})";
    if ($userMail !== '') {
        $quien .= "\nEmail del usuario: {$userMail}";
    }
} else {
    $username = 'Anónimo';
    $userMail = '';
    $quien = "Usuario: Anónimo (sin sesión)";
}

$para   = 'manumetal@datgarscanlation.xyz';
$titulo = '[DatGar Report] ' . $asunto;

$cuerpo  = "Nuevo reporte desde la app Dat-Gar Scan\n";
$cuerpo .= "=====================================\n\n";
$cuerpo .= $quien . "\n";
$cuerpo .= "Manga: " . ($manga !== '' ? $manga : 'No especificado') . "\n";
$cuerpo .= "Asunto: {$asunto}\n\n";
$cuerpo .= "Reporte:\n{$reporte}\n\n";
$cuerpo .= "Fecha: " . date('Y-m-d H:i:s') . "\n";
$cuerpo .= "IP: " . ($_SERVER['REMOTE_ADDR'] ?? '') . "\n";

$headers  = "From: noreply@datgarscanlation.xyz\r\n";
$headers .= "Reply-To: " . ($userMail !== '' ? $userMail : 'noreply@datgarscanlation.xyz') . "\r\n";
$headers .= "Content-Type: text/plain; charset=UTF-8\r\n";
$headers .= "X-Mailer: DatGarScan-App\r\n";

$enviado = @mail($para, '=?UTF-8?B?' . base64_encode($titulo) . '?=', $cuerpo, $headers);

if ($enviado) {
    echo json_encode([
        'success' => true,
        'message' => 'Reporte enviado. Gracias por avisarnos.',
    ], JSON_UNESCAPED_UNICODE);
} else {
    echo json_encode([
        'success' => false,
        'message' => 'No se pudo enviar el correo. Intenta más tarde.',
    ], JSON_UNESCAPED_UNICODE);
}
