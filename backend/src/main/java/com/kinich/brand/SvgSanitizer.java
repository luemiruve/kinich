package com.kinich.brand;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.springframework.stereotype.Component;

/**
 * Sanitizador del logotipo SVG. Lista cerrada de elementos y atributos: todo lo activo o
 * externo se rechaza (scripts, eventos, foreignObject, imágenes, enlaces, animaciones, texto, javascript:, data:,
 * referencias que no sean #id, @import, @font-face, url() externos). Los metadatos de editores y los comentarios se
 * eliminan. El resultado se reescribe desde cero: nunca se guarda el archivo recibido. No cambia el diseño: no
 * recolorea ni toca geometría.
 */
@Component
public class SvgSanitizer {

    static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    private static final String XINCLUDE_NS = "http://www.w3.org/2001/XInclude";

    /** Espacios de nombres de editores: sus elementos y atributos son inertes y se eliminan. */
    private static final Set<String> EDITOR_NS = Set.of(
            "http://www.inkscape.org/namespaces/inkscape",
            "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd",
            "http://ns.adobe.com/AdobeIllustrator/10.0/",
            "http://ns.adobe.com/AdobeSVGViewerExtensions/3.0/",
            "http://ns.adobe.com/Extensibility/1.0/",
            "http://ns.adobe.com/Graphs/1.0/",
            "http://ns.adobe.com/SaveForWeb/1.0/",
            "http://ns.adobe.com/Variables/1.0/",
            "http://ns.adobe.com/ImageReplacement/1.0/",
            "http://ns.adobe.com/GenericCustomNamespace/1.0/",
            "http://ns.adobe.com/XPath/1.0/",
            "http://www.bohemiancoding.com/sketch/ns",
            "http://www.figma.com/figma/ns",
            "http://purl.org/dc/elements/1.1/",
            "http://creativecommons.org/ns#",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#");

    private static final Set<String> ELEMENTS = Set.of("svg", "g", "defs", "title", "desc", "style", "path", "rect",
            "circle", "ellipse", "line", "polyline", "polygon", "linearGradient", "radialGradient", "stop", "clipPath",
            "mask", "use");

    /** Elementos cuyo texto se conserva; en el resto solo se admite espacio en blanco. */
    private static final Set<String> TEXT_ELEMENTS = Set.of("title", "desc", "style");

    private static final Set<String> ATTRIBUTES = Set.of("id", "class", "style", "type", "version", "viewBox",
            "preserveAspectRatio", "width", "height", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry",
            "fx", "fy", "fr", "d", "points", "transform", "href", "offset", "fill", "fill-opacity", "fill-rule",
            "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray",
            "stroke-dashoffset", "stroke-opacity", "opacity", "color", "display", "visibility", "overflow",
            "clip-path", "clip-rule", "mask", "stop-color", "stop-opacity", "gradientUnits", "gradientTransform",
            "spreadMethod", "clipPathUnits", "maskUnits", "maskContentUnits", "shape-rendering", "vector-effect",
            "paint-order", "isolation", "mix-blend-mode", "enable-background");

    private static final Pattern INTERNAL_HREF = Pattern.compile("#[A-Za-z_][\\w.:-]*");
    private static final Pattern URL = Pattern.compile("url\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERNAL_URL = Pattern.compile("\\s*(['\"]?)#[A-Za-z_][\\w.:-]*\\1\\s*");
    private static final Pattern AT_RULE = Pattern.compile("@([a-z-]+)([^{;]*)");
    private static final Pattern THEME_QUERY =
            Pattern.compile("\\s*\\(\\s*prefers-color-scheme\\s*:\\s*(light|dark)\\s*\\)\\s*");
    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");
    private static final Pattern CSS_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final List<String> CSS_FORBIDDEN = List.of("@import", "@font-face", "expression(", "javascript:",
            "data:", "image-set(", "image(", "element(", "cross-fade(", "src(");

    private final SvgLimits limits;

    public SvgSanitizer(SvgLimits limits) {
        this.limits = limits;
    }

    public SanitizedSvg sanitize(byte[] data) {
        if (data.length > limits.maxBytes()) {
            throw new SvgTooLargeException();
        }
        if (data.length == 0) {
            throw new InvalidSvgException("El archivo está vacío");
        }
        String source = decodeUtf8(data);
        if (source.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")) {
            throw new InvalidSvgException("No se permite DOCTYPE");
        }
        try {
            return rewrite(source);
        } catch (XMLStreamException e) {
            throw new InvalidSvgException("El archivo no es un XML válido");
        }
    }

    private static String decodeUtf8(byte[] data) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new InvalidSvgException("El SVG debe estar codificado en UTF-8");
        }
    }

    private SanitizedSvg rewrite(String source) throws XMLStreamException {
        XMLStreamReader reader = inputFactory().createXMLStreamReader(new StringReader(source));
        StringWriter out = new StringWriter();
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
        Deque<String> open = new ArrayDeque<>();
        StringBuilder css = new StringBuilder();
        double[] viewBox = null;
        int elements = 0;
        int depth = 0;
        int skipping = 0;
        try {
            // El lector empieza en START_DOCUMENT: la codificación declarada se comprueba antes del primer evento.
            String encoding = reader.getCharacterEncodingScheme();
            if (encoding != null && !"UTF-8".equalsIgnoreCase(encoding)) {
                throw new InvalidSvgException("El SVG debe estar codificado en UTF-8");
            }
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        elements++;
                        depth++;
                        if (elements > limits.maxElements()) {
                            throw new InvalidSvgException("El SVG supera el máximo de elementos");
                        }
                        if (depth > limits.maxDepth()) {
                            throw new InvalidSvgException("El SVG supera la profundidad máxima");
                        }
                        if (skipping > 0) {
                            skipping++;
                            continue;
                        }
                        String ns = nullToEmpty(reader.getNamespaceURI());
                        String name = reader.getLocalName();
                        boolean root = open.isEmpty();
                        if (root && !(SVG_NS.equals(ns) && "svg".equals(name))) {
                            throw new InvalidSvgException("La raíz debe ser <svg> con el espacio de nombres SVG");
                        }
                        if (EDITOR_NS.contains(ns) || (SVG_NS.equals(ns) && "metadata".equals(name))) {
                            skipping = 1;
                            continue;
                        }
                        if (XINCLUDE_NS.equals(ns)) {
                            throw new InvalidSvgException("XInclude no permitido");
                        }
                        if (!SVG_NS.equals(ns) || !ELEMENTS.contains(name)) {
                            throw new InvalidSvgException("elemento no permitido: " + name);
                        }
                        Map<String, String> attributes = attributes(reader);
                        if (root) {
                            viewBox = viewBox(attributes.get("viewBox"));
                        }
                        writer.writeStartElement(name);
                        if (root) {
                            writer.writeDefaultNamespace(SVG_NS);
                        }
                        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                            writer.writeAttribute(attribute.getKey(), attribute.getValue());
                        }
                        open.push(name);
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        depth--;
                        if (skipping > 0) {
                            skipping--;
                            continue;
                        }
                        String name = open.pop();
                        if ("style".equals(name)) {
                            writer.writeCharacters(css(css.toString()));
                            css.setLength(0);
                        }
                        writer.writeEndElement();
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                        if (skipping > 0 || open.isEmpty()) {
                            continue;
                        }
                        String text = reader.getText();
                        String current = open.peek();
                        if ("style".equals(current)) {
                            css.append(text);
                        } else if (TEXT_ELEMENTS.contains(current)) {
                            writer.writeCharacters(text);
                        } else if (!text.isBlank()) {
                            throw new InvalidSvgException("No se permite texto fuera de title, desc o style");
                        }
                    }
                    case XMLStreamConstants.DTD -> throw new InvalidSvgException("No se permite DOCTYPE");
                    case XMLStreamConstants.ENTITY_REFERENCE -> throw new InvalidSvgException("No se permiten entidades");
                    case XMLStreamConstants.PROCESSING_INSTRUCTION ->
                            throw new InvalidSvgException("No se permiten instrucciones de procesamiento");
                    default -> {
                        // COMMENT y fin de documento: los comentarios se eliminan.
                    }
                }
            }
            writer.flush();
        } finally {
            reader.close();
            writer.close();
        }
        return new SanitizedSvg(out.toString().getBytes(StandardCharsets.UTF_8), viewBox[2], viewBox[3]);
    }

    private static XMLInputFactory inputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        return factory;
    }

    /** Atributos permitidos en orden de aparición; los de editores, xml:* y data-* se eliminan. */
    private static Map<String, String> attributes(XMLStreamReader reader) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String ns = nullToEmpty(reader.getAttributeNamespace(i));
            String name = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            if (name.toLowerCase(Locale.ROOT).startsWith("on")) {
                throw new InvalidSvgException("No se permiten eventos (on*)");
            }
            if (XLINK_NS.equals(ns) && "href".equals(name)) {
                result.put("href", checked("href", value));
            } else if (!ns.isEmpty()) {
                if (!EDITOR_NS.contains(ns) && !XMLConstants.XML_NS_URI.equals(ns)) {
                    throw new InvalidSvgException("atributo no permitido: " + name);
                }
            } else if (!name.startsWith("data-")) {
                if (!ATTRIBUTES.contains(name)) {
                    throw new InvalidSvgException("atributo no permitido: " + name);
                }
                result.put(name, checked(name, value));
            }
        }
        return result;
    }

    private static String checked(String name, String value) {
        String compact = value.replaceAll("[\\s\\p{Cntrl}]", "").toLowerCase(Locale.ROOT);
        if (compact.contains("javascript:") || compact.contains("data:")) {
            throw new InvalidSvgException("No se permiten javascript: ni data:");
        }
        if ("href".equals(name)) {
            if (!INTERNAL_HREF.matcher(value.strip()).matches()) {
                throw new InvalidSvgException("referencia no permitida: solo se admiten referencias internas (#id)");
            }
            return value.strip();
        }
        if ("style".equals(name)) {
            return css(value);
        }
        if (compact.contains("url(")) {
            requireInternalUrls(value);
        }
        return value;
    }

    /** CSS de <style> o style="": sin escapes, sin recursos externos y sin otras reglas que @media de tema. */
    static String css(String raw) {
        if (raw.indexOf('\\') >= 0 || raw.indexOf('<') >= 0) {
            throw new InvalidSvgException("CSS no permitido: escapes o etiquetas");
        }
        String css = CSS_COMMENT.matcher(raw).replaceAll("");
        if (css.contains("/*")) {
            throw new InvalidSvgException("CSS no permitido: comentario sin cerrar");
        }
        String lower = css.toLowerCase(Locale.ROOT);
        for (String forbidden : CSS_FORBIDDEN) {
            if (lower.replaceAll("\\s", "").contains(forbidden)) {
                throw new InvalidSvgException("CSS no permitido: " + forbidden);
            }
        }
        Matcher at = AT_RULE.matcher(lower);
        while (at.find()) {
            if (!"media".equals(at.group(1)) || !THEME_QUERY.matcher(at.group(2)).matches()) {
                throw new InvalidSvgException("CSS no permitido: solo @media (prefers-color-scheme: light|dark)");
            }
        }
        requireInternalUrls(css);
        return css;
    }

    private static void requireInternalUrls(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        Matcher url = URL.matcher(lower);
        int found = 0;
        while (url.find()) {
            found++;
            if (!INTERNAL_URL.matcher(url.group(1)).matches()) {
                throw new InvalidSvgException("url no permitida: solo se admiten referencias internas url(#id)");
            }
        }
        if (found != lower.split("url\\(", -1).length - 1) {
            throw new InvalidSvgException("url no permitida: solo se admiten referencias internas url(#id)");
        }
    }

    private static double[] viewBox(String value) {
        if (value == null) {
            throw new InvalidSvgException("El SVG necesita un viewBox válido");
        }
        String[] parts = value.strip().split("[\\s,]+");
        if (parts.length != 4) {
            throw new InvalidSvgException("El SVG necesita un viewBox válido");
        }
        double[] numbers = new double[4];
        try {
            for (int i = 0; i < 4; i++) {
                if (!NUMBER.matcher(parts[i]).matches()) {
                    throw new NumberFormatException();
                }
                numbers[i] = Double.parseDouble(parts[i]);
                if (!Double.isFinite(numbers[i])) {
                    throw new NumberFormatException();
                }
            }
        } catch (NumberFormatException e) {
            throw new InvalidSvgException("El SVG necesita un viewBox válido");
        }
        if (numbers[2] <= 0 || numbers[3] <= 0) {
            throw new InvalidSvgException("El SVG necesita un viewBox válido");
        }
        return numbers;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
