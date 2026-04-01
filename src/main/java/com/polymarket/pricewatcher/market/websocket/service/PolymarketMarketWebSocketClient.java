package com.polymarket.pricewatcher.market.websocket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.pricewatcher.config.PolymarketProperties;
import com.polymarket.pricewatcher.market.service.ConfiguredTrackedMarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "polymarket.websocket", name = "enabled", havingValue = "true")
public class PolymarketMarketWebSocketClient implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PolymarketMarketWebSocketClient.class);

    private final ReactorNettyWebSocketClient webSocketClient = new ReactorNettyWebSocketClient();
    private final ObjectMapper objectMapper;
    private final PolymarketProperties polymarketProperties;
    private final ConfiguredTrackedMarketService trackedMarketService;
    private final PolymarketMarketMessageHandler messageHandler;

    private volatile Disposable connectionLoop;
    private volatile boolean running;

    public PolymarketMarketWebSocketClient(
            ObjectMapper objectMapper,
            PolymarketProperties polymarketProperties,
            ConfiguredTrackedMarketService trackedMarketService,
            PolymarketMarketMessageHandler messageHandler
    ) {
        this.objectMapper = objectMapper;
        this.polymarketProperties = polymarketProperties;
        this.trackedMarketService = trackedMarketService;
        this.messageHandler = messageHandler;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        List<String> trackedAssetIds = trackedMarketService.getTrackedAssetIds();
        Duration reconnectDelay = polymarketProperties.getWebsocket().getReconnectDelay();

        connectionLoop = Mono.defer(() -> connectOnce(trackedAssetIds))
                .onErrorResume(exception -> {
                    log.warn(
                            "Polymarket market WebSocket session failed. Reconnecting in {}",
                            reconnectDelay,
                            exception
                    );
                    return Mono.empty();
                })
                .repeatWhen(repeat -> repeat.delayElements(reconnectDelay))
                .subscribe();

        running = true;
        log.info(
                "Started Polymarket market WebSocket client for {} tracked assets",
                trackedAssetIds.size()
        );
    }

    @Override
    public void stop() {
        Disposable currentLoop = this.connectionLoop;
        if (currentLoop != null) {
            currentLoop.dispose();
        }

        this.connectionLoop = null;
        this.running = false;
        log.info("Stopped Polymarket market WebSocket client");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private Mono<Void> connectOnce(List<String> trackedAssetIds) {
        URI websocketUri = URI.create(polymarketProperties.getWebsocket().getUrl());
        String subscriptionMessage = subscriptionMessage(trackedAssetIds);

        return webSocketClient.execute(websocketUri, session -> {
            log.info("Connected to Polymarket market WebSocket: {}", websocketUri);

            Flux<String> outboundText = Flux.concat(
                    Mono.just(subscriptionMessage),
                    Flux.interval(polymarketProperties.getWebsocket().getPingInterval())
                            .map(ignored -> heartbeatMessage())
            );

            Mono<Void> send = session.send(outboundText.map(session::textMessage))
                    .doOnSubscribe(ignored -> log.info(
                            "Subscribed to {} Polymarket assetIds with customFeatureEnabled={}",
                            trackedAssetIds.size(),
                            polymarketProperties.getWebsocket().isCustomFeatureEnabled()
                    ));

            Mono<Void> receive = session.receive()
                    .map(webSocketMessage -> webSocketMessage.getPayloadAsText())
                    .concatMap(payload -> Mono.fromRunnable(() -> messageHandler.handlePayload(payload))
                            .subscribeOn(Schedulers.boundedElastic()))
                    .then();

            return send.and(receive);
        }).doFinally(signalType -> log.warn("Polymarket market WebSocket session ended: {}", signalType));
    }

    String subscriptionMessage(List<String> trackedAssetIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assets_ids", trackedAssetIds);
        payload.put("type", "market");
        payload.put("custom_feature_enabled", polymarketProperties.getWebsocket().isCustomFeatureEnabled());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Polymarket market subscription payload", exception);
        }
    }

    String heartbeatMessage() {
        return "PING";
    }
}
