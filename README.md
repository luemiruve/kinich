# Kinich

Plataforma web open source para negocios de fotografía y video: un sitio público administrable y un panel de
administración para gestionarlo.

Kinich no está ligado a ningún negocio. Cada instalación configura su propia identidad (nombre, lema, logotipo,
contacto y redes sociales) y prepara el contenido de su sitio desde el panel, sin modificar el código.

## Funcionalidades

- **Portada administrable** con secciones fijas (Hero, Nota editorial, Disciplinas, Valores, Selección destacada y
  Cierre), editadas como borrador, previsualizadas y publicadas sección por sección.
- **Portafolio**: proyectos con portada, galería, categorías y orden, publicados uno a uno y mostrados en un listado
  con filtros y una página de detalle.
- **Imágenes responsivas**: el original se conserva de forma privada y se generan representaciones optimizadas (AVIF
  con respaldo JPEG o PNG) en varios anchos, sin metadatos de cámara ni de ubicación.
- **Encuadre por elemento** (recorte o punto focal) sin modificar nunca el original.
- **Identidad configurable**: nombre, lema, logotipo SVG con variante para fondo oscuro, contacto y redes sociales.
- **Panel seguro**: sesión en cookie `HttpOnly`, protección CSRF, limitación de intentos de acceso y control de
  ediciones simultáneas.
- **Diseño adaptable** a móvil, tableta y escritorio, con tema claro y oscuro.

## Stack

| Parte | Tecnologías |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Flyway, Maven, libvips |
| Base de datos | PostgreSQL 17 |
| Frontend | React, TypeScript, Vite, Tailwind CSS, React Router |
| Pruebas | JUnit 5, MockMvc, Vitest, Testing Library |

## Estructura

```text
kinich/
├── backend/        API REST (Spring Boot)
├── frontend-web/   Sitio público y panel (React)
├── database/       Migraciones de PostgreSQL (Flyway)
└── docs/           Documentación
```

## Inicio rápido

Requisitos: Java 21, Maven, PostgreSQL 17, Node.js 24 y libvips con soporte AVIF.

```bash
# 1. Base de datos: crea las bases kinich_dev y kinich_test (ver docs/instalacion.md)

# 2. Configuración del backend
cp backend/.env.example backend/.env    # completa los valores

# 3. Backend (http://localhost:8080)
cd backend && mvn spring-boot:run

# 4. Frontend (http://localhost:5173)
cd frontend-web && npm install && npm run dev
```

Entra en `http://localhost:5173/admin` con la cuenta de administración inicial definida en `backend/.env`.

## Pruebas

```bash
cd backend && mvn test
cd frontend-web && npm test && npm run lint && npm run build
```

## Documentación

- [Instalación](docs/instalacion.md)
- [Configuración](docs/configuracion.md)
- [Manual de usuario](docs/manual-de-usuario.md)
- [Arquitectura](docs/arquitectura.md)

## Seguridad

Los secretos se configuran mediante variables de entorno y nunca se versionan: `backend/.env` está excluido del
repositorio y `backend/.env.example` solo contiene valores de ejemplo. Para desplegar en producción, revisa las
recomendaciones de [configuración](docs/configuracion.md#producción).

## Licencia

[MIT](LICENSE)
