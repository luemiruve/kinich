# Arquitectura

## Visión general

```text
Navegador ──► frontend-web (React) ──► API REST /api ──► backend (Spring Boot) ──► PostgreSQL
                                                                 │
                                                                 └──► almacenamiento de imágenes (KINICH_MEDIA_DIR)
```

| Carpeta | Contenido |
|---|---|
| `backend/` | API REST en Java 21 y Spring Boot 3 (Maven) |
| `frontend-web/` | Sitio público y panel en React, TypeScript, Vite y Tailwind CSS |
| `database/migrations/` | Migraciones de PostgreSQL (Flyway), empaquetadas en el backend |

## Backend

Organizado por módulos, cada uno con capas `Controller → Service → Repository`. Los controladores solo traducen
HTTP ↔ DTO; las reglas, validaciones y transacciones viven en los servicios.

| Paquete | Responsabilidad |
|---|---|
| `auth` | Inicio y cierre de sesión, JWT en cookie, limitador de intentos |
| `user` | Usuarios y alta de la cuenta de administración inicial |
| `content` | Secciones de la portada: textos, elementos con imagen, cabeceras, categorías, borrador y publicación |
| `portfolio` | Portafolio: proyectos con portada y galería, borrador por proyecto, instantánea publicada, orden y API pública |
| `media` | Imágenes: validación, procesamiento con libvips, almacenamiento y entrega |
| `settings` | Configuración de la instalación: identidad, contacto y redes sociales |
| `brand` | Logotipo SVG: sanitización, almacenamiento y entrega |
| `config` | Seguridad, CORS y configuración general |
| `shared` | Auditoría, errores (ProblemDetail) y limpieza de textos comunes |

### Persistencia

- Claves primarias UUID; columnas de auditoría (`created_by`, `created_at`, `updated_by`, `updated_at`) rellenadas
  desde el usuario autenticado, nunca desde el cliente.
- Control de concurrencia optimista: cada recurso editable tiene `version` (o `revision`, en las secciones). Guardar
  con una versión desactualizada responde `409`.
- El esquema solo cambia mediante migraciones; Hibernate se limita a validarlo.

### Contenido: borrador y publicación

Cada sección de la portada tiene un borrador y una instantánea publicada. Publicar copia el borrador de **una**
sección a la instantánea en una sola transacción; la API pública solo lee instantáneas. La configuración de la
instalación (`settings`), en cambio, se aplica al guardar.

### Portafolio: borrador y publicación

Cada proyecto es un agregado (datos, portada con encuadre 4:5 y galería de 1 a 60 imágenes) con su propio borrador y su
instantánea publicada; se publica **por proyecto**. El orden también vive en el borrador y solo pasa al sitio con
«Publicar orden». Retirar un proyecto (ocultarlo) se aplica al publicarlo; eliminarlo borra borrador e instantánea de
inmediato. Los textos de cabecera y cierre de la página se gestionan como secciones editoriales (`PAGE_HEADER` y
`CLOSING_CTA` de la página `PORTFOLIO`). Las publicaciones se serializan con un bloqueo de tabla; guardar o publicar con
una versión desactualizada responde `409`. El esquema llega hasta la migración V12 (V11 amplía el catálogo de páginas,
V12 crea las tablas de proyectos).

### Errores

Las respuestas de error siguen ProblemDetail (RFC 9457), sin trazas ni detalles internos. El campo `code` usa un
conjunto cerrado: `VALIDATION_FAILED` (con `errors` por campo), `VERSION_CONFLICT` y `MAX_ITEMS_REACHED`.

## Seguridad

- **Sesión**: JWT firmado con HMAC-SHA256 en una cookie `HttpOnly`, `Secure` y `SameSite=Strict`. El token nunca se
  acepta por cabecera ni por parámetro de URL.
- **CSRF**: toda escritura autenticada exige la cabecera `X-XSRF-TOKEN` igual a la cookie `XSRF-TOKEN`. El token es
  estable durante la sesión del navegador y se renueva al iniciar y al cerrar sesión.
- **Contraseñas**: BCrypt. El limitador de intentos bloquea temporalmente por correo y por IP.
- **API pública** (`/api/public/**`): cadena de seguridad propia, solo `GET`, sin cookies ni sesión.
- **CORS**: solo los orígenes configurados explícitamente.
- **Cabeceras**: CSP restrictiva, `X-Frame-Options: DENY`, `nosniff` y `Referrer-Policy: no-referrer`.

## Imágenes

```text
subida → validación del formato real → original privado → libvips → representaciones web → publicación
```

- Se valida el contenido real del archivo (nunca su nombre ni el `Content-Type` del cliente): JPEG, PNG, WebP o AVIF,
  hasta 10 MB y 8000 px por lado, sin animación ni rotación pendiente. Se eliminan metadatos (EXIF, XMP, GPS).
- El original se conserva de forma privada y nunca se sirve.
- libvips genera representaciones en cinco anchos (480, 768, 1280, 1920 y 2560 px, sin ampliar el original) en AVIF y
  un respaldo JPEG, o PNG si la imagen tiene transparencia real.
- El frontend las entrega con `<picture>`, `srcset` y `sizes`.
- El encuadre (recorte 4:5 o punto focal) pertenece a cada elemento de contenido y se aplica al mostrar la imagen:
  nunca modifica el original.
- El almacenamiento está detrás de una interfaz (`MediaStorage`); la implementación incluida usa el sistema de
  archivos local (`KINICH_MEDIA_DIR`).
- La API pública solo sirve representaciones web (`WEB`) de imágenes que estén en un elemento publicado de Inicio, en la
  portada de un proyecto publicado o en su galería publicada. Un borrador nunca hace público un archivo.

## Logotipo SVG

El logotipo es un recurso de marca independiente de la biblioteca de imágenes:

- Variante principal (todos los temas) y variante opcional para fondo oscuro.
- Antes de guardarse, el SVG se valida contra una lista cerrada de elementos y atributos, se eliminan metadatos y
  comentarios y se reescribe: nunca se guarda el archivo recibido.
- Se sirve en `/api/public/brand-assets/{id}` solo mientras la configuración lo referencia, con caché inmutable,
  `ETag` (SHA-256), CSP con `sandbox` y `nosniff`. Cada reemplazo genera un recurso y una URL nuevos.
- El frontend lo muestra como imagen (`<img>` y `<source media="(prefers-color-scheme: dark)">`), nunca insertado en
  el HTML.

## Frontend

| Carpeta (`src/`) | Contenido |
|---|---|
| `api/` | Cliente HTTP y llamadas a la API (gestiona el token CSRF) |
| `pages/` | Páginas: portada, acceso, panel, edición de Inicio, configuración |
| `portfolio/` | Páginas públicas del portafolio: listado con filtros y detalle de proyecto |
| `home/` | Secciones de la portada |
| `admin/` | Componentes del panel (formularios, selector de imágenes, encuadre, configuración) |
| `admin/portfolio/` | Lista, orden y editor de proyectos del portafolio |
| `components/` | Componentes reutilizables (layout, editoriales, formulario, logotipo) |
| `session/`, `routes/`, `site/` | Sesión del panel, rutas protegidas e identidad pública |

Rutas públicas: `/` (portada), `/portafolio` (listado, filtro con `?categoria=`) y `/portafolio/:slug` (detalle). La
cabecera del sitio ofrece la navegación **Inicio** y **Portafolio**, con menú desplegable en móvil.

Rutas del panel: `/admin/login`, `/admin`, `/admin/inicio`, `/admin/configuracion`, `/admin/portafolio`,
`/admin/portafolio/nuevo` y `/admin/portafolio/:id`.

## API

Todas las rutas están bajo `/api`. Las de administración requieren sesión y, si escriben, la cabecera CSRF.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/auth/login`, `/auth/logout` | Iniciar y cerrar sesión |
| `GET` | `/auth/me` | Usuario de la sesión |
| `GET` | `/public/site` | Identidad, contacto y redes visibles |
| `GET` | `/public/pages/home` | Portada publicada |
| `GET` | `/public/pages/portfolio` | Portafolio publicado: cabecera, cierre, tarjetas en orden y categorías presentes |
| `GET` | `/public/portfolio/projects/{slug}` | Detalle publicado de un proyecto (404 idéntico si es borrador, retirado o no existe) |
| `GET` | `/public/media/files/{id}`, `/public/brand-assets/{id}` | Imágenes y logotipo publicados |
| `GET` | `/admin/pages/home` | Borrador de la portada |
| `POST`, `PUT`, `PATCH`, `DELETE` | `/admin/pages/home/sections/{sección}/texts…` | Bloques de texto (crear, editar, visibilidad, orden, eliminar) |
| `POST`, `PUT`, `PATCH`, `DELETE` | `/admin/pages/home/sections/{sección}/items…` | Elementos con imagen |
| `PUT` | `/admin/pages/home/sections/{sección}/heading` | Textos de cabecera de la sección |
| `PUT` | `/admin/pages/home/sections/{sección}/publication` | Publicar la sección |
| `GET`, `POST` | `/admin/media` | Biblioteca de imágenes y subida |
| `GET`, `POST` | `/admin/content-categories` | Categorías |
| `GET` | `/admin/settings` | Configuración completa |
| `PUT` | `/admin/settings/identity`, `/admin/settings/contact` | Identidad y contacto |
| `PUT`, `DELETE` | `/admin/settings/logos/{primary\|on-dark}` | Subir o retirar el logotipo |
| `POST`, `PUT`, `DELETE` | `/admin/settings/social-links…` | Redes sociales y su orden |
| `GET`, `POST` | `/admin/portfolio/projects` | Lista de proyectos (con `orderModified`) y alta |
| `GET`, `PUT`, `DELETE` | `/admin/portfolio/projects/{id}` | Leer, guardar el borrador completo y eliminar (inmediato) |
| `PATCH` | `/admin/portfolio/projects/{id}/visibility` | Retirar (efectivo al publicar el proyecto) o mostrar (cancela una retirada pendiente; en un proyecto nunca publicado, efectivo al publicarlo) |
| `PUT` | `/admin/portfolio/projects/{id}/publication` | Publicar el proyecto |
| `PUT` | `/admin/portfolio/projects/order` | Guardar el orden en el borrador |
| `PUT` | `/admin/portfolio/order/publication` | Publicar el orden |
| `GET` | `/admin/pages/portfolio` | Cabecera y cierre de la página, en borrador |
| `PUT` | `/admin/pages/portfolio/sections/{sección}/heading`, `/publication` | Editar y publicar esos textos |
