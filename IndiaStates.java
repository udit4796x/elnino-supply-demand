import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class IndiaStates {

    private static final Map<String, double[]> POSITIONS = new LinkedHashMap<>();
    public static final String[] NAMES;

    static {
        // Approximate centre of each state / UT as {longitude, latitude}
        POSITIONS.put("Andhra Pradesh", new double[]{79.7, 15.9});
        POSITIONS.put("Arunachal Pradesh", new double[]{94.7, 28.2});
        POSITIONS.put("Assam", new double[]{92.9, 26.2});
        POSITIONS.put("Bihar", new double[]{85.3, 25.6});
        POSITIONS.put("Chhattisgarh", new double[]{81.8, 21.3});
        POSITIONS.put("Goa", new double[]{74.0, 15.3});
        POSITIONS.put("Gujarat", new double[]{71.2, 22.3});
        POSITIONS.put("Haryana", new double[]{76.3, 29.2});
        POSITIONS.put("Himachal Pradesh", new double[]{77.2, 31.9});
        POSITIONS.put("Jharkhand", new double[]{85.3, 23.6});
        POSITIONS.put("Karnataka", new double[]{75.7, 14.8});
        POSITIONS.put("Kerala", new double[]{76.3, 10.5});
        POSITIONS.put("Madhya Pradesh", new double[]{78.7, 23.5});
        POSITIONS.put("Maharashtra", new double[]{75.6, 19.4});
        POSITIONS.put("Manipur", new double[]{93.9, 24.7});
        POSITIONS.put("Meghalaya", new double[]{91.4, 25.5});
        POSITIONS.put("Mizoram", new double[]{92.8, 23.3});
        POSITIONS.put("Nagaland", new double[]{94.5, 26.1});
        POSITIONS.put("Odisha", new double[]{84.5, 20.5});
        POSITIONS.put("Punjab", new double[]{75.4, 31.0});
        POSITIONS.put("Rajasthan", new double[]{73.8, 26.6});
        POSITIONS.put("Sikkim", new double[]{88.5, 27.6});
        POSITIONS.put("Tamil Nadu", new double[]{78.4, 11.1});
        POSITIONS.put("Telangana", new double[]{79.0, 17.9});
        POSITIONS.put("Tripura", new double[]{91.7, 23.8});
        POSITIONS.put("Uttar Pradesh", new double[]{80.9, 26.8});
        POSITIONS.put("Uttarakhand", new double[]{79.1, 30.1});
        POSITIONS.put("West Bengal", new double[]{87.9, 23.0});

        // Union Territories
        POSITIONS.put("Andaman and Nicobar Islands", new double[]{92.7, 11.7});
        POSITIONS.put("Chandigarh", new double[]{76.8, 30.7});
        POSITIONS.put("Dadra and Nagar Haveli and Daman and Diu", new double[]{73.0, 20.2});
        POSITIONS.put("Delhi", new double[]{77.1, 28.6});
        POSITIONS.put("Jammu and Kashmir", new double[]{75.0, 33.8});
        POSITIONS.put("Ladakh", new double[]{77.6, 34.2});
        POSITIONS.put("Lakshadweep", new double[]{72.6, 10.6});
        POSITIONS.put("Puducherry", new double[]{79.8, 11.9});

        NAMES = POSITIONS.keySet().toArray(new String[0]);
        Arrays.sort(NAMES);
    }

    // Returns {longitude, latitude}, or null if the name is unknown
    public static double[] getPosition(String name) {
        return POSITIONS.get(name);
    }

    // Finds a known name ignoring capital letters. Returns null if it is not known.
    public static String canonicalName(String typed) {
        if (typed == null) return null;
        String t = typed.trim();
        for (String key : POSITIONS.keySet()) {
            if (key.equalsIgnoreCase(t)) return key;
        }
        return null;
    }

    // Adds a place of your own (city, district, warehouse...) so the map can draw it
    public static void addCustom(String name, double lon, double lat) {
        POSITIONS.put(name, new double[]{lon, lat});
    }
}