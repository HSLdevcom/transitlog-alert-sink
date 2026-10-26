package fi.hsl.transitlog.alerts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DbWriterTest {
    @Test
    public void readsDatabaseCredentialFromSystemProperty() {
        final String propertyName = "transitlog.test.db.username";
        final String originalValue = System.getProperty(propertyName);

        try {
            System.setProperty(propertyName, "test-user");

            assertEquals("test-user", DbWriter.getDatabaseCredential(propertyName, "UNUSED_TEST_ENV"));
        } finally {
            if (originalValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalValue);
            }
        }
    }
}
