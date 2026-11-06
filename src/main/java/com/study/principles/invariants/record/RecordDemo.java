package com.study.principles.invariants.record;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows validation in a compact constructor and the shallow-immutability trap.
 */
public class RecordDemo {

    public static void main(String[] args) {
        System.out.println("=== Compact constructor: an invalid DateRange cannot be created ===");
        var range = new DateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10));
        System.out.println("  valid: " + range + ", days = " + range.days());
        try {
            new DateRange(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1));
        } catch (IllegalArgumentException e) {
            System.out.println("  rejected: " + e.getMessage());
        }

        System.out.println("\n=== TRAP: ShallowResult ===");
        List<String> source = new ArrayList<>(List.of("loginTest"));
        var shallow = new ShallowResult(source);
        int hashBefore = shallow.hashCode();
        source.add("searchTest");                       // changed from outside
        System.out.println("  after source.add():          " + shallow);
        shallow.failedTests().clear();                  // changed through the accessor
        System.out.println("  after failedTests().clear(): " + shallow);
        System.out.println("  hashCode changed: " + (hashBefore != shallow.hashCode()));

        System.out.println("\n=== FIX: SafeResult ===");
        List<String> source2 = new ArrayList<>(List.of("loginTest"));
        var safe = new SafeResult(source2);
        source2.add("searchTest");
        System.out.println("  after source.add():          " + safe + "   <-- unaffected");
        try {
            safe.failedTests().clear();
        } catch (UnsupportedOperationException e) {
            System.out.println("  failedTests().clear() rejected: " + e.getClass().getSimpleName());
        }
    }
}
