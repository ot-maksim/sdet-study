package com.study.principles.inheritance.basetest.good.mixins;

import com.study.principles.inheritance.basetest.Db;
import com.study.principles.inheritance.basetest.User;
import com.study.principles.inheritance.basetest.good.DbExtension;
import com.study.principles.inheritance.basetest.good.Checks;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Middle ground: a JUnit "test interface". A class can implement several, so it is multiple inheritance of
 * <i>behaviour</i> (extensions + default methods). No fields: state still comes in as parameters, which
 * keeps the interface from becoming a smaller god class.
 */
@ExtendWith(DbExtension.class)
public interface DbAssertions {

    default void assertStored(Db db, User expected) {
        Checks.assertStored(db, expected);
    }
}
