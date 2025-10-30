package com.outlaweco.api;

import org.bukkit.OfflinePlayer;

import java.util.UUID;

public interface EconomyService {

    double getBalance(UUID playerId);

    double getBalance(OfflinePlayer player);

    void setBalance(UUID playerId, double amount);

    void deposit(UUID playerId, double amount);

    boolean withdraw(UUID playerId, double amount);

    boolean has(UUID playerId, double amount);
}
