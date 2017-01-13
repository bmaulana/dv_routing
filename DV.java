import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;

public class DV implements RoutingAlgorithm {

    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    private Router router;
    private int updateInterval;
    private boolean poisonReverse;
    private boolean expire;

    private HashMap<Integer, DVRoutingTableEntry> routingTable;
    private int maxRouterId;

    public DV() {
        updateInterval = 1;
        poisonReverse = false;
        expire = false;
        routingTable = new HashMap<>();
    }

    public void setRouterObject(Router obj) {
        router = obj;
    }

    public void setUpdateInterval(int u) {
        updateInterval = u;
    }

    public void setAllowPReverse(boolean flag) {
        poisonReverse = flag;
    }

    public void setAllowExpire(boolean flag) {
        expire = flag;
    }

    public void initalise() {
        routingTable.put(router.getId(), new DVRoutingTableEntry(router.getId(), LOCAL, 0, 1));
        maxRouterId = router.getId();
    }

    public int getNextHop(int destination) {
        if (!routingTable.containsKey(destination)) {
            return UNKNOWN; // destination not in routing table
        }

        DVRoutingTableEntry entry = routingTable.get(destination); // routing table entry to destination
        if (entry.getMetric() >= INFINITY || !router.getInterfaceState(entry.getInterface())) {
            return UNKNOWN; // path to destination down
        }
        return entry.getInterface(); // interface of next hop to destination
    }

    public void tidyTable() {
        // Get list of down interfaces
        ArrayList<Integer> interfaces = new ArrayList<>();
        for (Link link : router.getLinks()) {
            if (link.isUp()) {
                continue;
            }
            interfaces.add(link.getRouter(1) == router.getId() ? link.getInterface(1) : link.getInterface(0));
        }

        // Iterate over routing table and 'remove' entries where interface is down by setting metric to INFINITY
        for (int i = 0; i <= maxRouterId; i++) {
            if (!routingTable.containsKey(i)) {
                continue;
            }

            DVRoutingTableEntry entry = routingTable.get(i);
            if (interfaces.contains(entry.getInterface())) {
                entry.setMetric(INFINITY);
            }
        }

        // Expire entries (remove from routing table) where metric has been INFINITY for more than 3 * updateInterval
        if (expire) {
            for (int i = 0; i <= maxRouterId; i++) {
                if (!routingTable.containsKey(i)) {
                    continue;
                }

                DVRoutingTableEntry entry = routingTable.get(i);
                if (entry.getMetric() == INFINITY && router.getCurrentTime() - entry.getTime() > 3 * updateInterval) {
                    routingTable.remove(entry.getDestination());
                }
            }
        }
    }

    public Packet generateRoutingPacket(int iface) {
        if (!router.getInterfaceState(iface)) {
            return null; // If interface down
        }

        // Initialise packet
        Packet packet = new RoutingPacket(router.getId(), Packet.BROADCAST);
        Payload payload = new Payload();

        // Add routing table to payload
        for (int i = 0; i <= maxRouterId; i++) {
            if (!routingTable.containsKey(i)) {
                continue;
            }

            DVRoutingTableEntry entry = routingTable.get(i).copy();
            if (poisonReverse && entry.getInterface() == iface) {
                entry.setMetric(INFINITY);
            }
            payload.addEntry(entry);
        }

        packet.setPayload(payload);
        return packet;
    }

    public void processRoutingPacket(Packet p, int iface) {
        if (p.getSource() == router.getId()) {
            return; // Ignore packets from itself
        }

        // Go through each entry in received packet
        Enumeration entries = p.getPayload().getData().elements();
        while (entries.hasMoreElements()) {
            DVRoutingTableEntry entry = (DVRoutingTableEntry) entries.nextElement();

            // If routing table do not yet has entry to destination or path proposed by received packet has less cost
            // (metrics) than path in routing table, update routing table.
            int destination = entry.getDestination();
            if (!routingTable.containsKey(destination) || routingTable.get(destination).getInterface() == iface ||
                    entry.getMetric() + router.getInterfaceWeight(iface) < routingTable.get(destination).getMetric()) {

                // new metric = metric from packet + interface weight
                int weight = entry.getMetric() + router.getInterfaceWeight(iface) > INFINITY ? INFINITY :
                        entry.getMetric() + router.getInterfaceWeight(iface);

                // if new metric is infinity and expiration is on, ignore it to not reset timer
                if (expire && weight == INFINITY) {
                    if (!routingTable.containsKey(destination) || routingTable.get(destination).getMetric() == INFINITY) {
                        continue;
                    }
                }

                // update routing table
                routingTable.put(destination, new DVRoutingTableEntry(destination, iface, weight, router.getCurrentTime()));
                if (maxRouterId < destination) {
                    maxRouterId = destination;
                }
            }
        }
    }

    public void showRoutes() {
        // Print routing table
        System.out.println("Router " + router.getId());
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

    public DVRoutingTableEntry copy() {
        return new DVRoutingTableEntry(destination, intrface, metric, time);
    }
}