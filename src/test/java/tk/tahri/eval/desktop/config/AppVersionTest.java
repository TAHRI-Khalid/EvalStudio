package tk.tahri.eval.desktop.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppVersionTest {

    @Test
    void testGetVersionReturnsValidApplicationVersion() {
        String version = AppVersion.getVersion();
        assertNotNull(version, "La version ne doit pas être nulle");
        assertTrue(version.startsWith("v"), "La version doit commencer par 'v' : " + version);
        assertEquals("v0.1.1", version, "La version doit correspondre à v0.1.1");
    }
}
