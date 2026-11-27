package me.blindle.realtime.solo.who;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Pixelise les images du défi côté serveur : le client ne reçoit que le palier atteint, jamais
 * l'image nette avant la révélation (principe 2). Les images viennent du contenu (URL des cartes),
 * jamais d'une valeur envoyée par le client.
 */
@Component
public class Pixelator {

    private static final Logger LOG = LoggerFactory.getLogger(Pixelator.class);
    static final int WIDTH = 240;
    static final int HEIGHT = 340;
    /** Taille des blocs par palier : du très flou au net. */
    static final int[] BLOCKS = {40, 28, 18, 11, 7, 4, 1};

    private final Function<String, BufferedImage> source;
    private final Map<String, BufferedImage> originals = lru(32);
    private final Map<String, byte[]> rendered = lru(256);

    public Pixelator() {
        this(Pixelator::download);
    }

    Pixelator(Function<String, BufferedImage> source) {
        this.source = source;
    }

    /** Image au palier donné (PNG). Une image introuvable donne un aplat gris (la partie continue). */
    public synchronized byte[] render(String url, int level) {
        int l = Math.max(0, Math.min(BLOCKS.length - 1, level));
        String key = l + "|" + url;
        byte[] cached = rendered.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage base = originals.computeIfAbsent(url, this::load);
        byte[] png = png(pixelate(base, BLOCKS[l], l == 0));
        rendered.put(key, png);
        return png;
    }

    private BufferedImage load(String url) {
        try {
            return fit(source.apply(url));
        } catch (RuntimeException e) {
            LOG.warn("Image du défi introuvable : {}", url, e);
            BufferedImage grey = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = grey.createGraphics();
            g.setColor(new Color(60, 60, 70));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.dispose();
            return grey;
        }
    }

    /** Recadre au format carte (portrait), centré. */
    static BufferedImage fit(BufferedImage src) {
        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        double scale = Math.max((double) WIDTH / src.getWidth(), (double) HEIGHT / src.getHeight());
        int w = (int) Math.ceil(src.getWidth() * scale);
        int h = (int) Math.ceil(src.getHeight() * scale);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, (WIDTH - w) / 2, (HEIGHT - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    /** Moyenne par bloc ; au premier palier, assombri façon silhouette. */
    static BufferedImage pixelate(BufferedImage img, int block, boolean dark) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y += block) {
            for (int x = 0; x < img.getWidth(); x += block) {
                int w = Math.min(block, img.getWidth() - x);
                int h = Math.min(block, img.getHeight() - y);
                long r = 0, gr = 0, b = 0;
                for (int yy = y; yy < y + h; yy++) {
                    for (int xx = x; xx < x + w; xx++) {
                        int rgb = img.getRGB(xx, yy);
                        r += (rgb >> 16) & 0xff;
                        gr += (rgb >> 8) & 0xff;
                        b += rgb & 0xff;
                    }
                }
                int n = w * h;
                double f = dark ? 0.55 : 1;
                int rgb = ((int) (r / n * f) << 16) | ((int) (gr / n * f) << 8) | (int) (b / n * f);
                for (int yy = y; yy < y + h; yy++) {
                    for (int xx = x; xx < x + w; xx++) {
                        out.setRGB(xx, yy, rgb);
                    }
                }
            }
        }
        return out;
    }

    private static byte[] png(BufferedImage img) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private static BufferedImage download(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "blindle").GET().build();
            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + res.statusCode());
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(res.body()));
            if (img == null) {
                throw new IllegalStateException("format d'image non lu");
            }
            return img;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static <K, V> Map<K, V> lru(int max) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > max;
            }
        };
    }
}
