package com.study.project.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/** One row of the current {@link ResultSet} position to an object. */
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet row) throws SQLException;
}
