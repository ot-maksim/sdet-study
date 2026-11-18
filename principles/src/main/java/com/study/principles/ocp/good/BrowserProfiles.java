package com.study.principles.ocp.good;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

/**
 * Registry: name → profile. The one place a <i>name</i> (from {@code -Dbrowser=...}) turns into behaviour.
 * <ul>
 *   <li>{@link #builtIn()} — the list lives here; a new built-in profile is one class + one line.</li>
 *   <li>{@link #discover()} — built-ins plus whatever {@link ServiceLoader} finds on the class path: another
 *       module (or the test sources, see {@code META-INF/services}) adds a profile without editing this class.</li>
 * </ul>
 * Unknown names fail when the registry is asked, listing what exists; duplicate names fail on construction.
 */
public final class BrowserProfiles {

    private final Map<String, BrowserProfile> byName = new TreeMap<>();

    public BrowserProfiles(Collection<? extends BrowserProfile> profiles) {
        for (BrowserProfile profile : profiles) {
            BrowserProfile previous = byName.putIfAbsent(profile.name(), profile);
            if (previous != null) {
                throw new IllegalStateException("two profiles named '" + profile.name() + "': "
                        + previous.getClass().getName() + " and " + profile.getClass().getName());
            }
        }
    }

    public static BrowserProfiles builtIn() {
        return new BrowserProfiles(builtInList());
    }

    public static BrowserProfiles discover() {
        List<BrowserProfile> all = new ArrayList<>(builtInList());
        ServiceLoader.load(BrowserProfile.class).forEach(all::add);
        return new BrowserProfiles(all);
    }

    private static List<BrowserProfile> builtInList() {
        return List.of(new ChromeProfile(), new FirefoxProfile(), new WebkitProfile(), new MobileWebProfile());
    }

    public BrowserProfile get(String name) {
        BrowserProfile profile = byName.get(name);
        if (profile == null) {
            throw new IllegalArgumentException("unknown browser '" + name + "', available: " + byName.keySet());
        }
        return profile;
    }

    public Collection<BrowserProfile> all() {
        return List.copyOf(byName.values());
    }
}
