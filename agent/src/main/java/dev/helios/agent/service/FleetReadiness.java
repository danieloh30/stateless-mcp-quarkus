package dev.helios.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;

import javax.net.ssl.*;

/** Passive observation only: the Kubernetes API never activates the MCP service. */
@ApplicationScoped
public class FleetReadiness {
    public record Ready(Integer count, String source) {}

    private static final Path SA = Path.of("/var/run/secrets/kubernetes.io/serviceaccount");
    @Inject ObjectMapper mapper;
    private volatile HttpClient client;

    public Ready read(String hosts) {
        if (hosts.isBlank()) return new Ready(null, "not configured (single-instance dev mode)");
        if (Files.exists(SA.resolve("token")) && !hosts.contains(",")) return platform(hosts);
        try {
            int count = 0;
            for (String host : hosts.split(","))
                count += InetAddress.getAllByName(host.trim()).length;
            return new Ready(count, "DNS address estimate; not platform readiness");
        } catch (UnknownHostException e) {
            return new Ready(null, "DNS unavailable; zero is not confirmed");
        }
    }

    private Ready platform(String service) {
        try {
            if (!service.matches("[a-z0-9-]+"))
                return new Ready(null, "unsupported discovery service");
            if (client == null) {
                var store = KeyStore.getInstance(KeyStore.getDefaultType());
                store.load(null, null);
                try (var in = Files.newInputStream(SA.resolve("ca.crt"))) {
                    int i = 0;
                    for (var cert :
                            CertificateFactory.getInstance("X.509").generateCertificates(in))
                        store.setCertificateEntry("cluster-" + i++, cert);
                }
                var trust =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trust.init(store);
                var ssl = SSLContext.getInstance("TLS");
                ssl.init(null, trust.getTrustManagers(), null);
                client =
                        HttpClient.newBuilder()
                                .sslContext(ssl)
                                .connectTimeout(Duration.ofSeconds(2))
                                .build();
            }
            String ns = Files.readString(SA.resolve("namespace")).trim();
            var uri =
                    URI.create(
                            "https://kubernetes.default.svc/api/v1/namespaces/"
                                    + ns
                                    + "/endpoints/"
                                    + service);
            var req =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(2))
                            .header(
                                    "Authorization",
                                    "Bearer " + Files.readString(SA.resolve("token")).trim())
                            .GET()
                            .build();
            var response = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                return new Ready(
                        null,
                        "platform observation unavailable (HTTP " + response.statusCode() + ")");
            int count = 0;
            for (var subset : mapper.readTree(response.body()).path("subsets"))
                count += subset.path("addresses").size();
            return new Ready(count, "Kubernetes ready endpoints (passive)");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Ready(null, "platform observation unavailable; zero is not confirmed");
        }
    }
}
