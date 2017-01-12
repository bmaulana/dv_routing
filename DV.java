import java.lang.Math;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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

    public DV() {
        allowPReverse = false;
        allowExpire = false;
        updateInterval = 1;
        routingTable = new HashMap<Integer, DVRoutingTableEntry>();
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
        routingTable.put(router.getId(), new DVRoutingTableEntry(router.getId(), LOCAL, 0, 1));
        maxRouterId = router.getId();
    }

    public int getNextHop(int destination) {
        if (!routingTable.containsKey(destination)) {
            return UNKNOWN;
        }
        DVRoutingTableEntry entry = routingTable.get(destination);
        if (entry.getMetric() > INFINITY || !router.getInterfaceState(entry.getInterface())) {
            return UNKNOWN;
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
            HashMap<Integer, DVRoutingTableEntry> routingTableCopy = (HashMap<Integer, DVRoutingTableEntry>) routingTable.clone();
            Iterator it = routingTableCopy.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                DVRoutingTableEntry entry = (DVRoutingTableEntry) pair.getValue();
                if(entry.getInterface() == intrface) {
                    entry.setMetric(INFINITY);
                    entry.setTime(router.getCurrentTime());
                }
                routingTable.put(entry.getDestination(), entry);
                it.remove(); // avoids a ConcurrentModificationException
            }
        }
    }

    public Packet generateRoutingPacket(int iface) {
        if (!router.getInterfaceState(iface)) {
            return null;
        }
        Payload payload = new Payload();

        // Iterate over routing table
        HashMap<Integer, DVRoutingTableEntry> routingTableCopy = (HashMap<Integer, DVRoutingTableEntry>) routingTable.clone();
        Iterator it = routingTableCopy.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            payload.addEntry(pair.getValue());
            it.remove(); // avoids a ConcurrentModificationException
        }

        RoutingPacket routingPacket = new RoutingPacket(router.getId(), Packet.BROADCAST);
        routingPacket.setPayload(payload);
        return routingPacket;
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

            if (destination == router.getId()) {
                continue;
            }

            if (!routingTable.containsKey(destination) || routingTable.get(destination).getInterface() == iface ||
                    entry.getMetric() + iweight < routingTable.get(destination).getMetric()) {

                int weight = entry.getMetric() + iweight > INFINITY ? INFINITY : entry.getMetric() + iweight;
                routingTable.put(destination, new DVRoutingTableEntry(destination, iface, weight, router.getCurrentTime()));
                if(maxRouterId < destination) {
                    maxRouterId = destination;
                }
            }
        }
    }

    public void showRoutes() {
        System.out.println("Router " + router.getId());

        // Iterate over routing table
        for(int i = 0; i <= maxRouterId; i++) {
            if(routingTable.containsKey(i)) {
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
}

