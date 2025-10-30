package com.outlaweco.api;

import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.UUID;

public final class EconomyAPI {

    private static EconomyService economyService;

    private EconomyAPI() {
    }

    public static void register(EconomyService service) {
        economyService = service;
    }

    public static void unregister() {
        economyService = null;
    }

    public static Optional<EconomyService> get() {
        if (economyService != null) {
            return Optional.of(economyService);
        }
        return Optional.ofNullable(Bukkit.getServicesManager().load(EconomyService.class));
    }

    public static double getBalance(UUID playerId) {
        return get().map(service -> service.getBalance(playerId)).orElse(0d);
    }

    public static boolean withdraw(UUID playerId, double amount) {
        return get().map(service -> service.withdraw(playerId, amount)).orElse(false);
    }

    public static void deposit(UUID playerId, double amount) {
        get().ifPresent(service -> service.deposit(playerId, amount));
    }
}
