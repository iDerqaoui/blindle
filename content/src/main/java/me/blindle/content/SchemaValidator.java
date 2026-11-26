package me.blindle.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Validation JSON Schema (draft 2020-12) des thèmes et presets, schémas lus sur le classpath. */
public final class SchemaValidator {

    private final JsonSchema themeSchema;
    private final JsonSchema presetSchema;

    public SchemaValidator() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        this.themeSchema = load(factory, "/schema/theme.schema.json");
        this.presetSchema = load(factory, "/schema/preset.schema.json");
    }

    private static JsonSchema load(JsonSchemaFactory factory, String resource) {
        try (InputStream in = SchemaValidator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Schéma introuvable : " + resource);
            }
            return factory.getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> validateTheme(JsonNode node) {
        return format(themeSchema.validate(node));
    }

    public List<String> validatePreset(JsonNode node) {
        return format(presetSchema.validate(node));
    }

    private static List<String> format(Set<ValidationMessage> messages) {
        return messages.stream()
                .sorted(Comparator.comparing(m -> String.valueOf(m.getInstanceLocation())))
                .map(ValidationMessage::getMessage)
                .toList();
    }
}
