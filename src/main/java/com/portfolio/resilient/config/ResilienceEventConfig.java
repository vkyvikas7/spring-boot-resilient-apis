package com.portfolio.resilient.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceEventConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceEventConfig.class);

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventLogger() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                subscribe(event.getAddedEntry());
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
                // Registry lifecycle hook. The breaker is gone; nothing else to detach.
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
                subscribe(event.getNewEntry());
            }
        };
    }

    private static void subscribe(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "Circuit breaker '{}' transitioned from {} to {}",
                        event.getCircuitBreakerName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onError(event -> log.info(
                        "Circuit breaker '{}' recorded a failure: {}",
                        event.getCircuitBreakerName(),
                        event.getThrowable().toString()))
                .onCallNotPermitted(event -> log.warn(
                        "Circuit breaker '{}' rejected a call while {}",
                        event.getCircuitBreakerName(),
                        circuitBreaker.getState()));
    }
}
