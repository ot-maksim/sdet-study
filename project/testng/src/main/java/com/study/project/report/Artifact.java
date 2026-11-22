package com.study.project.report;

/** Something a test produced for a person to look at: a screenshot today, a page source or a trace tomorrow. */
public record Artifact(String test, String name, String mediaType, String extension, byte[] content) {

    /** {@code UsersPageTest-createsUser()} → {@code UsersPageTest-createsUser_}: safe as a file name everywhere. */
    public String fileName() {
        return (test + "-" + name).replaceAll("[^A-Za-z0-9._-]+", "_") + "." + extension;
    }
}
