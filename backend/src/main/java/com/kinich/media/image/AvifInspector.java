package com.kinich.media.image;

import java.util.ArrayList;
import java.util.List;

/**
 * AVIF (ISOBMFF). Sin dependencias nuevas no se reescribe el contenedor: se aceptan solo archivos sin items
 * Exif/XMP ni miniaturas, y se devuelven sin modificar. Dimensiones y rotación del ítem primario
 * (pitm → ipma → ispe/irot). Rechaza secuencias (marca avis o caja moov).
 */
final class AvifInspector {

    private record Box(String type, int start, int content, int end) {}

    ImageInspection process(byte[] d) {
        List<Box> top = children(d, 0, d.length);
        if (top.isEmpty() || !top.get(0).type().equals("ftyp")) {
            throw InvalidImageException.malformed();
        }
        checkBrands(d, top.get(0));
        if (top.stream().anyMatch(b -> b.type().equals("moov"))) {
            throw InvalidImageException.animated();
        }
        Box meta = single(top, "meta");
        List<Box> metaChildren = children(d, meta.content() + 4, meta.end());
        checkItems(d, single(metaChildren, "iinf"));
        metaChildren.stream().filter(b -> b.type().equals("iref")).forEach(iref -> checkReferences(d, iref));
        long primary = primaryItem(d, single(metaChildren, "pitm"));
        Box iprp = single(metaChildren, "iprp");
        List<Box> iprpChildren = children(d, iprp.content(), iprp.end());
        Box ipco = single(iprpChildren, "ipco");
        List<Box> properties = children(d, ipco.content(), ipco.end());
        int width = 0;
        int height = 0;
        for (int index : propertiesOf(d, single(iprpChildren, "ipma"), primary)) {
            if (index < 1 || index > properties.size()) {
                throw InvalidImageException.malformed();
            }
            Box property = properties.get(index - 1);
            if (property.type().equals("ispe")) {
                width = Bytes.size(Bytes.u32be(d, property.content() + 4));
                height = Bytes.size(Bytes.u32be(d, property.content() + 8));
            } else if (property.type().equals("irot") && (Bytes.u8(d, property.content()) & 0x03) != 0) {
                throw InvalidImageException.rotated();
            }
        }
        if (width <= 0 || height <= 0) {
            throw InvalidImageException.malformed();
        }
        return new ImageInspection(MediaFormat.AVIF, width, height, d);
    }

    private static void checkBrands(byte[] d, Box ftyp) {
        boolean avif = false;
        for (int i = ftyp.content(); i + 4 <= ftyp.end(); i += 4) {
            if (i == ftyp.content() + 4) {
                continue; // minor_version
            }
            String brand = Bytes.ascii(d, i, 4);
            if (brand.equals("avis")) {
                throw InvalidImageException.animated();
            }
            avif |= brand.equals("avif");
        }
        if (!avif) {
            throw InvalidImageException.malformed();
        }
    }

    private static void checkItems(byte[] d, Box iinf) {
        int version = Bytes.u8(d, iinf.content());
        int pos = iinf.content() + 4 + (version == 0 ? 2 : 4);
        for (Box infe : children(d, pos, iinf.end())) {
            if (!infe.type().equals("infe")) {
                continue;
            }
            int infeVersion = Bytes.u8(d, infe.content());
            if (infeVersion < 2) {
                throw InvalidImageException.malformed();
            }
            int p = infe.content() + 4 + (infeVersion == 2 ? 2 : 4) + 2;
            String itemType = Bytes.ascii(d, p, 4);
            if (itemType.equals("Exif")) {
                throw InvalidImageException.metadata();
            }
            if (itemType.equals("mime")) {
                int afterName = skipString(d, p + 4, infe.end());
                int contentTypeEnd = skipString(d, afterName, infe.end());
                String contentType = Bytes.ascii(d, afterName, contentTypeEnd - afterName - 1);
                if (contentType.equalsIgnoreCase("application/rdf+xml")) {
                    throw InvalidImageException.metadata();
                }
            }
        }
    }

    /** Una miniatura (thmb) puede mostrar el encuadre original: se rechaza. */
    private static void checkReferences(byte[] d, Box iref) {
        for (Box reference : children(d, iref.content() + 4, iref.end())) {
            if (reference.type().equals("thmb")) {
                throw InvalidImageException.metadata();
            }
        }
    }

    private static long primaryItem(byte[] d, Box pitm) {
        int version = Bytes.u8(d, pitm.content());
        return version == 0 ? Bytes.u16be(d, pitm.content() + 4) : Bytes.u32be(d, pitm.content() + 4);
    }

    /** Índices (1-based en ipco) de las propiedades asociadas al ítem. */
    private static List<Integer> propertiesOf(byte[] d, Box ipma, long item) {
        int version = Bytes.u8(d, ipma.content());
        boolean wideIndex = (Bytes.u8(d, ipma.content() + 3) & 0x01) != 0;
        long entries = Bytes.u32be(d, ipma.content() + 4);
        int pos = ipma.content() + 8;
        for (long e = 0; e < entries; e++) {
            long id = version < 1 ? Bytes.u16be(d, pos) : Bytes.u32be(d, pos);
            pos += version < 1 ? 2 : 4;
            int count = Bytes.u8(d, pos++);
            List<Integer> indexes = new ArrayList<>();
            for (int a = 0; a < count; a++) {
                if (wideIndex) {
                    indexes.add(Bytes.u16be(d, pos) & 0x7FFF);
                    pos += 2;
                } else {
                    indexes.add(Bytes.u8(d, pos) & 0x7F);
                    pos += 1;
                }
            }
            if (pos > ipma.end()) {
                throw InvalidImageException.malformed();
            }
            if (id == item) {
                return indexes;
            }
        }
        throw InvalidImageException.malformed();
    }

    /** Devuelve la posición siguiente al terminador nulo. */
    private static int skipString(byte[] d, int pos, int end) {
        while (pos < end) {
            if (Bytes.u8(d, pos++) == 0) {
                return pos;
            }
        }
        throw InvalidImageException.malformed();
    }

    private static Box single(List<Box> boxes, String type) {
        List<Box> matches = boxes.stream().filter(b -> b.type().equals(type)).toList();
        if (matches.size() != 1) {
            throw InvalidImageException.malformed();
        }
        return matches.get(0);
    }

    private static List<Box> children(byte[] d, int pos, int end) {
        List<Box> boxes = new ArrayList<>();
        while (pos < end) {
            long size = Bytes.u32be(d, pos);
            String type = Bytes.ascii(d, pos + 4, 4);
            int header = 8;
            if (size == 1) {
                size = Bytes.u32be(d, pos + 8) << 32 | Bytes.u32be(d, pos + 12);
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header || size > end - pos) {
                throw InvalidImageException.malformed();
            }
            boxes.add(new Box(type, pos, pos + header, (int) (pos + size)));
            pos += (int) size;
        }
        return boxes;
    }
}
