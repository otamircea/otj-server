package com.opentable.server;

import java.time.Duration;
import java.util.Optional;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opentable.bucket.BucketLog;

@SuppressWarnings({"PMD.MoreThanOneLogger"})
public class OtSecureRequestCustomizer extends SecureRequestCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(OtSecureRequestCustomizer.class);
    private static final Logger BUCKET_LOG = BucketLog.of(OtSecureRequestCustomizer.class, 1, Duration.ofSeconds(10)); // 1 per 10 second
    private final ServerConnectorConfig config;
    private Optional<BiConsumer<SSLEngine, Request>> sniErrorCallback = Optional.empty();

    public OtSecureRequestCustomizer(ServerConnectorConfig config) {
        super(config.isSniRequired(), config.isSniHostCheck(), -1, false);
        this.config = config;
    }

    @Override
    protected void customize(SSLEngine sslEngine, Request request) {
        final String sniHost = (String) sslEngine.getSession().getValue(SslContextFactory.Server.SNI_HOST);
        if ((sniHost != null) || !config.isAllowEmptySni()) {
            try {
                super.customize(sslEngine, request);  // will default to jetty 10 defaults ie - different sni behaviour from 9
            } catch (BadMessageException ex) {
                LOG.error("Invalid SNI: Host={}, SNI={}, SNI Certificate={}, peerHost={}, peerPort={}",
                    request.getServerName(),
                    sniHost,
                    sslEngine.getSession().getValue(X509_CERT),
                    sslEngine.getPeerHost(),
                    sslEngine.getPeerPort());
                sniErrorCallback.ifPresent(c -> c.accept(sslEngine, request));
            }
        } else {
            BUCKET_LOG.warn("SNIHOST: Host={}, SNI=null, SNI Certificate={}, peerHost={}, peerPort={}",
                request.getServerName(),
                sslEngine.getSession().getValue(X509_CERT),
                sslEngine.getPeerHost(),
                sslEngine.getPeerPort());
        }
    }

    public void setSniErrorCallback(BiConsumer<SSLEngine, Request> sniErrorCallback) {
        this.sniErrorCallback = Optional.ofNullable(sniErrorCallback);
    }
}
