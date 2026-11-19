package com.flashdrop.auth.infrastructure.adapter.outbound.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.flashdrop.auth.application.port.outbound.ProfilePhotoProcessor;
import com.flashdrop.auth.domain.exception.InvalidProfilePhotoException;
import com.flashdrop.auth.domain.exception.ProfilePhotoTooLargeException;

/**
 * Normaliza la foto de perfil solo con el JDK (ImageIO), sin librerias nuevas.
 *
 * <ol>
 *   <li>Acepta JPEG o PNG, reconocidos por sus primeros bytes y no por el tipo declarado.</li>
 *   <li>Lee las dimensiones antes de decodificar y rechaza imagenes enormes ("bombas").</li>
 *   <li>Decodifica con submuestreo, asi una foto de 4000 px no ocupa toda la memoria.</li>
 *   <li>Endereza la imagen segun la orientacion EXIF (fotos tomadas con el celular en vertical).</li>
 *   <li>La reduce a un maximo de {@value #MAX_OUTPUT_SIDE} px y la reescribe como JPEG. Al
 *       reescribirla se pierden todos los metadatos, incluida la ubicacion GPS.</li>
 * </ol>
 */
public class ImageIoProfilePhotoProcessor implements ProfilePhotoProcessor {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_INPUT_SIDE = 4000;
    static final int MAX_OUTPUT_SIDE = 512;
    private static final float JPEG_QUALITY = 0.85f;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Override
    public byte[] normalize(byte[] content, String declaredContentType) {
        if (content == null || content.length == 0) {
            throw new InvalidProfilePhotoException("La foto está vacía");
        }
        if (content.length > MAX_BYTES) {
            throw new ProfilePhotoTooLargeException("La foto supera el límite de 2 MB");
        }
        String detected = detectContentType(content);
        if (detected == null) {
            throw new InvalidProfilePhotoException("La foto debe ser JPEG o PNG");
        }
        if (!declaredMatches(declaredContentType, detected)) {
            throw new InvalidProfilePhotoException("El tipo declarado no coincide con el contenido de la foto");
        }

        BufferedImage decoded = decode(content);
        int orientation = "image/jpeg".equals(detected) ? ExifOrientation.read(content) : 1;
        BufferedImage oriented = ExifOrientation.apply(decoded, orientation);
        return writeJpeg(scaleToFit(oriented, MAX_OUTPUT_SIDE));
    }

    static String detectContentType(byte[] content) {
        if (startsWith(content, JPEG_MAGIC)) return "image/jpeg";
        if (startsWith(content, PNG_MAGIC)) return "image/png";
        return null;
    }

    /**
     * Un tipo generico o ausente se acepta: algunos clientes (p. ej. {@code http} de
     * Flutter) mandan {@code application/octet-stream}. Lo que se rechaza es declarar
     * una imagen de un tipo y mandar otra.
     */
    private static boolean declaredMatches(String declared, String detected) {
        if (declared == null || declared.isBlank()) return true;
        String type = declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (type.equals("application/octet-stream")) return true;
        if (type.equals("image/jpg") || type.equals("image/pjpeg")) type = "image/jpeg";
        return type.equals(detected);
    }

    private static BufferedImage decode(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidProfilePhotoException("No se pudo leer la foto");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new InvalidProfilePhotoException("No se pudo leer la foto");
                }
                if (width > MAX_INPUT_SIDE || height > MAX_INPUT_SIDE) {
                    throw new ProfilePhotoTooLargeException(
                            "La foto supera el máximo de " + MAX_INPUT_SIDE + " x " + MAX_INPUT_SIDE + " píxeles");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                // Decodifica a cerca del doble del tamano final: menos memoria, misma calidad visible.
                int step = Math.max(1, Math.max(width, height) / (MAX_OUTPUT_SIDE * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) {
                    throw new InvalidProfilePhotoException("No se pudo leer la foto");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidProfilePhotoException || e instanceof ProfilePhotoTooLargeException) {
                throw (RuntimeException) e;
            }
            // Archivo truncado o danado, o una variante que ImageIO no lee (p. ej. JPEG CMYK).
            throw new InvalidProfilePhotoException("No se pudo leer la foto");
        }
    }

    /** Reduce por pasos de a la mitad y termina con bilineal: mejor calidad que un solo salto. */
    static BufferedImage scaleToFit(BufferedImage source, int maxSide) {
        BufferedImage current = toRgb(source);
        int width = current.getWidth();
        int height = current.getHeight();
        double ratio = Math.min(1.0, (double) maxSide / Math.max(width, height));
        int targetW = Math.max(1, (int) Math.round(width * ratio));
        int targetH = Math.max(1, (int) Math.round(height * ratio));

        while (width / 2 >= targetW && height / 2 >= targetH) {
            width /= 2;
            height /= 2;
            current = resize(current, width, height);
        }
        if (width != targetW || height != targetH) {
            current = resize(current, targetW, targetH);
        }
        return current;
    }

    /** Copia en RGB sobre fondo blanco: JPEG no tiene transparencia. */
    private static BufferedImage toRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** Escribe solo los pixeles: sin metadatos de entrada, el JPEG sale sin EXIF. */
    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el JPEG de la foto", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) return false;
        }
        return true;
    }

    /** Lee la etiqueta de orientacion (0x0112) del EXIF de un JPEG y la aplica a la imagen. */
    static final class ExifOrientation {

        private ExifOrientation() {
        }

        /** Orientacion 1..8; 1 (normal) si no hay EXIF o no se puede leer. */
        static int read(byte[] jpeg) {
            try {
                int pos = 2; // despues de SOI
                while (pos + 4 <= jpeg.length) {
                    if ((jpeg[pos] & 0xFF) != 0xFF) return 1;
                    int marker = jpeg[pos + 1] & 0xFF;
                    if (marker == 0xDA || marker == 0xD9) return 1; // inicio de datos o fin: no hay EXIF
                    int length = u16(jpeg, pos + 2, false);
                    if (length < 2) return 1;
                    int segment = pos + 4;
                    if (marker == 0xE1 && segment + 6 <= jpeg.length
                            && jpeg[segment] == 'E' && jpeg[segment + 1] == 'x' && jpeg[segment + 2] == 'i'
                            && jpeg[segment + 3] == 'f' && jpeg[segment + 4] == 0 && jpeg[segment + 5] == 0) {
                        return readTiff(jpeg, segment + 6, Math.min(jpeg.length, pos + 2 + length));
                    }
                    pos += 2 + length;
                }
            } catch (RuntimeException ignored) {
                // EXIF mal formado: se trata como orientacion normal.
            }
            return 1;
        }

        private static int readTiff(byte[] b, int tiff, int end) {
            boolean little;
            if (b[tiff] == 'I' && b[tiff + 1] == 'I') little = true;
            else if (b[tiff] == 'M' && b[tiff + 1] == 'M') little = false;
            else return 1;
            int ifd = tiff + (int) u32(b, tiff + 4, little);
            if (ifd + 2 > end) return 1;
            int entries = u16(b, ifd, little);
            for (int i = 0; i < entries; i++) {
                int entry = ifd + 2 + i * 12;
                if (entry + 12 > end) return 1;
                if (u16(b, entry, little) == 0x0112) {
                    int value = u16(b, entry + 8, little);
                    return value >= 1 && value <= 8 ? value : 1;
                }
            }
            return 1;
        }

        static BufferedImage apply(BufferedImage image, int orientation) {
            if (orientation <= 1 || orientation > 8) return image;
            int w = image.getWidth();
            int h = image.getHeight();
            boolean swap = orientation >= 5;
            AffineTransform t = new AffineTransform();
            switch (orientation) {
                case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
                case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
                case 4 -> { t.translate(0, h); t.scale(1, -1); }
                case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
                case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
                case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
                case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
                default -> { return image; }
            }
            BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = target.createGraphics();
            try {
                g.drawImage(image, t, null);
            } finally {
                g.dispose();
            }
            return target;
        }

        private static int u16(byte[] b, int at, boolean little) {
            int a = b[at] & 0xFF;
            int c = b[at + 1] & 0xFF;
            return little ? (c << 8) | a : (a << 8) | c;
        }

        private static long u32(byte[] b, int at, boolean little) {
            long v = 0;
            for (int i = 0; i < 4; i++) {
                int byteValue = b[at + (little ? 3 - i : i)] & 0xFF;
                v = (v << 8) | byteValue;
            }
            return v;
        }
    }
}
