package me.blindle.rules;

import me.blindle.core.rule.Rule;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Catalogue des règles disponibles, indexées par {@link Rule#type()}. */
@Component
public class RuleCatalog {

    private final Map<String, Rule> rules;

    public RuleCatalog(List<Rule> rules) {
        this.rules = rules.stream().collect(Collectors.toUnmodifiableMap(Rule::type, Function.identity()));
    }

    public Optional<Rule> find(String type) {
        return Optional.ofNullable(rules.get(type));
    }

    public Rule require(String type) {
        return find(type).orElseThrow(() -> new IllegalArgumentException("Règle inconnue : " + type));
    }

    public Map<String, Rule> all() {
        return rules;
    }
}
