package net.ezforever.udpmote;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class ActiveServersList {
    private final Map<Integer, UdpwiiServer> activeServers = new TreeMap<>();
    private boolean changed = false;

    ActiveServersList() {
    }

    public synchronized void addServer(UdpwiiServer server) {
        if (this.activeServers.get(server.id) == null) {
            this.changed = true;
        }
        this.activeServers.put(server.id, server);
    }

    public synchronized void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, UdpwiiServer>> it = this.activeServers.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().timestamp >= 5000) {
                it.remove();
                this.changed = true;
            }
        }
    }

    public synchronized boolean getServerListIfChanged(Map<Integer, UdpwiiServer> servers) {
        boolean z = false;
        synchronized (this) {
            if (this.changed) {
                servers.clear();
                servers.putAll(this.activeServers);
                this.changed = false;
                z = true;
            }
        }
        return z;
    }
}
