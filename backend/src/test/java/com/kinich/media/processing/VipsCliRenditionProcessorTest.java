package com.kinich.media.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.media.image.ImageFixtures;
import com.kinich.media.image.ImageInspector;
import com.kinich.media.image.InvalidImageException;
import com.kinich.media.image.MediaFormat;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Usa la CLI real de libvips (requisito del entorno, B3) con imágenes sintéticas. */
class VipsCliRenditionProcessorTest {

    @TempDir
    Path work;

    private VipsCliRenditionProcessor processor() {
        return new VipsCliRenditionProcessor(new MediaProcessingProperties("vips", work, Duration.ofSeconds(60), 50, 82, 2),
                new ImageInspector());
    }

    private static byte[] png(int width, int height, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(200, 120, 60, alpha ? 0 : 255));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private long leftovers() throws Exception {
        try (var entries = Files.list(work)) {
            return entries.count();
        }
    }

    @Test
    void generatesAvifAndJpegAtEveryCandidateWidthWithoutMetadata() throws Exception {
        byte[] original = ImageFixtures.withJpegSegment(ImageFixtures.jpeg(3000, 2000), 0xE1,
                ImageFixtures.exifApp1Payload(1, true));
        byte[] copy = original.clone();

        List<RenditionFile> files = processor().render(original, MediaFormat.JPEG, 3000);

        assertThat(files).hasSize(10);
        assertThat(files).extracting(RenditionFile::format).containsOnly(MediaFormat.AVIF, MediaFormat.JPEG);
        for (MediaFormat format : RenditionPlan.formatsFor(false)) {
            assertThat(files.stream().filter(f -> f.format() == format)).extracting(RenditionFile::width)
                    .containsExactly(480, 768, 1280, 1920, 2560);
        }
        for (RenditionFile file : files) {
            assertThat(file.height()).isEqualTo(Math.round(file.width() * 2000 / 3000f));
            assertThat(new ImageInspector().inspect(file.content()).format()).isEqualTo(file.format());
            assertThat(ImageFixtures.contains(file.content(), ImageFixtures.GPS_MARKER)).isFalse();
            assertThat(ImageFixtures.contains(file.content(), "Exif".getBytes(StandardCharsets.US_ASCII))).isFalse();
        }
        assertThat(original).isEqualTo(copy); // el original no se toca
        assertThat(leftovers()).isZero();       // espacio de trabajo borrado
    }

    @Test
    void aNarrowOriginalGetsOneRenditionPerFormatAtItsOwnWidth() throws Exception {
        List<RenditionFile> files = processor().render(png(300, 200, false), MediaFormat.PNG, 300);
        assertThat(files).extracting(RenditionFile::width).containsExactly(300, 300);
    }

    @Test
    void readsWebpAndAvifOriginals() {
        assertThat(processor().render(ImageFixtures.resource("lossless.webp"), MediaFormat.WEBP, 16)).hasSize(2);
        assertThat(processor().render(ImageFixtures.resource("plain.avif"), MediaFormat.AVIF, 16)).hasSize(2);
    }

    @Test
    void transparentImagesKeepTheirTransparencyInAPngFallbackInsteadOfJpeg() throws Exception {
        List<RenditionFile> files = processor().render(png(600, 400, true), MediaFormat.PNG, 600);

        assertThat(files).extracting(RenditionFile::format).containsExactlyInAnyOrder(MediaFormat.AVIF, MediaFormat.PNG);
        RenditionFile fallback = files.stream().filter(f -> f.format() == MediaFormat.PNG).findFirst().orElseThrow();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(fallback.content()));
        assertThat(decoded.getColorModel().hasAlpha()).isTrue();
        assertThat(decoded.getRGB(10, 10) >>> 24).isZero(); // sigue transparente: no se aplana sobre blanco
        assertThat(leftovers()).isZero();
    }

    @Test
    void aSingleTransparentPixelIsEnoughForThePngFallback() throws Exception {
        BufferedImage image = new BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(new Color(20, 40, 60, 255));
        g.fillRect(0, 0, 500, 300);
        g.dispose();
        image.setRGB(499, 299, 0x00FFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        assertThat(processor().render(out.toByteArray(), MediaFormat.PNG, 500)).extracting(RenditionFile::format)
                .containsExactlyInAnyOrder(MediaFormat.AVIF, MediaFormat.PNG);
    }

    @Test
    void anAlphaChannelThatIsFullyOpaqueKeepsTheJpegFallback() throws Exception {
        BufferedImage image = new BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(new Color(20, 40, 60, 255));
        g.fillRect(0, 0, 500, 300);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        assertThat(processor().render(out.toByteArray(), MediaFormat.PNG, 500)).extracting(RenditionFile::format)
                .containsExactlyInAnyOrder(MediaFormat.AVIF, MediaFormat.JPEG);
    }

    @Test
    void theLoaderIsForcedByTheDetectedFormat() throws Exception {
        // Un PNG declarado como JPEG no se decodifica: jpegload no autodetecta otro formato.
        assertThatThrownBy(() -> processor().render(png(10, 10, false), MediaFormat.JPEG, 10))
                .isInstanceOf(InvalidImageException.class);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"/>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> processor().render(svg, MediaFormat.PNG, 10)).isInstanceOf(InvalidImageException.class);
        assertThat(leftovers()).isZero();
    }

    @Test
    void errorsNeverMentionTheWorkDirectory() {
        assertThatThrownBy(() -> processor().render(Arrays.copyOf(ImageFixtures.jpeg(50, 50), 40), MediaFormat.JPEG, 50))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(work.toString()))
                .satisfies(e -> assertThat(e.getCause()).isNull());
    }

    @Test
    void failsFastWhenLibvipsIsMissing() {
        assertThatThrownBy(() -> new VipsCliRenditionProcessor(
                new MediaProcessingProperties("kinich-vips-inexistente", work, Duration.ofSeconds(5), 50, 82, 1),
                new ImageInspector())).isInstanceOf(IllegalStateException.class);
    }
}
