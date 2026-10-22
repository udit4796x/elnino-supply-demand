import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("========== SUPPLY-DEMAND MATCHER ==========");
        System.out.println("Select Condition:");
        System.out.println("1. Normal");
        System.out.println("2. El Niño");
        System.out.print("Enter choice: ");
        int choice = sc.nextInt();

        List<Region> regions = new ArrayList<>();
        regions.add(new Region("Punjab", 10000, 4000));
        regions.add(new Region("Haryana", 8000, 5000));
        regions.add(new Region("Rajasthan", 3000, 7000));
        regions.add(new Region("Maharashtra", 2000, 6000));

        if (choice == 2) {
            Matcher.applyElNino(regions);
        }

        System.out.println(Matcher.match(regions));

        sc.close();
    }
}