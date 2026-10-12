package com.kinich.media.processing;

import com.kinich.media.image.MediaFormat;
import java.util.List;

/**
 * Puerto de procesamiento: genera las representaciones web de un original ya validado. El original no se modifica.
 * El dominio no conoce la herramienta (libvips) ni rutas de trabajo.
 */
public interface ImageRenditionProcessor {

    List<RenditionFile> render(byte[] original, MediaFormat format, int originalWidth);
}
