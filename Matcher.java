import java.util.*;

public class Matcher {

    // Reduces supply when El Niño is selected
    public static void applyElNino(List<Region> regions) {
        for (Region r : regions) {
            if (r.name.equals("Punjab") || r.name.equals("Haryana")) {
                r.supply = (int) (r.supply * 0.90);
            } else {
                r.supply = (int) (r.supply * 0.80);
            }
        }
    }

    // NEW: returns the list of transfers (used by the map)
    public static List<Transfer> getTransfers(List<Region> regions) {
        HashMap<String, Integer> surplus = new HashMap<>();
        HashMap<String, Integer> deficit = new HashMap<>();

        for (Region r : regions) {
            if (r.getBalance() > 0) {
                surplus.put(r.name, r.getBalance());
            } else if (r.getBalance() < 0) {
                deficit.put(r.name, -r.getBalance());
            }
        }

        ArrayList<Map.Entry<String, Integer>> surplusList = new ArrayList<>(surplus.entrySet());
        ArrayList<Map.Entry<String, Integer>> deficitList = new ArrayList<>(deficit.entrySet());

        surplusList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        deficitList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        List<Transfer> transfers = new ArrayList<>();

        for (Map.Entry<String, Integer> s : surplusList) {
            for (Map.Entry<String, Integer> d : deficitList) {
                int available = s.getValue();
                int required = d.getValue();

                if (available > 0 && required > 0) {
                    int transfer = Math.min(available, required);
                    transfers.add(new Transfer(s.getKey(), d.getKey(), transfer));
                    s.setValue(available - transfer);
                    d.setValue(required - transfer);
                }
            }
        }
        return transfers;
    }

    // Does the matching and returns the result as text
    public static String match(List<Region> regions) {
        HashMap<String, Integer> surplus = new HashMap<>();
        HashMap<String, Integer> deficit = new HashMap<>();

        int totalSupply = 0;
        int totalDemand = 0;

        for (Region r : regions) {
            totalSupply += r.supply;
            totalDemand += r.demand;

            if (r.getBalance() > 0) {
                surplus.put(r.name, r.getBalance());
            } else if (r.getBalance() < 0) {
                deficit.put(r.name, -r.getBalance());
            }
        }

        ArrayList<Map.Entry<String, Integer>> surplusList = new ArrayList<>(surplus.entrySet());
        ArrayList<Map.Entry<String, Integer>> deficitList = new ArrayList<>(deficit.entrySet());

        surplusList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        deficitList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        StringBuilder sb = new StringBuilder();
        sb.append("--- MATCHING ---\n");

        for (Map.Entry<String, Integer> s : surplusList) {
            for (Map.Entry<String, Integer> d : deficitList) {
                int available = s.getValue();
                int required = d.getValue();

                if (available > 0 && required > 0) {
                    int transfer = Math.min(available, required);
                    sb.append(s.getKey()).append(" -> ").append(d.getKey())
                      .append(" : ").append(transfer).append(" units\n");
                    s.setValue(available - transfer);
                    d.setValue(required - transfer);
                }
            }
        }

        sb.append("\n--- REMAINING SURPLUS ---\n");
        for (Map.Entry<String, Integer> e : surplusList) {
            sb.append(e.getKey()).append(" : ").append(e.getValue()).append("\n");
        }

        sb.append("\n--- REMAINING DEFICIT ---\n");
        int unmet = 0;
        for (Map.Entry<String, Integer> e : deficitList) {
            sb.append(e.getKey()).append(" : ").append(e.getValue()).append("\n");
            unmet += e.getValue();
        }

        double shortagePercent = 0;
        if (totalDemand > 0) {
            shortagePercent = (unmet * 100.0) / totalDemand;
        }

        sb.append("\n--- SUMMARY ---\n");
        sb.append("Total supply : ").append(totalSupply).append("\n");
        sb.append("Total demand : ").append(totalDemand).append("\n");
        sb.append("Unmet demand : ").append(unmet).append("\n");
        sb.append(String.format("Shortage     : %.1f%% of total demand\n", shortagePercent));

        return sb.toString();
    }
}