package com.orchestragent.daemon.cli;

import com.orchestragent.daemon.cli.config.ConfigStore;
import com.orchestragent.daemon.provider.AgentProvider;
import com.orchestragent.daemon.provider.ProviderInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/** The providers on the class path, found with {@link ServiceLoader}, and the choice of which ones to run. */
public final class Providers {

    /** A provider and what {@link AgentProvider#detect()} found (empty: not installed). */
    public record Found(AgentProvider provider, Optional<ProviderInfo> info) {
    }

    private Providers() {
    }

    /** Every provider on the class path, by ID, in class path order. A second provider with the same ID is skipped. */
    public static Map<String, AgentProvider> load() {
        Map<String, AgentProvider> providers = new LinkedHashMap<>();
        for (AgentProvider provider : ServiceLoader.load(AgentProvider.class)) {
            providers.putIfAbsent(provider.id(), provider);
        }
        return providers;
    }

    /** Hands each provider its settings from {@code config.json}; a wrong setting is a {@link UserError}. */
    public static Map<String, AgentProvider> configure(Map<String, AgentProvider> providers, ConfigStore store) {
        for (AgentProvider provider : providers.values()) {
            try {
                provider.configure(store.providerSettings(provider.id()));
            } catch (IllegalArgumentException e) {
                throw new UserError(e.getMessage());
            }
        }
        return providers;
    }

    public static List<Found> detectAll(Map<String, AgentProvider> available) {
        List<Found> found = new ArrayList<>();
        for (AgentProvider provider : available.values()) {
            found.add(new Found(provider, safeDetect(provider)));
        }
        return found;
    }

    /**
     * The providers to run. Named ones must exist and be installed (otherwise a {@link UserError} says which);
     * without names, every installed provider that runs by default (not the mock).
     */
    public static List<Found> select(Map<String, AgentProvider> available, List<String> names) {
        List<Found> selected = new ArrayList<>();
        if (names == null || names.isEmpty()) {
            for (Found found : detectAll(available)) {
                if (found.info().isPresent() && found.provider().enabledByDefault()) {
                    selected.add(found);
                }
            }
            if (selected.isEmpty()) {
                throw new UserError("No AI tool was found on this computer (known providers: "
                        + String.join(", ", available.keySet()) + "). Install one, or pass --providers mock to try the daemon.");
            }
            return selected;
        }
        for (String raw : names) {
            String name = raw.strip().toLowerCase(Locale.ROOT);
            if (name.isEmpty() || selected.stream().anyMatch(found -> found.provider().id().equals(name))) {
                continue;
            }
            AgentProvider provider = available.get(name);
            if (provider == null) {
                throw new UserError("Unknown provider " + name + " (known: " + String.join(", ", available.keySet()) + ")");
            }
            Optional<ProviderInfo> info = safeDetect(provider);
            if (info.isEmpty()) {
                throw new UserError("The provider " + name + " is not installed on this computer (see doctor)");
            }
            selected.add(new Found(provider, info));
        }
        if (selected.isEmpty()) {
            throw new UserError("--providers names no provider");
        }
        return selected;
    }

    private static Optional<ProviderInfo> safeDetect(AgentProvider provider) {
        try {
            return provider.detect();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
