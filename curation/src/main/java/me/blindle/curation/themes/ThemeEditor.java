package me.blindle.curation.themes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.blindle.content.ContentLoader;
import me.blindle.content.ContentRegistry;
import me.blindle.content.Preset;
import me.blindle.content.Theme;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Édition des cartes des thèmes écrits à la main (pas les thèmes générés, comme les univers anime,
 * qui s'éditent par leur curation). Chaque modification construit le thème complet, le fait valider
 * comme un fichier (schéma, droits, presets qui l'utilisent, règles du moteur), l'enregistre en base
 * puis recharge le contenu : les nouvelles rooms l'ont aussitôt, les rooms existantes gardent le leur.
 * Un thème refusé lève {@link me.blindle.content.ContentValidationException} et rien n'est enregistré.
 */
@Service
public class ThemeEditor {

    private final ContentRegistry content;
    private final ThemeDocumentRepository documents;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ThemeEditor(ContentRegistry content, ThemeDocumentRepository documents, Clock clock) {
        this.content = content;
        this.documents = documents;
        this.clock = clock;
    }

    /** Thème servi, et s'il vient de la base (date de la dernière modification) ou de son fichier. */
    public record Edited(Theme theme, Instant updatedAt, List<String> presets) {
        public boolean stored() {
            return updatedAt != null;
        }
    }

    /** Thèmes éditables, triés par nom. */
    public List<Edited> themes() {
        return content.themes().stream()
                .filter(t -> !content.generated(t.id()))
                .sorted(Comparator.comparing(Theme::name))
                .map(this::edited)
                .toList();
    }

    public Edited theme(String id) {
        return edited(editable(id));
    }

    /** Remplace la carte {@code cardId} (son identifiant peut changer), ou l'ajoute si {@code cardId} est null. */
    public synchronized Edited putCard(String themeId, String cardId, JsonNode card, UUID by) {
        if (card == null || !card.isObject()) {
            throw new IllegalArgumentException("carte attendue (objet JSON)");
        }
        ObjectNode doc = document(editable(themeId));
        ArrayNode cards = (ArrayNode) doc.withArray("cards");
        ObjectNode clean = withoutNulls((ObjectNode) card);
        String newId = clean.path("id").asText("");
        int index = cardId == null ? -1 : indexOf(cards, cardId);
        if (cardId != null && index < 0) {
            throw new NoSuchElementException("Carte inconnue : " + cardId);
        }
        if (!newId.equals(cardId) && indexOf(cards, newId) >= 0) {
            throw new IllegalArgumentException("Une carte « " + newId + " » existe déjà");
        }
        if (index < 0) {
            cards.add(clean);
        } else {
            cards.set(index, clean);
        }
        return save(themeId, doc, by);
    }

    public synchronized Edited removeCard(String themeId, String cardId, UUID by) {
        ObjectNode doc = document(editable(themeId));
        ArrayNode cards = (ArrayNode) doc.withArray("cards");
        int index = indexOf(cards, cardId);
        if (index < 0) {
            throw new NoSuchElementException("Carte inconnue : " + cardId);
        }
        cards.remove(index);
        return save(themeId, doc, by);
    }

    /** Oublie la version en base : le fichier de {@code content/themes} revient. */
    public synchronized Edited reset(String themeId) {
        editable(themeId);
        if (documents.existsById(themeId)) {
            content.preview(new ContentLoader.Overrides(Map.of(themeId, NullNode.getInstance()), Map.of()));
            documents.deleteById(themeId);
            content.reload();
        }
        return theme(themeId);
    }

    private Edited save(String themeId, ObjectNode doc, UUID by) {
        content.preview(new ContentLoader.Overrides(Map.of(themeId, doc), Map.of()));
        String json;
        try {
            json = mapper.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        Instant now = clock.instant();
        ThemeDocument row = documents.findById(themeId).orElse(null);
        if (row == null) {
            row = new ThemeDocument(themeId, json, now, by);
        } else {
            row.update(json, now, by);
        }
        documents.save(row);
        content.reload();
        return theme(themeId);
    }

    /** Document de départ : celui en base s'il existe, sinon le thème servi (lu depuis son fichier). */
    private ObjectNode document(Theme theme) {
        Optional<ThemeDocument> stored = documents.findById(theme.id());
        try {
            if (stored.isPresent()) {
                return (ObjectNode) mapper.readTree(stored.get().document());
            }
        } catch (JsonProcessingException e) {
            // document illisible : on repart du thème servi
        }
        return withoutNulls(mapper.valueToTree(theme));
    }

    private Theme editable(String id) {
        Theme theme = content.theme(id).orElseThrow(() -> new NoSuchElementException("Thème inconnu : " + id));
        if (content.generated(id)) {
            throw new IllegalArgumentException("Le thème « " + id + " » est généré : il s'édite par sa curation");
        }
        return theme;
    }

    private Edited edited(Theme theme) {
        List<String> presets = content.presets().stream()
                .filter(p -> p.theme().equals(theme.id())).map(Preset::id).toList();
        return new Edited(theme, documents.findById(theme.id()).map(ThemeDocument::updatedAt).orElse(null), presets);
    }

    private static int indexOf(ArrayNode cards, String cardId) {
        for (int i = 0; i < cards.size(); i++) {
            if (cardId.equals(cards.get(i).path("id").asText(null))) {
                return i;
            }
        }
        return -1;
    }

    /** Retire les champs null (le schéma n'en veut pas : un champ absent vaut « non renseigné »). */
    static ObjectNode withoutNulls(ObjectNode node) {
        ObjectNode copy = node.deepCopy();
        strip(copy);
        return copy;
    }

    private static void strip(JsonNode node) {
        if (node.isObject()) {
            List<String> nulls = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                if (f.getValue().isNull()) {
                    nulls.add(f.getKey());
                } else {
                    strip(f.getValue());
                }
            }
            ((ObjectNode) node).remove(nulls);
        } else if (node.isArray()) {
            node.forEach(ThemeEditor::strip);
        }
    }
}
