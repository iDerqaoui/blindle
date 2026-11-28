package me.blindle.curation.themes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.blindle.content.ContentLoader;
import me.blindle.content.ContentOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Thèmes édités en base, servis à la place des fichiers de même identifiant. */
@Component
public class ThemeStore implements ContentOverlay {

    private static final Logger log = LoggerFactory.getLogger(ThemeStore.class);

    private final ThemeDocumentRepository documents;
    private final ObjectMapper mapper = new ObjectMapper();

    public ThemeStore(ThemeDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public ContentLoader.Overrides overrides() {
        Map<String, JsonNode> themes = new LinkedHashMap<>();
        for (ThemeDocument d : documents.findAll()) {
            try {
                themes.put(d.themeId(), mapper.readTree(d.document()));
            } catch (JsonProcessingException e) {
                log.warn("Thème {} en base illisible, fichier utilisé ({})", d.themeId(), e.getOriginalMessage());
            }
        }
        return new ContentLoader.Overrides(themes, Map.of());
    }
}
