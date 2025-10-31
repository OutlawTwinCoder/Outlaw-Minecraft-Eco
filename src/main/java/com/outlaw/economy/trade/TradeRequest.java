package com.outlaw.economy.trade;

import java.time.Instant;
import java.util.UUID;

public class TradeRequest {
    private final UUID requester;
    private final UUID target;
    private final Instant createdAt;

    public TradeRequest(UUID requester, UUID target) {
        this.requester = requester;
        this.target = target;
        this.createdAt = Instant.now();
    }

    public UUID getRequester() {
        return requester;
    }

    public UUID getTarget() {
        return target;
    }

    public boolean isExpired(long seconds) {
        return Instant.now().isAfter(createdAt.plusSeconds(seconds));
    }
}
