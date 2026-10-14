package com.nexgate.velocity.antibot;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * High-performance CIDR matcher supporting IPv4 and IPv6 subnets.
 */
public class CidrMatcher {
    private final InetAddress networkAddress;
    private final int prefixLength;
    private final byte[] mask;

    public CidrMatcher(String cidr) throws IllegalArgumentException {
        Objects.requireNonNull(cidr, "CIDR string cannot be null");
        String clean = cidr.trim();
        int slashIdx = clean.indexOf('/');
        try {
            if (slashIdx == -1) {
                this.networkAddress = InetAddress.getByName(clean);
                this.prefixLength = networkAddress.getAddress().length * 8;
            } else {
                this.networkAddress = InetAddress.getByName(clean.substring(0, slashIdx));
                this.prefixLength = Integer.parseInt(clean.substring(slashIdx + 1));
            }
            this.mask = createMask(networkAddress.getAddress().length, prefixLength);
        } catch (UnknownHostException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CIDR format: " + cidr, e);
        }
    }

    public boolean matches(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String cleanIp = ip.trim();
        if (cleanIp.startsWith("/")) {
            cleanIp = cleanIp.substring(1);
        }
        int portIdx = cleanIp.lastIndexOf(':');
        int dotIdx = cleanIp.indexOf('.');
        if (dotIdx != -1 && portIdx != -1) {
            cleanIp = cleanIp.substring(0, portIdx);
        }

        try {
            InetAddress candidate = InetAddress.getByName(cleanIp);
            byte[] candidateBytes = candidate.getAddress();
            byte[] networkBytes = networkAddress.getAddress();

            if (candidateBytes.length != networkBytes.length) {
                return false; // IPv4 vs IPv6 mismatch
            }

            for (int i = 0; i < candidateBytes.length; i++) {
                if ((candidateBytes[i] & mask[i]) != (networkBytes[i] & mask[i])) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static byte[] createMask(int length, int prefix) {
        byte[] mask = new byte[length];
        int remaining = prefix;
        for (int i = 0; i < length; i++) {
            if (remaining >= 8) {
                mask[i] = (byte) 0xFF;
                remaining -= 8;
            } else if (remaining > 0) {
                mask[i] = (byte) (0xFF << (8 - remaining));
                remaining = 0;
            } else {
                mask[i] = 0x00;
            }
        }
        return mask;
    }

    public String getNetworkAddress() {
        return networkAddress.getHostAddress();
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    @Override
    public String toString() {
        return networkAddress.getHostAddress() + "/" + prefixLength;
    }
}
