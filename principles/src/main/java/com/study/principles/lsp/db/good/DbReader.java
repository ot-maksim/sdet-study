package com.study.principles.lsp.db.good;

import java.util.List;

/** What every client can do. Contract: returns the users whose e-mail starts with the prefix. */
public interface DbReader {

    List<String> users(String prefix);
}
