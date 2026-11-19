package com.flashdrop.auth.infrastructure.adapter.outbound.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.flashdrop.auth.domain.exception.InvalidProfilePhotoException;
import com.flashdrop.auth.domain.exception.ProfilePhotoTooLargeException;

/** F10: la foto sale como JPEG chico, derecho y sin metadatos; lo que no es foto se rechaza. */
class ImageIoProfilePhotoProcessorTest {

    private final ImageIoProfilePhotoProcessor processor = new ImageIoProfilePhotoProcessor();

    // ------------------------------------------------------------------ salida normalizada

    @Test
    void unJpegGrandeSaleReducidoA512ManteniendoLaProporcion() throws IOException {
        byte[] out = processor.normalize(jpeg(2000, 1000), "image/jpeg");

        BufferedImage result = read(out);
        assertEquals(512, result.getWidth());
        assertEquals(256, result.getHeight());
        assertTrue(isJpeg(out));
    }

    @Test
    void unaFotoChicaNoSeAgranda() throws IOException {
        BufferedImage result = read(processor.normalize(jpeg(300, 200), "image/jpeg"));

        assertEquals(300, result.getWidth());
        assertEquals(200, result.getHeight());
    }

    /** El caso que motiva todo: una foto del celular con ubicacion GPS en el EXIF. */
    @Test
    void elExifConUbicacionGpsDesapareceDeLaFotoGuardada() throws IOException {
        byte[] withGps = withExif(jpeg(400, 300), 1, true);
        assertTrue(containsAscii(withGps, "Exif"), "el archivo de prueba debe traer EXIF");

        byte[] out = processor.normalize(withGps, "image/jpeg");

        assertFalse(hasApp1Segment(out), "la salida no debe tener segmento APP1 (EXIF)");
        assertFalse(containsAscii(out, "Exif"));
        assertFalse(containsAscii(out, "Santiago"), "el dato de prueba del GPS no debe sobrevivir");
    }

    /** Orientacion 6: el celular guardo la foto acostada y pide girarla 90 grados al mostrarla. */
    @Test
    void unaFotoVerticalConOrientacion6SaleDerecha() throws IOException {
        // Mitad izquierda roja, mitad derecha azul, guardada acostada (400 x 200).
        byte[] lying = withExif(twoColorJpeg(400, 200), 6, false);

        BufferedImage result = read(processor.normalize(lying, "image/jpeg"));

        // Al girar 90 grados a la derecha queda vertical, con el rojo arriba.
        assertEquals(200, result.getWidth());
        assertEquals(400, result.getHeight());
        assertTrue(isRed(result.getRGB(100, 50)), "arriba deberia quedar el rojo");
        assertTrue(isBlue(result.getRGB(100, 350)), "abajo deberia quedar el azul");
    }

    @Test
    void unPngConTransparenciaSaleComoJpegConFondoBlanco() throws IOException {
        BufferedImage transparent = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        byte[] out = processor.normalize(png(transparent), "image/png");

        assertTrue(isJpeg(out));
        int pixel = read(out).getRGB(50, 50);
        assertTrue(((pixel >> 16) & 0xFF) > 240 && ((pixel >> 8) & 0xFF) > 240 && (pixel & 0xFF) > 240,
                "lo transparente queda blanco");
    }

    /** {@code http} de Flutter manda application/octet-stream si no se indica el tipo. */
    @Test
    void aceptaTipoGenericoOAusenteYAliasDeJpeg() {
        byte[] photo = jpeg(50, 50);

        assertNotNull(processor.normalize(photo, null));
        assertNotNull(processor.normalize(photo, "application/octet-stream"));
        assertNotNull(processor.normalize(photo, "image/jpg"));
        assertNotNull(processor.normalize(photo, "IMAGE/JPEG; charset=binary"));
    }

    // ------------------------------------------------------------------ rechazos

    @Test
    void rechazaUnArchivoQueNoEsImagen() {
        byte[] text = "hola, no soy una foto".getBytes(StandardCharsets.UTF_8);

        assertThrows(InvalidProfilePhotoException.class, () -> processor.normalize(text, "image/jpeg"));
    }

    @Test
    void rechazaGifYWebp() {
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
        byte[] webp = "RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

        assertThrows(InvalidProfilePhotoException.class, () -> processor.normalize(gif, "image/gif"));
        assertThrows(InvalidProfilePhotoException.class, () -> processor.normalize(webp, "image/webp"));
    }

    @Test
    void rechazaDeclararPngYMandarJpeg() {
        assertThrows(InvalidProfilePhotoException.class, () -> processor.normalize(jpeg(50, 50), "image/png"));
    }

    @Test
    void rechazaUnJpegTruncado() {
        byte[] photo = jpeg(300, 300);
        byte[] cut = Arrays.copyOf(photo, 40);

        assertThrows(InvalidProfilePhotoException.class, () -> processor.normalize(cut, "image/jpeg"));
    }

    @Test
    void rechazaArchivoVacio() {
        assertThrows(InvalidProfilePhotoException.class, () -> processor.normalize(new byte[0], "image/jpeg"));
    }

    @Test
    void rechazaMasDe2Mb() {
        byte[] big = new byte[ImageIoProfilePhotoProcessor.MAX_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;

        assertThrows(ProfilePhotoTooLargeException.class, () -> processor.normalize(big, "image/jpeg"));
    }

    /** Se rechaza leyendo solo la cabecera: una imagen "bomba" nunca llega a decodificarse. */
    @Test
    void rechazaDimensionesMayoresA4000() {
        byte[] wide = png(new BufferedImage(4001, 10, BufferedImage.TYPE_BYTE_GRAY));

        assertThrows(ProfilePhotoTooLargeException.class, () -> processor.normalize(wide, "image/png"));
    }

    @Test
    void unExifMalFormadoSeIgnoraYLaFotoIgualSeProcesa() throws IOException {
        byte[] photo = jpeg(120, 80);
        byte[] broken = new byte[photo.length + 10];
        broken[0] = (byte) 0xFF;
        broken[1] = (byte) 0xD8;
        // APP1 "Exif" que declara mas largo del que tiene: el lector no debe reventar.
        byte[] app1 = {(byte) 0xFF, (byte) 0xE1, 0x00, 0x08, 'E', 'x', 'i', 'f', 0, 0};
        System.arraycopy(app1, 0, broken, 2, app1.length);
        System.arraycopy(photo, 2, broken, 12, photo.length - 2);

        assertEquals(1, ImageIoProfilePhotoProcessor.ExifOrientation.read(broken));
        assertEquals(120, read(processor.normalize(broken, "image/jpeg")).getWidth());
    }

    @Test
    void detectaElTipoPorLosPrimerosBytes() {
        assertEquals("image/jpeg", ImageIoProfilePhotoProcessor.detectContentType(jpeg(10, 10)));
        assertEquals("image/png", ImageIoProfilePhotoProcessor.detectContentType(
                png(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB))));
        assertEquals(null, ImageIoProfilePhotoProcessor.detectContentType(new byte[] {1, 2, 3}));
    }

    @Test
    void laSalidaEsEstableParaLaMismaEntrada() {
        byte[] photo = jpeg(200, 200);

        assertArrayEquals(processor.normalize(photo, "image/jpeg"), processor.normalize(photo, "image/jpeg"));
    }

    // ------------------------------------------------------------------ utilidades

    private static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 120, 200));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return encode(image, "jpeg");
    }

    private static byte[] twoColorJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return encode(image, "jpeg");
    }

    private static byte[] png(BufferedImage image) {
        return encode(image, "png");
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) throw new IllegalStateException("sin escritor " + format);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Inserta despues del SOI un APP1 EXIF (TIFF little endian) con la orientacion dada y,
     * si se pide, un IFD de GPS con un texto reconocible para comprobar que no sobrevive.
     */
    private static byte[] withExif(byte[] jpeg, int orientation, boolean gps) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        int entries = gps ? 2 : 1;
        int ifd0Size = 2 + entries * 12 + 4;
        int gpsIfdOffset = 8 + ifd0Size;
        byte[] place = "Santiago\0".getBytes(StandardCharsets.US_ASCII);

        write(tiff, 'I', 'I', 0x2A, 0x00, 0x08, 0, 0, 0);       // cabecera, IFD0 en 8
        le16(tiff, entries);
        le16(tiff, 0x0112); le16(tiff, 3); le32(tiff, 1); le16(tiff, orientation); le16(tiff, 0);
        if (gps) {
            le16(tiff, 0x8825); le16(tiff, 4); le32(tiff, 1); le32(tiff, gpsIfdOffset);
        }
        le32(tiff, 0);                                          // sin IFD siguiente
        if (gps) {
            int valueOffset = gpsIfdOffset + 2 + 12 + 4;
            le16(tiff, 1);
            le16(tiff, 0x001B); le16(tiff, 7); le32(tiff, place.length); le32(tiff, valueOffset);
            le32(tiff, 0);
            tiff.writeBytes(place);
        }

        byte[] body = tiff.toByteArray();
        int length = 2 + 6 + body.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        write(out, 0xFF, 0xE1, (length >> 8) & 0xFF, length & 0xFF, 'E', 'x', 'i', 'f', 0, 0);
        out.writeBytes(body);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, int... values) {
        for (int v : values) out.write(v);
    }

    private static void le16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void le32(ByteArrayOutputStream out, int v) {
        le16(out, v & 0xFFFF);
        le16(out, (v >> 16) & 0xFFFF);
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    /** Recorre los segmentos del JPEG hasta los datos de la imagen buscando un APP1. */
    private static boolean hasApp1Segment(byte[] b) {
        int pos = 2;
        while (pos + 4 <= b.length && (b[pos] & 0xFF) == 0xFF) {
            int marker = b[pos + 1] & 0xFF;
            if (marker == 0xE1) return true;
            if (marker == 0xDA) return false;
            pos += 2 + (((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF));
        }
        return false;
    }

    private static boolean containsAscii(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    private static boolean isRed(int rgb) {
        return ((rgb >> 16) & 0xFF) > 180 && ((rgb >> 8) & 0xFF) < 80 && (rgb & 0xFF) < 80;
    }

    private static boolean isBlue(int rgb) {
        return ((rgb >> 16) & 0xFF) < 80 && ((rgb >> 8) & 0xFF) < 80 && (rgb & 0xFF) > 180;
    }
}
