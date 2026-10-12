# Instalación

Esta guía instala Kinich en un equipo de desarrollo: base de datos, backend y frontend. Para los parámetros de
configuración y las recomendaciones de producción, consulta [configuracion.md](configuracion.md).

## Requisitos

| Componente | Versión |
|---|---|
| Java (JDK) | 21 |
| Maven | 3.9 o superior |
| PostgreSQL | 17 |
| Node.js | 24 (ver `frontend-web/.nvmrc`) |
| libvips | 8.13 o superior, con soporte AVIF |

libvips procesa las imágenes que se suben al panel. El backend comprueba al arrancar que está instalado y que puede
generar AVIF; si no es así, no arranca.

```bash
# macOS (Homebrew)
brew install vips

# Debian / Ubuntu
sudo apt install libvips-tools
```

Comprueba la instalación con `vips --version`.

## 1. Base de datos

Crea un usuario y dos bases de datos: una para trabajar y otra para las pruebas de integración.

```sql
CREATE USER kinich WITH PASSWORD 'elige-una-contraseña';
CREATE DATABASE kinich_dev OWNER kinich;
CREATE DATABASE kinich_test OWNER kinich;
```

No hace falta crear tablas: el backend aplica las migraciones de `database/migrations` con Flyway al arrancar.

## 2. Configuración del backend

Copia la plantilla y completa los valores:

```bash
cp backend/.env.example backend/.env
```

Como mínimo:

| Variable | Valor |
|---|---|
| `KINICH_DB_PASSWORD` | La contraseña del usuario `kinich` |
| `KINICH_JWT_SECRET` | Una clave aleatoria: `openssl rand -base64 48` |
| `KINICH_MEDIA_DIR` | Ruta absoluta de un directorio existente y escribible, fuera del repositorio |
| `KINICH_ADMIN_EMAIL`, `KINICH_ADMIN_PASSWORD`, `KINICH_ADMIN_FULL_NAME` | La cuenta de administración inicial |

`backend/.env` está excluido de Git: nunca lo subas al repositorio.

La cuenta de administración se crea solo si la tabla de usuarios está vacía (el primer arranque). Su contraseña debe
tener al menos 12 caracteres. Después del primer arranque puedes borrar `KINICH_ADMIN_PASSWORD` del archivo.

## 3. Backend

```bash
cd backend
mvn spring-boot:run
```

La API queda en `http://localhost:8080`.

## 4. Frontend

```bash
cd frontend-web
npm install
npm run dev
```

La aplicación queda en `http://localhost:5173`. En desarrollo, Vite redirige `/api` al backend, así que ambos
comparten origen y la cookie de sesión funciona sin configuración adicional.

## 5. Primer acceso

1. Abre `http://localhost:5173/admin` e inicia sesión con la cuenta de administración inicial.
2. En **Configuración** (`/admin/configuracion`) define el nombre del negocio, el logotipo, el contacto y las redes.
3. En **Inicio** (`/admin/inicio`) prepara el contenido de la portada y publícalo.

Mientras la identidad no esté configurada, el sitio muestra el nombre provisional «Kinich». Consulta
[manual-de-usuario.md](manual-de-usuario.md) para el uso del panel.

## Pruebas

```bash
cd backend && mvn test           # pruebas unitarias y de integración contra kinich_test
cd frontend-web && npm test      # pruebas del frontend
```

Las pruebas de integración del backend usan la base `kinich_test` (variable `KINICH_TEST_DB_URL`) y directorios
temporales propios para las imágenes. Necesitan libvips instalado.

Validaciones adicionales del frontend:

```bash
npm run lint       # oxlint
npm run build      # comprobación de tipos (tsc) y compilación de producción
```

## Compilación para producción

```bash
cd backend && mvn package                # genera el JAR en backend/target/
cd frontend-web && npm run build         # genera los estáticos en frontend-web/dist/
```

El frontend compilado es un conjunto de archivos estáticos: sírvelos detrás del mismo dominio que la API (por
ejemplo, con un proxy inverso que envíe `/api` al backend). Revisa la sección de producción de
[configuracion.md](configuracion.md).
