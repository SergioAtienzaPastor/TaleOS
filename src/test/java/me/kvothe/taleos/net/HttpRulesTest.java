package me.kvothe.taleos.net;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRulesTest {
    @Test
    void matchesCidrDomainsAndWildcards() throws Exception {
        List<InetAddress> loopback = List.of(InetAddress.getByName("127.0.0.1"));
        assertTrue(HttpRules.parse("127.0.0.0/8", "allow", 0).matches("localhost", loopback));
        assertFalse(HttpRules.parse("10.0.0.0/8", "allow", 0).matches("localhost", loopback));
        assertTrue(HttpRules.parse("*.example.com", "allow", 0).matches("www.example.com", loopback));
        assertFalse(HttpRules.parse("*.example.com", "allow", 0).matches("example.com", loopback));
        assertFalse(HttpRules.parse("*.example.com", "allow", 0).matches("badexample.com", loopback));
        assertTrue(HttpRules.parse("example.com", "allow", 0).matches("EXAMPLE.COM", loopback));
    }

    @Test
    void recognizesNonPublicIpv4AndIpv6Ranges() throws Exception {
        String[] denied = {
                "0.0.0.0", "10.1.2.3", "100.64.0.1", "127.0.0.1", "169.254.1.1",
                "172.16.0.1", "192.0.2.1", "192.168.1.1", "198.18.0.1", "198.51.100.1",
                "203.0.113.1", "224.0.0.1", "255.255.255.255", "::", "::1", "100::1",
                "64:ff9b::7f00:1", "2001:db8::1", "fc00::1", "fe80::1", "ff02::1"
        };
        for (String address : denied) {
            assertTrue(HttpRules.isPrivate(InetAddress.getByName(address)), address);
        }
        assertFalse(HttpRules.isPrivate(InetAddress.getByName("8.8.8.8")));
        assertFalse(HttpRules.isPrivate(InetAddress.getByName("2001:4860:4860::8888")));
    }

    @Test
    void rejectsInvalidCidrAndActions() {
        assertThrows(IllegalArgumentException.class, () -> HttpRules.parse("127.0.0.1/33", "allow", 0));
        assertThrows(IllegalArgumentException.class, () -> HttpRules.parse("example.com", "perhaps", 0));
        assertThrows(IllegalArgumentException.class, () -> HttpRules.parse("*", "allow", -1));
    }
}
