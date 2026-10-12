# Manual de usuario

Kinich tiene dos partes:

- **Sitio público** (`/`): la portada del negocio, visible para cualquier visitante.
- **Panel de administración** (`/admin`): donde el estudio configura su identidad y prepara el contenido.

El administrador edita contenido, no el diseño: el orden de las secciones, su estructura y su presentación vienen
definidos por Kinich y se adaptan solos a móvil, tableta y escritorio, en tema claro y oscuro.

## Acceso al panel

1. Abre `/admin`. Si no hay una sesión activa, aparece la pantalla **Acceso al Estudio**.
2. Escribe el correo y la contraseña de tu cuenta y pulsa **Acceder al estudio**.
3. Para salir, usa **Cerrar sesión** en el panel.

La sesión dura unas horas (cuatro por defecto). Tras varios intentos fallidos seguidos, el acceso se bloquea
temporalmente durante unos minutos.

## Configuración (`/admin/configuracion`)

La configuración se aplica **al guardar**, sin borradores. Tiene tres áreas independientes, cada una con su propio
botón de guardado.

### Identidad

- **Nombre del negocio o estudio** (obligatorio) y **Lema** (opcional). Pulsa **Guardar identidad**.
- **Logotipo**: se sube como archivo SVG y se aplica al momento.
  - **Logotipo principal**: se usa en todos los temas. Si el SVG ya se adapta al tema claro y al oscuro
    (`prefers-color-scheme`), basta con este.
  - **Logotipo para fondo oscuro** (opcional): sustituye al principal cuando el visitante usa tema oscuro. Solo se
    puede subir cuando existe el principal.
  - **Quitar** retira una variante. El principal no se puede quitar mientras exista la variante para fondo oscuro.

La identidad se considera configurada cuando tiene nombre y logotipo principal; hasta entonces, el panel muestra un
aviso y el sitio usa el nombre en texto (o el provisional «Kinich»).

Por seguridad, Kinich revisa cada SVG antes de guardarlo y rechaza los que contienen scripts, enlaces, imágenes o
recursos externos, texto sin convertir a trazos u otro contenido activo. Exporta el logotipo con los textos
convertidos a contornos. El tamaño máximo es 512 KB.

### Contacto

Correo, WhatsApp, teléfono, ciudad o ubicación, dirección y horarios. Todos son opcionales; los que rellenes
aparecen en el pie del sitio. Los teléfonos se guardan en formato internacional (`+`, código de país y número), aunque
se escriban con espacios o guiones. Pulsa **Guardar contacto**.

### Redes sociales

- **Nueva red social**: elige la plataforma (Instagram, Facebook, TikTok, YouTube, Vimeo, Pinterest, X, LinkedIn u
  **Otra**) y escribe la dirección completa, que debe empezar por `https://`. Con «Otra», escribe también el
  **Nombre de la plataforma**.
- Cada red puede mostrarse u ocultarse en el sitio, cambiar de orden o eliminarse.

## Portada (`/admin/inicio`)

La portada se compone de seis secciones en un orden fijo:

| Sección | Contenido |
|---|---|
| **Hero** | Una imagen principal con un antetítulo |
| **Nota editorial** | Un bloque de texto |
| **Disciplinas** | Hasta 4 tarjetas con imagen y título |
| **Valores** | Bloques de texto, sin límite |
| **Selección destacada** | Tarjetas con imagen y título, sin límite (se recorren como carrusel) |
| **Cierre** | Una imagen y los textos de llamada a la acción |

### Borrador, previsualización y publicación

Cada sección se trabaja por separado:

1. **Editar**: los cambios se guardan como **borrador**. El sitio público no cambia todavía.
2. **Previsualizar**: muestra la sección tal como quedará publicada.
3. **Publicar sección**: el borrador de esa sección pasa al sitio público. Las demás secciones no se ven afectadas.

Las etiquetas **Cambios sin publicar**, **Se mostrará al publicar** y **Se ocultará al publicar** indican qué
cambiará en el sitio al publicar.

### Textos de la sección

En **Editar textos de la sección** se escriben los títulos y textos que acompañan a cada sección (por ejemplo, el
título de Disciplinas o el texto del Cierre). Se guardan en el borrador y se publican con la sección.

### Bloques de texto (Nota editorial y Valores)

Con **Nuevo** se añade un bloque; cada bloque se puede editar, mostrar u ocultar, ordenar con **Subir** y **Bajar** o
eliminar. Ocultar un bloque lo retira del sitio al publicar, sin borrarlo.

### Elementos con imagen

Con **Nuevo elemento** se añade una tarjeta o imagen:

1. **Elegir imagen**: selecciona una imagen ya subida o usa **Subir imagen**.
2. Completa los textos que admite la sección y, si la sección lo permite, una categoría (puedes crear una nueva).
3. Ajusta el **Encuadre**:
   - En Disciplinas y Selección destacada, la imagen se recorta en proporción 4:5: usa **Acercar** y las posiciones
     horizontal y vertical, con vista previa.
   - En Hero y Cierre se elige un **punto focal**: la parte de la imagen que siempre debe quedar visible.
4. Guarda el elemento en el borrador.

El encuadre no modifica la imagen original: puedes cambiarlo cuando quieras.

### Imágenes

- Formatos admitidos: JPEG, PNG, WebP y AVIF; hasta 10 MB y 8000 px por lado.
- No se admiten imágenes animadas ni con una rotación pendiente: expórtalas ya giradas.
- Kinich guarda el original de forma privada y genera versiones optimizadas para la web. Los datos de cámara y de
  ubicación (EXIF, GPS) nunca se publican.
- Sube una exportación pensada para web, no el archivo de máxima resolución de la cámara.

## Ediciones simultáneas

Si dos personas o dos pestañas editan lo mismo a la vez, la segunda en guardar recibe el aviso **El contenido
cambió**: el panel recarga los datos actuales para que no se pierdan cambios ajenos. Vuelve a aplicar tu modificación
sobre la versión recargada.
