package com.study.principles.records;

import com.study.principles.records.misfit.bad.UsersApiClient;
import com.study.principles.records.traps.bad.Attachment;
import com.study.principles.records.traps.bad.Credentials;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Runs each bad/good pair side by side: where a record fits, its traps, and where it does not fit.
 */
public class RecordsDemo {

    public static void main(String[] args) {
        System.out.println("=== FIT: DTO equality ===");
        var badExpected = new com.study.principles.records.fit.bad.UserResponse(1, "john", "j@x.io", "ADMIN");
        var badActual = new com.study.principles.records.fit.bad.UserResponse(1, "john", "j@x.io", "USER");
        System.out.println("  bad  expected ADMIN, got USER -> equals: " + badExpected.equals(badActual)
                + "   <-- false positive, toString: " + badActual);
        var goodExpected = new com.study.principles.records.fit.good.UserResponse(1, "john", "j@x.io", "ADMIN");
        var goodActual = new com.study.principles.records.fit.good.UserResponse(1, "john", "j@x.io", "USER");
        System.out.println("  good expected ADMIN, got USER -> equals: " + goodExpected.equals(goodActual)
                + ", toString: " + goodActual);

        System.out.println("\n=== TRAP: toString leaks secrets ===");
        System.out.println("  bad:  " + new Credentials("admin", "s3cret"));
        System.out.println("  good: " + new com.study.principles.records.traps.good.Credentials("admin", "s3cret"));

        System.out.println("\n=== TRAP: array component ===");
        byte[] bytes = "%PDF-1.7 report".getBytes(StandardCharsets.US_ASCII);
        var uploaded = new Attachment("report.pdf", bytes);
        var downloaded = new Attachment("report.pdf", bytes.clone());
        System.out.println("  bad  same content -> equals: " + uploaded.equals(downloaded) + ", toString: " + uploaded);
        var goodUp = new com.study.principles.records.traps.good.Attachment("report.pdf", bytes);
        var goodDown = new com.study.principles.records.traps.good.Attachment("report.pdf", bytes.clone());
        System.out.println("  good same content -> equals: " + goodUp.equals(goodDown) + ", toString: " + goodUp);

        System.out.println("\n=== MISFIT: a client as a record ===");
        var client = new UsersApiClient("http://localhost:7070", "eyJhbGciOi.secret", new ArrayList<>());
        var clients = new HashSet<UsersApiClient>();
        clients.add(client);
        client.getUser(42);
        System.out.println("  toString:            " + client);
        System.out.println("  token():             " + client.token());
        System.out.println("  still in HashSet:    " + clients.contains(client) + "   <-- hash changed after a request");

        var goodClient = new com.study.principles.records.misfit.good.UsersApiClient(
                "http://localhost:7070", "eyJhbGciOi.secret");
        goodClient.getUser(42);
        System.out.println("  good toString:       " + goodClient);
    }
}
