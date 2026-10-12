package com.kinich.media.processing;

import com.kinich.media.image.ImageInspection;
import com.kinich.media.image.ImageInspector;
import com.kinich.media.image.InvalidImageException;
import com.kinich.media.image.MediaFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adaptador libvips por línea de comandos (Java 21: la integración FFM exige Java 22+). Mitigaciones:
 * solo recibe originales ya validados; el cargador se fuerza según el formato detectado (nunca autodetección sobre
 * el archivo subido); VIPS_BLOCK_UNTRUSTED=1 bloquea SVG, PDF, ImageMagick y demás cargadores no confiables;
 * argumentos fijos sin shell, rutas relativas a un directorio de trabajo privado (0700) que siempre se borra;
 * tiempo límite y concurrencia acotada. La salida de vips se descarta: puede contener rutas.
 */
@Slf4j(topic = "kinich.media")
@Component
public class VipsCliRenditionProcessor implements ImageRenditionProcessor {

    private static final String ORIGINAL = "original";
    // Intermedio sin compresión en un formato de confianza: el cargador explícito ya hizo la decodificación segura.
    private static final String DECODED = "decoded.tif";
    private static final String ALPHA = "alpha.tif";
    private static final String OUTPUT = "output.txt";
    // La salida numérica de getpoint/min/max es corta; más es una anomalía.
    private static final int MAX_OUTPUT_BYTES = 256;

    private final MediaProcessingProperties properties;
    private final ImageInspector inspector;
    private final Semaphore slots;

    public VipsCliRenditionProcessor(MediaProcessingProperties properties, ImageInspector inspector) {
        this.properties = properties;
        this.inspector = inspector;
        this.slots = new Semaphore(properties.maxConcurrent());
        verifyInstallation();
    }

    @Override
    public List<RenditionFile> render(byte[] original, MediaFormat format, int originalWidth) {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaProcessingException();
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory(properties.workDir(), "vips-",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            Files.write(dir.resolve(ORIGINAL), original);
            if (run(dir, properties.command(), loaderFor(format), ORIGINAL, DECODED + "[compression=none,keep=icc]",
                    "--fail-on", "error") != 0) {
                throw InvalidImageException.malformed();
            }
            List<RenditionFile> files = new ArrayList<>();
            for (MediaFormat target : RenditionPlan.formatsFor(hasTransparency(dir))) {
                for (int width : RenditionPlan.widthsFor(originalWidth)) {
                    String output = "w" + width + extension(target);
                    if (run(dir, properties.command(), "thumbnail", DECODED, output + saveOptions(target),
                            String.valueOf(width), "--size", "down", "--export-profile", "srgb") != 0) {
                        throw new MediaProcessingException();
                    }
                    byte[] content = Files.readAllBytes(dir.resolve(output));
                    ImageInspection inspection = inspector.inspect(content);
                    if (inspection.format() != target) {
                        throw new MediaProcessingException();
                    }
                    files.add(new RenditionFile(target, inspection.width(), inspection.height(), content));
                }
            }
            return files;
        } catch (IOException e) {
            throw new MediaProcessingException();
        } finally {
            deleteQuietly(dir);
            slots.release();
        }
    }

    /**
     * Transparencia real, no solo canal alfa declarado: un PNG RGBA totalmente opaco conserva el respaldo JPEG.
     * Tras los cargadores permitidos (JPEG, PNG, WebP, AVIF) la imagen tiene 1–4 bandas; con 2 (gris + alfa) o 4
     * (RGB + alfa) la última es el alfa. Es transparente si algún valor queda por debajo del opaco (255, o 65535
     * en imágenes de 16 bits).
     */
    private boolean hasTransparency(Path dir) throws IOException {
        int bands = numbers(runForOutput(dir, properties.command(), "getpoint", DECODED, "0", "0")).length;
        if (bands != 2 && bands != 4) {
            return false;
        }
        if (run(dir, properties.command(), "extract_band", DECODED, ALPHA, String.valueOf(bands - 1)) != 0) {
            throw new MediaProcessingException();
        }
        double min = single(runForOutput(dir, properties.command(), "min", ALPHA));
        double max = single(runForOutput(dir, properties.command(), "max", ALPHA));
        return min < (max > 255 ? 65535 : 255);
    }

    private String saveOptions(MediaFormat target) {
        return switch (target) {
            case AVIF -> "[Q=" + properties.avifQuality() + ",keep=none]";
            // Solo imágenes opacas: no hay transparencia que perder al aplanar (el fondo blanco es una salvaguarda).
            case JPEG -> "[Q=" + properties.jpegQuality() + ",keep=none,optimize-coding,interlace,background=255]";
            // Imágenes con transparencia: sin pérdida y conservando el alfa.
            case PNG -> "[compression=9,keep=none]";
            default -> throw new IllegalArgumentException("formato de salida no previsto");
        };
    }

    private static String extension(MediaFormat target) {
        return switch (target) {
            case AVIF -> ".avif";
            case PNG -> ".png";
            default -> ".jpg";
        };
    }

    /** Valores numéricos de una salida de vips; cualquier otra cosa (p. ej. una ruta) es un fallo. */
    private static double[] numbers(String output) {
        String trimmed = output.strip();
        if (!trimmed.matches("-?\\d+(\\.\\d+)?( -?\\d+(\\.\\d+)?)*")) {
            throw new MediaProcessingException();
        }
        return Arrays.stream(trimmed.split(" ")).mapToDouble(Double::parseDouble).toArray();
    }

    private static double single(String output) {
        double[] values = numbers(output);
        if (values.length != 1) {
            throw new MediaProcessingException();
        }
        return values[0];
    }

    private static String loaderFor(MediaFormat format) {
        return switch (format) {
            case JPEG -> "jpegload";
            case PNG -> "pngload";
            case WEBP -> "webpload";
            case AVIF -> "heifload";
        };
    }

    /**
     * Como {@link #run}, pero devuelve la salida estándar de una operación que imprime números. La salida va a un
     * archivo del directorio de trabajo (no a una tubería) para que el tiempo límite se aplique siempre.
     */
    private String runForOutput(Path dir, String... command) throws IOException {
        Path output = dir.resolve(OUTPUT);
        if (run(dir, output, command) != 0 || Files.size(output) > MAX_OUTPUT_BYTES) {
            throw new MediaProcessingException();
        }
        return Files.readString(output, StandardCharsets.US_ASCII);
    }

    private int run(Path dir, String... command) throws IOException {
        return run(dir, null, command);
    }

    private int run(Path dir, Path output, String... command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(dir == null ? null : dir.toFile())
                .redirectOutput(output == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(output.toFile()))
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("VIPS_BLOCK_UNTRUSTED", "1");
        Process process = builder.start();
        try {
            if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                log.warn("Procesamiento de imagen cancelado por tiempo límite");
                throw new MediaProcessingException();
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new MediaProcessingException();
        }
    }

    /** Arranque fallido si libvips no está instalado o no puede escribir AVIF. */
    private void verifyInstallation() {
        try {
            if (run(null, properties.command(), "--version") != 0
                    || run(null, properties.command(), "-l", "heifsave") != 0) {
                throw new IllegalStateException("libvips con soporte AVIF no está disponible (kinich.media.processing.command)");
            }
        } catch (IOException | MediaProcessingException e) {
            throw new IllegalStateException("libvips no está disponible (kinich.media.processing.command)");
        }
        if (!Files.isDirectory(properties.workDir()) || !Files.isWritable(properties.workDir())) {
            throw new IllegalStateException("kinich.media.processing.work-dir no existe o no se puede escribir");
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Residuo del directorio privado de trabajo; no contiene nada público.
                }
            });
        } catch (IOException ignored) {
            // idem
        }
    }
}
