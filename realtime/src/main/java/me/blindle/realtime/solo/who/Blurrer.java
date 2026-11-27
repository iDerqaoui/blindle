package me.blindle.realtime.solo.who;

import com.jhlabs.image.GaussianFilter;
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
 * Floute les images du défi côté serveur (flou gaussien de JH Labs Image Filters) : le client ne reçoit que le palier atteint, jamais
 * l'image nette avant la révélation (principe 2). Les images viennent du contenu (URL des cartes),
 * jamais d'une valeur envoyée par le client.
 */
@Component
public class Blurrer {

    private static final Logger LOG = LoggerFactory.getLogger(Blurrer.class);
    static final int WIDTH = 240;
    static final int HEIGHT = 340;
    /** Rayon du flou gaussien par palier : d'une tache de couleurs au net. */
    static final float[] RADII = {36, 24, 15, 9, 5, 2, 0};

    private final Function<String, BufferedImage> source;
    private final Map<String, BufferedImage> originals = lru(32);
    private final Map<String, byte[]> rendered = lru(256);

    public Blurrer() {
        this(Blurrer::download);
    }

    Blurrer(Function<String, BufferedImage> source) {
        this.source = source;
    }

    /** Image au palier donné (PNG). Une image introuvable donne un aplat gris (la partie continue). */
    public synchronized byte[] render(String url, int level) {
        int l = Math.max(0, Math.min(RADII.length - 1, level));
        String key = l + "|" + url;
        byte[] cached = rendered.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage base = originals.computeIfAbsent(url, this::load);
        byte[] png = png(blur(base, RADII[l], l == 0));
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

    /** Flou gaussien (bords étirés, pas de cadre noir) ; au premier palier, assombri façon silhouette. */
    static BufferedImage blur(BufferedImage img, float radius, boolean dark) {
        BufferedImage out = radius <= 0 ? img : new GaussianFilter(radius).filter(img, null);
        if (!dark) {
            return out;
        }
        BufferedImage shaded = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = shaded.createGraphics();
        g.drawImage(out, 0, 0, null);
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.dispose();
        return shaded;
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
