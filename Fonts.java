import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// Picks the best installed fonts, so the window looks right on Windows, Mac and Linux
public class Fonts {

    private static final Set<String> AVAILABLE = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

    private static String pick(String fallback, String... names) {
        for (String n : names) {
            if (AVAILABLE.contains(n)) return n;
        }
        return fallback;
    }

    public static final String SANS = pick(Font.SANS_SERIF,
            "Segoe UI", "Helvetica Neue", "Noto Sans", "Ubuntu", "DejaVu Sans");
    public static final String SERIF = pick(Font.SERIF,
            "Georgia", "Cambria", "Palatino Linotype", "Noto Serif", "DejaVu Serif");
}