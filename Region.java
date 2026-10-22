public class Region {

    String name;
    int supply;
    int demand;

    public Region(String name, int supply, int demand) {
        this.name = name;
        this.supply = supply;
        this.demand = demand;
    }

    public int getBalance() {
        return supply - demand;
    }
}