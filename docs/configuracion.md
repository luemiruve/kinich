# Configuración

El backend se configura con variables de entorno. En desarrollo pueden definirse en `backend/.env` (excluido de Git;
plantilla en `backend/.env.example`). En producción, inyéctalas con el mecanismo de tu plataforma: nunca dejes
secretos en archivos versionados.

## Variables de entorno

### Base de datos

| Variable | Obligatoria | Descripción |
|---|---|---|
| `KINICH_DB_URL` | Sí | URL JDBC de PostgreSQL, p. ej. `jdbc:postgresql://localhost:5432/kinich_dev` |
| `KINICH_DB_USERNAME` | Sí | Usuario de la base de datos |
| `KINICH_DB_PASSWORD` | Sí | Contraseña del usuario |
| `KINICH_TEST_DB_URL` | Solo pruebas | Base para las pruebas de integración (por defecto `kinich_test` en `localhost`) |

Las migraciones (`database/migrations`) se aplican automáticamente al arrancar. Hibernate solo valida el esquema; nunca
lo modifica.

### Sesión y seguridad

| Variable | Por defecto | Descripción |
|---|---|---|
| `KINICH_JWT_SECRET` | — (obligatoria) | Clave HMAC del token de sesión, en Base64 y de al menos 256 bits. Genérala con `openssl rand -base64 48` |
| `KINICH_JWT_TTL` | `PT4H` | Duración de la sesión (formato ISO-8601) |
| `KINICH_COOKIE_SECURE` | `true` | Marca la cookie de sesión como `Secure`. Solo puede ser `false` en desarrollo sobre `http://localhost` |
| `KINICH_CORS_ALLOWED_ORIGINS` | vacío | Orígenes permitidos, separados por comas. Vacío rechaza toda petición de otro origen |
| `KINICH_FORWARD_HEADERS` | `none` | Usa `native` detrás de un proxy inverso con HTTPS |

### Cuenta de administración inicial

| Variable | Descripción |
|---|---|
| `KINICH_ADMIN_EMAIL` | Correo de la cuenta (es su usuario) |
| `KINICH_ADMIN_PASSWORD` | Contraseña: entre 12 caracteres y 72 bytes |
| `KINICH_ADMIN_FULL_NAME` | Nombre que se muestra en el panel |

La cuenta se crea solo si no existe ningún usuario. No hay registro público: es la única forma de dar de alta la
administración. Tras el primer arranque puedes retirar la contraseña de la configuración.

### Imágenes

| Variable | Por defecto | Descripción |
|---|---|---|
| `KINICH_MEDIA_DIR` | — (obligatoria) | Directorio donde se guardan originales, representaciones web y logotipos. Ruta absoluta, existente y escribible, fuera de cualquier carpeta servida por la web |
| `KINICH_VIPS_COMMAND` | `vips` | Comando de libvips |
| `KINICH_MEDIA_WORK_DIR` | temporal del sistema | Directorio de trabajo privado de libvips |

## Parámetros técnicos (`application.yml`)

Estos valores tienen un valor por defecto razonable y no suelen cambiarse:

| Parámetro | Valor | Descripción |
|---|---|---|
| `spring.servlet.multipart.max-file-size` | `10MB` | Tamaño máximo de una imagen subida |
| `kinich.media.processing.avif-quality` | `50` | Calidad de las representaciones AVIF |
| `kinich.media.processing.jpeg-quality` | `82` | Calidad del respaldo JPEG |
| `kinich.media.processing.timeout` | `60s` | Tiempo máximo de procesamiento por imagen |
| `kinich.media.processing.max-concurrent` | `2` | Procesamientos simultáneos |
| `kinich.brand.svg.max-bytes` | `524288` | Tamaño máximo del logotipo SVG (512 KB) |
| `kinich.brand.svg.max-elements` | `10000` | Elementos máximos del SVG |
| `kinich.brand.svg.max-depth` | `32` | Profundidad máxima del SVG |
| `kinich.login-rate-limit.max-failures-per-email` | `5` | Intentos fallidos por correo en la ventana |
| `kinich.login-rate-limit.max-failures-per-ip` | `20` | Intentos fallidos por IP en la ventana |
| `kinich.login-rate-limit.window` | `PT15M` | Ventana del limitador de intentos |

## Producción

- **HTTPS obligatorio.** Sirve frontend y API bajo el mismo dominio detrás de un proxy inverso con TLS, con
  `KINICH_FORWARD_HEADERS=native` y `KINICH_COOKIE_SECURE=true`.
- **Mismo origen.** La sesión viaja en una cookie `HttpOnly` con `SameSite=Strict`; el proxy debe enviar `/api` al
  backend y servir los estáticos de `frontend-web/dist`.
- **Secretos.** Genera un `KINICH_JWT_SECRET` propio por instalación y no lo compartas entre entornos.
- **Almacenamiento.** `KINICH_MEDIA_DIR` contiene los originales privados: no lo publiques en el servidor web y
  inclúyelo en las copias de seguridad junto con la base de datos.
- **Base de datos.** Usa un usuario con permisos solo sobre la base de Kinich.
- **Limitación de tráfico.** La API pública no limita peticiones por sí misma; configura esa protección en el proxy.
