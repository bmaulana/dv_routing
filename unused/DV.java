import java.lang.Math;
import java.util.ArrayList;

public class DV implements RoutingAlgorithm {

    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    private Router router;
    private int updateInterval;
    private boolean allowPReverse;
    private boolean allowExpire;

    private ArrayList<DVRoutingTableEntry> routingTable;

    public DV() {
        allowPReverse = false;
        allowExpire = false;
        updateInterval = 1;
        routingTable = new ArrayList<>();
    }

    public void setRouterObject(Router obj) {
        router = obj;
    }

    public void setUpdateInterval(int u) {
        updateInterval = u;
    }

    public void setAllowPReverse(boolean flag) {
        allowPReverse = flag;
    }

    public void setAllowExpire(boolean flag) {
        allowExpire = flag;
    }

    public void initalise() {
        routingTable.add(new DVRoutingTableEntry(router.getId(), LOCAL, 0, 1));
    }

    public int getNextHop(int destination) {
        return 0;
    }

    public void tidyTable() {
    }

    public Packet generateRoutingPacket(int iface) {
        return null;
    }

    public void processRoutingPacket(Packet p, int iface) {
    }

    public void showRoutes() {
    }
}

class DVRoutingTableEntry implements RoutingTableEntry {

    public DVRoutingTableEntry(int d, int i, int m, int t) {
    }

    public int getDestination() {
        return 0;
    }

    public void setDestination(int d) {
    }

    public int getInterface() {
        return 0;
    }

    public void setInterface(int i) {
    }

    public int getMetric() {
        return 0;
    }

    public void setMetric(int m) {
    }

    public int getTime() {
        return 0;
    }

    public void setTime(int t) {
    }

    public String toString() {
        return "";
    }
}

