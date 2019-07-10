
package org.apereo.cas;

import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.runner.RunWith;

/**
 * This is {@link AllTestsSuite}.
 *
 * @author Auto-generated by Gradle Build
 * @since 6.0.0-RC3
 */
@SelectClasses({
    JdbcMultiRowAttributeRepositoryTests.class,
    JdbcSingleRowAttributeRepositoryTests.class,
    RestfulPersonAttributeDaoTests.class,
    CachingAttributeRepositoryTests.class,
    JdbcSingleRowAttributeRepositoryPostgresTests.class
})
@RunWith(JUnitPlatform.class)
public class AllTestsSuite {
}