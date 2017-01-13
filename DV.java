import java.util.Enumeration;
import java.util.HashMap;

public class DV implements RoutingAlgorithm {

    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    private Router router;
    private int updateInterval;
    private boolean allowPReverse;
    private boolean allowExpire;

    private HashMap<Integer, DVRoutingTableEntry> routingTable;
    private int maxRouterId;
    private int gc;
    private int timeout;

    public DV() {
        allowPReverse = false;
        allowExpire = false;
        updateInterval = 1;
        routingTable = new HashMap<Integer, DVRoutingTableEntry>();
        gc = 3;
        timeout = 4;
    }

    public void setRouterObject(Router obj) {
        router = obj;
    }

    public void setUpdateInterval(int u) {
        updateInterval = u;
        gc = 3 * u;
        timeout = 4 * u;
    }

    public void setAllowPReverse(boolean flag) {
        allowPReverse = flag;
    }

    public void setAllowExpire(boolean flag) {
        allowExpire = flag;
    }

    public void initalise() {
        routingTable.put(router.getId(), new DVRoutingTableEntry(router.getId(), LOCAL, 0, 1));
        maxRouterId = router.getId();
    }

    public int getNextHop(int destination) {
        if (!routingTable.containsKey(destination)) {
            return UNKNOWN; // destination not in routing table
        }

        DVRoutingTableEntry entry = routingTable.get(destination);
        if (entry.getMetric() >= INFINITY || !router.getInterfaceState(entry.getInterface())) {
            return UNKNOWN; // path to destination down
        }
        return entry.getInterface();
    }

    public void tidyTable() {
        // remove entries where interface is down by setting metric to INFINITY
        Link[] links = router.getLinks();
        for (Link link : links) {
            if (link.isUp()) {
                continue;
            }
            int intrface = link.getRouter(1) == router.getId() ? link.getInterface(1) : link.getInterface(0);

            // Iterate over routing table
            for (int i = 0; i <= maxRouterId; i++) {
                if (routingTable.containsKey(i)) {
                    DVRoutingTableEntry entry = routingTable.get(i);
                    if (entry.getInterface() == intrface) {
                        entry.setMetric(INFINITY);
                        entry.setTime(router.getCurrentTime());
                    }
                }
            }
        }

        if (allowExpire) {
            // Iterate over routing table
            for (int i = 0; i <= maxRouterId; i++) {
                if (routingTable.containsKey(i)) {
                    DVRoutingTableEntry entry = routingTable.get(i);
                    if (entry.getMetric() == INFINITY && ((router.getCurrentTime() - entry.getTime()) > gc)) {
                        routingTable.remove(entry.getDestination());
                    } else if ((router.getCurrentTime() - entry.getTime()) > timeout && entry.getInterface() != LOCAL) {
                        entry.setMetric(INFINITY);
                        entry.setTime(router.getCurrentTime());
                    }
                }
            }
        }
    }

    public Packet generateRoutingPacket(int iface) {
        if (!router.getInterfaceState(iface)) {
            return null;
        }

        // init
        Packet packet = new RoutingPacket(router.getId(), Packet.BROADCAST);
        Payload payload = new Payload();

        // Iterate over routing table
        for (int i = 0; i <= maxRouterId; i++) {
            if (routingTable.containsKey(i)) {
                DVRoutingTableEntry entry = routingTable.get(i).copy();
                if (allowPReverse && entry.getInterface() == iface) {
                    entry.setMetric(INFINITY);
                }
                payload.addEntry(entry);
            }
        }

        packet.setPayload(payload);
        return packet;
    }

    public void processRoutingPacket(Packet p, int iface) {
        if (p.getSource() == router.getId()) {
            return;
        }

        Payload payload = p.getPayload();
        int iweight = router.getInterfaceWeight(iface);
        Enumeration entries = payload.getData().elements();

        while (entries.hasMoreElements()) {
            DVRoutingTableEntry entry = (DVRoutingTableEntry) entries.nextElement();
            int destination = entry.getDestination();

            if (!routingTable.containsKey(destination) || routingTable.get(destination).getInterface() == iface ||
                    entry.getMetric() + iweight < routingTable.get(destination).getMetric()) {

                if (allowExpire && entry.getMetric() == INFINITY) {
                    if (!routingTable.containsKey(destination) || routingTable.get(destination).getMetric() == INFINITY) {
                        continue;
                    }
                }

                int weight = entry.getMetric() + iweight > INFINITY ? INFINITY : entry.getMetric() + iweight;
                routingTable.put(destination, new DVRoutingTableEntry(destination, iface, weight, router.getCurrentTime()));
                if (maxRouterId < destination) {
                    maxRouterId = destination;
                }
            }
        }
    }

    public void showRoutes() {
        System.out.println("Router " + router.getId());

        // Iterate over routing table
        for (int i = 0; i <= maxRouterId; i++) {
            if (routingTable.containsKey(i)) {
                System.out.println(routingTable.get(i).toString());
            }
        }
    }
}

class DVRoutingTableEntry implements RoutingTableEntry {

    private int destination;
    private int intrface;
    private int metric;
    private int time;

    public DVRoutingTableEntry(int d, int i, int m, int t) {
        destination = d;
        intrface = i;
        metric = m;
        time = t;
    }

    public int getDestination() {
        return destination;
    }

    public void setDestination(int d) {
        destination = d;
    }

    public int getInterface() {
        return intrface;
    }

    public void setInterface(int i) {
        intrface = i;
    }

    public int getMetric() {
        return metric;
    }

    public void setMetric(int m) {
        metric = m;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int t) {
        time = t;
    }

    public String toString() {
        return "d " + destination + " i " + intrface + " m " + metric;
    }

    DVRoutingTableEntry copy() {
        return new DVRoutingTableEntry(destination, intrface, metric, time);
    }
}

