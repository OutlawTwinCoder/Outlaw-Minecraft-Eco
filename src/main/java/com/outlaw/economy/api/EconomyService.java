package com.outlaw.economy.api;

import java.util.UUID;

public interface EconomyService {
    double getBalance(UUID playerId);
    boolean deposit(UUID playerId, double amount, String reason);
    boolean withdraw(UUID playerId, double amount, String reason);
    String format(double amount);
    String currencyCode();
}
