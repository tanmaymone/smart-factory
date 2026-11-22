package com.factory.central.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InterlockEvaluator {

    private final Map<String, InterlockRule> rulesByKey;

    public InterlockEvaluator(List<InterlockRule> rules) {
        Map<String, InterlockRule> map = new LinkedHashMap<>();
        for (InterlockRule rule : rules) {
            InterlockRule previous = map.put(rule.key(), rule);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate InterlockRule key '" + rule.key() + "': "
                                + previous.getClass().getName()
                                + " and "
                                + rule.getClass().getName());
            }
        }
        this.rulesByKey = Map.copyOf(map);
    }

    /**
     * @return empty when the write is allowed; otherwise a rejection reason
     */
    public Optional<String> evaluate(String ruleKey, InterlockContext context) {
        if (ruleKey == null || ruleKey.isBlank()) {
            return Optional.empty();
        }
        InterlockRule rule = rulesByKey.get(ruleKey);
        if (rule == null) {
            return Optional.of("Unknown interlock rule key: " + ruleKey);
        }
        return rule.violation(context);
    }
}
