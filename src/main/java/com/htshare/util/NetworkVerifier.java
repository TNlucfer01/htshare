package com.htshare.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Network verification utility to ensure devices are on the same network
 */
public class NetworkVerifier {
    private static final Logger logger = LoggerFactory.getLogger(NetworkVerifier.class);

    /**
     * Information about a network interface
     */
    public static class NetworkInfo {
        private final String interfaceName;
        private final String displayName;
        private final InetAddress address;
        private final InetAddress subnet;
        private final short prefixLength;
        
        public NetworkInfo(String interfaceName, String displayName, 
                          InetAddress address, InetAddress subnet, short prefixLength) {
            this.interfaceName = interfaceName;
            this.displayName = displayName;
            this.address = address;
            this.subnet = subnet;
            this.prefixLength = prefixLength;
        }
        
        public String getInterfaceName() { return interfaceName; }
        public String getDisplayName() { return displayName; }
        public InetAddress getAddress() { return address; }
        public InetAddress getSubnet() { return subnet; }
        public short getPrefixLength() { return prefixLength; }
        
        public String getNetworkRange() {
            return subnet.getHostAddress() + "/" + prefixLength;
        }
        
        @Override
        public String toString() {
            return String.format("%s (%s): %s - Network: %s/%d", 
                displayName, interfaceName, address.getHostAddress(), 
                subnet.getHostAddress(), prefixLength);
        }
    }

    /**
     * Get all active network interfaces with IPv4 addresses
     */
    public static List<NetworkInfo> getActiveNetworks() throws SocketException {
        List<NetworkInfo> networks = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            
            // Skip loopback and inactive interfaces
            if (iface.isLoopback() || !iface.isUp()) {
                continue;
            }
            
            // Get all addresses for this interface
            List<InterfaceAddress> interfaceAddresses = iface.getInterfaceAddresses();
            
            for (InterfaceAddress ifAddr : interfaceAddresses) {
                InetAddress addr = ifAddr.getAddress();
                
                // Only process IPv4 addresses
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    InetAddress subnet = calculateNetworkAddress(addr, ifAddr.getNetworkPrefixLength());
                    
                    NetworkInfo info = new NetworkInfo(
                        iface.getName(),
                        iface.getDisplayName(),
                        addr,
                        subnet,
                        ifAddr.getNetworkPrefixLength()
                    );
                    
                    networks.add(info);
                    logger.debug("Found network: {}", info);
                }
            }
        }
        
        return networks;
    }

    /**
     * Get the primary network interface (most likely the one connected to internet)
     */
    public static NetworkInfo getPrimaryNetwork() throws SocketException, UnknownHostException {
        List<NetworkInfo> networks = getActiveNetworks();
        
        if (networks.isEmpty()) {
            throw new SocketException("No active network interfaces found");
        }
        
        // Try to find the interface with default gateway
        // Priority: Ethernet > WiFi > Others
        for (NetworkInfo network : networks) {
            String name = network.getInterfaceName().toLowerCase();
            String display = network.getDisplayName().toLowerCase();
            
            // Prefer Ethernet connections
            if (name.contains("eth") || display.contains("ethernet")) {
                logger.info("Selected primary network (Ethernet): {}", network);
                return network;
            }
        }
        
        // Then WiFi
        for (NetworkInfo network : networks) {
            String name = network.getInterfaceName().toLowerCase();
            String display = network.getDisplayName().toLowerCase();
            
            if (name.contains("wlan") || name.contains("wi-fi") || 
                display.contains("wireless") || display.contains("wi-fi")) {
                logger.info("Selected primary network (WiFi): {}", network);
                return network;
            }
        }
        
        // Return first available
        NetworkInfo primary = networks.get(0);
        logger.info("Selected primary network (default): {}", primary);
        return primary;
    }

    /**
     * Check if a client IP is on the same network as the server
     */
    public static boolean isSameNetwork(InetAddress serverAddr, InetAddress clientAddr, 
                                       short prefixLength) {
        try {
            byte[] serverBytes = serverAddr.getAddress();
            byte[] clientBytes = clientAddr.getAddress();
            
            // Both must be IPv4
            if (serverBytes.length != 4 || clientBytes.length != 4) {
                return false;
            }
            
            // Calculate subnet mask
            int mask = 0xffffffff << (32 - prefixLength);
            
            // Convert IP addresses to integers
            int serverInt = byteArrayToInt(serverBytes);
            int clientInt = byteArrayToInt(clientBytes);
            
            // Compare network portions
            return (serverInt & mask) == (clientInt & mask);
            
        } catch (Exception e) {
            logger.error("Error checking network match", e);
            return false;
        }
    }

    /**
     * Calculate network address from IP and prefix length
     */
    public static InetAddress calculateNetworkAddress(InetAddress address, short prefixLength) {
        try {
            byte[] addrBytes = address.getAddress();
            int mask = 0xffffffff << (32 - prefixLength);
            int addrInt = byteArrayToInt(addrBytes);
            int networkInt = addrInt & mask;
            
            return InetAddress.getByAddress(intToByteArray(networkInt));
        } catch (Exception e) {
            logger.error("Error calculating network address", e);
            return address;
        }
    }

    /**
     * Calculate broadcast address from IP and prefix length
     */
    public static InetAddress calculateBroadcastAddress(InetAddress address, short prefixLength) {
        try {
            byte[] addrBytes = address.getAddress();
            int mask = 0xffffffff << (32 - prefixLength);
            int addrInt = byteArrayToInt(addrBytes);
            int broadcastInt = addrInt | ~mask;
            
            return InetAddress.getByAddress(intToByteArray(broadcastInt));
        } catch (Exception e) {
            logger.error("Error calculating broadcast address", e);
            return address;
        }
    }

    /**
     * Get network type description
     */
    public static String getNetworkType(NetworkInfo network) {
        String name = network.getInterfaceName().toLowerCase();
        String display = network.getDisplayName().toLowerCase();
        
        if (name.contains("eth") || display.contains("ethernet")) {
            return "Ethernet";
        } else if (name.contains("wlan") || name.contains("wi-fi") || 
                   display.contains("wireless") || display.contains("wi-fi")) {
            return "Wi-Fi";
        } else if (name.contains("lo")) {
            return "Loopback";
        } else if (name.contains("vir") || display.contains("virtual")) {
            return "Virtual";
        }
        return "Other";
    }

    /**
     * Validate network connectivity
     */
    public static NetworkValidationResult validateNetwork() {
        try {
            List<NetworkInfo> networks = getActiveNetworks();
            
            if (networks.isEmpty()) {
                return new NetworkValidationResult(false, 
                    "No active network connections found. Please connect to a network.", 
                    null);
            }
            
            NetworkInfo primary = getPrimaryNetwork();
            String networkType = getNetworkType(primary);
            
            // Check if it's a usable network (not virtual or loopback)
            if (networkType.equals("Virtual") || networkType.equals("Loopback")) {
                return new NetworkValidationResult(false,
                    "Connected to virtual/loopback network. Please use a real network connection.",
                    primary);
            }
            
            // Check if private IP range (local network)
            if (!isPrivateIP(primary.getAddress())) {
                logger.warn("Public IP detected: {}", primary.getAddress().getHostAddress());
            }
            
            String message = String.format("Connected to %s network: %s", 
                networkType, primary.getAddress().getHostAddress());
            
            return new NetworkValidationResult(true, message, primary);
            
        } catch (Exception e) {
            logger.error("Network validation failed", e);
            return new NetworkValidationResult(false, 
                "Network validation failed: " + e.getMessage(), 
                null);
        }
    }

    /**
     * Check if IP is in private range
     */
    public static boolean isPrivateIP(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) return false;
        
        int b1 = bytes[0] & 0xFF;
        int b2 = bytes[1] & 0xFF;
        
        // 10.0.0.0/8
        if (b1 == 10) return true;
        
        // 172.16.0.0/12
        if (b1 == 172 && (b2 >= 16 && b2 <= 31)) return true;
        
        // 192.168.0.0/16
        if (b1 == 192 && b2 == 168) return true;
        
        return false;
    }

    /**
     * Result of network validation
     */
    public static class NetworkValidationResult {
        private final boolean valid;
        private final String message;
        private final NetworkInfo networkInfo;
        
        public NetworkValidationResult(boolean valid, String message, NetworkInfo networkInfo) {
            this.valid = valid;
            this.message = message;
            this.networkInfo = networkInfo;
        }
        
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public NetworkInfo getNetworkInfo() { return networkInfo; }
    }

    // Helper methods
    private static int byteArrayToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               (bytes[3] & 0xFF);
    }

    private static byte[] intToByteArray(int value) {
        return new byte[] {
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }
}