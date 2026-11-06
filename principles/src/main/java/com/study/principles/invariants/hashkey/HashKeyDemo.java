package com.study.principles.invariants.hashkey;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches an auth token per test user, then "renames" the user.
 * With a mutable key the cached entry becomes unreachable; with an immutable key it does not.
 */
public class HashKeyDemo {

    public static void main(String[] args) {
        System.out.println("=== BAD: mutable key ===");
        Map<com.study.principles.invariants.hashkey.bad.TestUser, String> badTokens = new HashMap<>();
        var badUser = new com.study.principles.invariants.hashkey.bad.TestUser("alice");
        badTokens.put(badUser, "token-123");

        badUser.setLogin("alice_renamed");   // identity changed while the object is a map key

        System.out.println("  get(sameObject)         = " + badTokens.get(badUser) + "   <-- lost");
        System.out.println("  containsKey(sameObject) = " + badTokens.containsKey(badUser));
        System.out.println("  size                    = " + badTokens.size() + "   <-- still there, unreachable");

        System.out.println("\n=== GOOD: immutable record key ===");
        Map<com.study.principles.invariants.hashkey.good.TestUser, String> goodTokens = new HashMap<>();
        var goodUser = new com.study.principles.invariants.hashkey.good.TestUser("alice");
        goodTokens.put(goodUser, "token-123");

        var renamed = goodUser.withLogin("alice_renamed");   // a new object, original untouched

        System.out.println("  get(original) = " + goodTokens.get(goodUser) + "   <-- still reachable");
        System.out.println("  get(renamed)  = " + goodTokens.get(renamed) + "   <-- a different user, as expected");
    }
}
