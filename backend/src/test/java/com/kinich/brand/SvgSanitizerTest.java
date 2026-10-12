package com.kinich.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Sanitizador del logotipo SVG: lista cerrada, rechazo de contenido activo o externo y salida reescrita. */
class SvgSanitizerTest {

    private static final String NS = "xmlns=\"http://www.w3.org/2000/svg\"";
    private static final SvgLimits LIMITS = new SvgLimits(524_288, 10_000, 32);

    private final SvgSanitizer sanitizer = new SvgSanitizer(LIMITS);

    private static String svg(String body) {
        return "<svg " + NS + " viewBox=\"0 0 120 40\">" + body + "</svg>";
    }

    private SanitizedSvg sanitize(String svg) {
        return sanitizer.sanitize(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(SanitizedSvg result) {
        return new String(result.content(), StandardCharsets.UTF_8);
    }

    private void rejected(String svg, String reason) {
        assertThatThrownBy(() -> sanitize(svg)).isInstanceOf(InvalidSvgException.class).hasMessageContaining(reason);
    }

    @Test
    void aSimpleLogoIsAcceptedWithItsViewBoxProportions() {
        SanitizedSvg result = sanitize(svg("<g fill=\"#111\"><path d=\"M0 0h120v40H0z\"/><circle cx=\"20\" cy=\"20\" r=\"8\"/></g>"));

        assertThat(result.viewBoxWidth()).isEqualTo(120);
        assertThat(result.viewBoxHeight()).isEqualTo(40);
        assertThat(text(result)).startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"")
                .contains("viewBox=\"0 0 120 40\"").contains("<path d=\"M0 0h120v40H0z\"").contains("<circle");
    }

    @Test
    void decimalAndCommaSeparatedViewBoxesAreAccepted() {
        SanitizedSvg result = sanitize("<svg " + NS + " viewBox=\"-1.5,0,300.25, 80\"><rect width=\"10\" height=\"10\"/></svg>");

        assertThat(result.viewBoxWidth()).isEqualTo(300.25);
        assertThat(result.viewBoxHeight()).isEqualTo(80);
    }

    @Test
    void aSingleSvgMayCarryOneDefinitionPerThemeWithPrefersColorScheme() {
        String css = ".mark{fill:#111}@media (prefers-color-scheme: dark){.mark{fill:#f5f5f5}}"
                + "@media (prefers-color-scheme:light){.mark{fill:#111}}";
        SanitizedSvg result = sanitize(svg("<style>" + css + "</style><path class=\"mark\" d=\"M0 0h10v10z\"/>"));

        assertThat(text(result)).contains("@media (prefers-color-scheme: dark)").contains("class=\"mark\"");
    }

    @Test
    void gradientsClipPathsMasksAndInternalReferencesAreAccepted() {
        String body = "<defs><linearGradient id=\"g1\"><stop offset=\"0\" stop-color=\"#000\"/>"
                + "<stop offset=\"1\" stop-color=\"#fff\"/></linearGradient><clipPath id=\"c\"><rect width=\"5\" height=\"5\"/>"
                + "</clipPath><mask id=\"m\"><rect width=\"9\" height=\"9\" fill=\"#fff\"/></mask>"
                + "<path id=\"p\" d=\"M0 0h4v4z\"/></defs>"
                + "<use href=\"#p\"/><use xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:href=\"#p\"/>"
                + "<rect fill=\"url(#g1)\" clip-path=\"url( '#c' )\" mask=\"url(#m)\" width=\"9\" height=\"9\"/>";

        String out = text(sanitize(svg(body)));

        assertThat(out).contains("fill=\"url(#g1)\"").contains("<use href=\"#p\"").doesNotContain("xlink");
    }

    @Test
    void editorMetadataCommentsAndDataAttributesAreRemoved() {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!-- Generator: editor -->"
                + "<svg " + NS + " xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\""
                + " xmlns:sodipodi=\"http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd\""
                + " inkscape:version=\"1.3\" viewBox=\"0 0 10 10\" xml:space=\"preserve\">"
                + "<metadata><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"/></metadata>"
                + "<sodipodi:namedview id=\"nv\"/><title>Marca</title>"
                + "<path data-name=\"Capa 1\" inkscape:label=\"x\" d=\"M0 0h1v1z\"/></svg>";

        String out = text(sanitize(input));

        assertThat(out).doesNotContain("inkscape").doesNotContain("sodipodi").doesNotContain("metadata")
                .doesNotContain("Generator").doesNotContain("data-name").doesNotContain("xml:space")
                .contains("<title>Marca</title>");
    }

    @Test
    void theStoredSvgIsTheRewrittenOneNotTheOriginalBytes() {
        String input = "<svg " + NS + "   viewBox='0 0 10 10'  ><!-- x --><path   d='M0 0h1v1z' /></svg>";

        assertThat(text(sanitize(input))).isEqualTo(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><path d=\"M0 0h1v1z\"></path></svg>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"script", "foreignObject", "image", "a", "text", "tspan", "textPath", "animate",
            "animateTransform", "animateMotion", "set", "iframe", "filter"})
    void elementsOutsideTheClosedListAreRejected(String element) {
        rejected(svg("<" + element + "/>"), "elemento no permitido: " + element);
    }

    @Test
    void aScriptInsideAllowedContentIsRejected() {
        rejected(svg("<g><script>alert(1)</script></g>"), "elemento no permitido: script");
    }

    @Test
    void eventHandlersAreRejected() {
        rejected(svg("<rect onload=\"alert(1)\" width=\"1\" height=\"1\"/>"), "eventos");
        rejected("<svg " + NS + " viewBox=\"0 0 1 1\" ONCLICK=\"x()\"/>", "eventos");
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", " JaVaScRiPt:x", "java\tscript:x", "data:image/png;base64,AA"})
    void javascriptAndDataUrlsAreRejected(String value) {
        rejected(svg("<use href=\"" + value + "\"/>"), "");
        rejected(svg("<rect fill=\"" + value + "\" width=\"1\" height=\"1\"/>"), "");
    }

    @Test
    void externalReferencesAreRejected() {
        rejected(svg("<use href=\"https://example.com/a.svg#x\"/>"), "referencia");
        rejected(svg("<use href=\"otro.svg#x\"/>"), "referencia");
        rejected(svg("<rect fill=\"url(https://example.com/x)\" width=\"1\" height=\"1\"/>"), "url");
        rejected(svg("<rect style=\"fill:url(//example.com/x)\" width=\"1\" height=\"1\"/>"), "url");
    }

    @ParameterizedTest
    @ValueSource(strings = {"@import url(https://example.com/a.css);", "@font-face{font-family:x;src:url(x.woff)}",
            ".a{background:url(https://example.com/x.png)}", ".a{fill:red}@media print{.a{fill:blue}}",
            ".a{content:\"\\75 rl\"}", ".a{background:image-set(\"x.png\" 1x)}", ".a{fill:red}</style><script>"})
    void cssThatLoadsResourcesOrUsesOtherAtRulesIsRejected(String css) {
        assertThatThrownBy(() -> sanitize(svg("<style>" + css + "</style>"))).isInstanceOf(InvalidSvgException.class);
    }

    @Test
    void unknownAttributesAndForeignNamespacesAreRejected() {
        rejected(svg("<rect formaction=\"x\" width=\"1\" height=\"1\"/>"), "atributo no permitido: formaction");
        rejected(svg("<x:include xmlns:x=\"http://www.w3.org/2001/XInclude\" href=\"/etc/passwd\"/>"), "XInclude");
        rejected(svg("<h:div xmlns:h=\"http://www.w3.org/1999/xhtml\"/>"), "elemento no permitido");
    }

    @Test
    void aDoctypeIsRejected() {
        rejected("<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY a \"aaaa\">]><svg " + NS
                + " viewBox=\"0 0 1 1\"><title>&a;</title></svg>", "DOCTYPE");
    }

    @Test
    void malformedXmlIsRejected() {
        rejected("<svg " + NS + " viewBox=\"0 0 1 1\"><g></svg>", "XML");
        rejected("no es xml", "XML");
    }

    @Test
    void onlyUtf8IsAccepted() {
        byte[] latin1 = ("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><svg " + NS + " viewBox=\"0 0 1 1\">"
                + "<title>Señal</title></svg>").getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> sanitizer.sanitize(latin1)).isInstanceOf(InvalidSvgException.class)
                .hasMessageContaining("UTF-8");
        byte[] declaredOther = ("<?xml version=\"1.0\" encoding=\"UTF-16\"?><svg " + NS + " viewBox=\"0 0 1 1\"/>")
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> sanitizer.sanitize(declaredOther)).isInstanceOf(InvalidSvgException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void theRootMustBeAnSvgElementWithAValidViewBox() {
        rejected("<g " + NS + "/>", "raíz");
        rejected("<svg viewBox=\"0 0 1 1\"/>", "raíz");
        rejected("<svg " + NS + "/>", "viewBox");
        rejected("<svg " + NS + " viewBox=\"0 0 0 10\"/>", "viewBox");
        rejected("<svg " + NS + " viewBox=\"0 0 -5 10\"/>", "viewBox");
        rejected("<svg " + NS + " viewBox=\"0 0 10\"/>", "viewBox");
        rejected("<svg " + NS + " viewBox=\"0 0 NaN 10\"/>", "viewBox");
    }

    @Test
    void textOutsideTitleDescAndStyleIsRejected() {
        rejected(svg("<g>visible</g>"), "texto");
    }

    @Test
    void processingInstructionsAreRejected() {
        rejected(svg("<?xml-stylesheet href=\"https://example.com/a.css\"?>"), "instrucciones");
    }

    @Test
    void anSvgLargerThanTheLimitIsRejected() {
        byte[] big = svg("<title>" + "a".repeat(524_288) + "</title>").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> sanitizer.sanitize(big)).isInstanceOf(SvgTooLargeException.class);
    }

    @Test
    void anSvgAtTheElementLimitIsAcceptedAndOneMoreIsRejected() {
        // La raíz cuenta como elemento.
        assertThat(sanitize(svg("<g/>".repeat(9_999)))).isNotNull();
        rejected(svg("<g/>".repeat(10_000)), "elementos");
    }

    @Test
    void anSvgAtTheDepthLimitIsAcceptedAndDeeperIsRejected() {
        assertThat(sanitize(svg("<g>".repeat(31) + "</g>".repeat(31)))).isNotNull();
        rejected(svg("<g>".repeat(32) + "</g>".repeat(32)), "profundidad");
    }

    @Test
    void emptyInputIsRejected() {
        assertThatThrownBy(() -> sanitizer.sanitize(new byte[0])).isInstanceOf(InvalidSvgException.class);
    }
}
