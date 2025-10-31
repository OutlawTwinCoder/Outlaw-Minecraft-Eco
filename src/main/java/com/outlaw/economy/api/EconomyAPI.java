package com.outlaw.economy.api;

import org.bukkit.Bukkit;

import java.util.Locale;
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
        return withdraw(playerId, amount, "");
    }

    public static boolean withdraw(UUID playerId, double amount, String reason) {
        return get().map(service -> service.withdraw(playerId, amount, reason)).orElse(false);
    }

    public static boolean deposit(UUID playerId, double amount) {
        return deposit(playerId, amount, "");
    }

    public static boolean deposit(UUID playerId, double amount, String reason) {
        return get().map(service -> service.deposit(playerId, amount, reason)).orElse(false);
    }

    public static String format(double amount) {
        return get().map(service -> service.format(amount)).orElse(String.format(Locale.US, "%.2f", amount));
    }

    public static String currencyCode() {
        return get().map(EconomyService::currencyCode).orElse("Coins");
    }
}
