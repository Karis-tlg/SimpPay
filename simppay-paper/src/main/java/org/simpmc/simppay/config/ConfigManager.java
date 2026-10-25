package org.simpmc.simppay.config;

import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.NameFormatters;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurationStore;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.simpmc.simppay.SPPlugin;
import org.simpmc.simppay.config.annotations.Folder;
import org.simpmc.simppay.config.migration.MigrationRegistry;
import org.simpmc.simppay.config.serializers.KeySerializer;
import org.simpmc.simppay.config.serializers.SoundComponentSerializer;
import org.simpmc.simppay.config.types.*;
import org.simpmc.simppay.config.types.DiscordConfig;
import org.simpmc.simppay.config.types.banking.PayosConfig;
import org.simpmc.simppay.config.types.banking.SepayConfig;
import org.simpmc.simppay.config.types.banking.Web2mConfig;
import org.simpmc.simppay.config.types.card.*;
import org.simpmc.simppay.config.types.menu.BankQrMenuConfig;
import org.simpmc.simppay.config.types.menu.FormsConfig;
import org.simpmc.simppay.config.types.menu.PaymentHistoryMenuConfig;
import org.simpmc.simppay.config.types.menu.ServerPaymentHistoryMenuConfig;
import org.simpmc.simppay.config.types.menu.StreakMenuConfig;
import org.simpmc.simppay.config.types.menu.card.CardListMenuConfig;
import org.simpmc.simppay.config.types.menu.card.CardPriceMenuConfig;
import org.simpmc.simppay.config.types.menu.card.anvil.CardPinMenuConfig;
import org.simpmc.simppay.config.types.menu.card.anvil.CardSerialMenuConfig;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.exlll.configlib.DeserializationCoercionType.BOOLEAN_TO_STRING;
import static de.exlll.configlib.DeserializationCoercionType.NUMBER_TO_STRING;

public class ConfigManager {
    @Getter
    private static ConfigManager instance;

    private volatile Map<Class<?>, Object> configs = Map.of();
    private final SPPlugin plugin;

    private final List<Class<?>> configClasses = List.of(
            // --- Core ---
            MainConfig.class,
            MessageConfig.class,
            DatabaseConfig.class,
            CoinsConfig.class,
            BankingConfig.class,
            CardConfig.class,

            // --- Banking Gateways ---
            PayosConfig.class,
            SepayConfig.class,
            Web2mConfig.class,

            // --- Card Gateways ---
            Gachthe1sConfig.class,
            Card2KConfig.class,
            ThesieureConfig.class,
            Doithe1sConfig.class,

            // --- Menus (Java) ---
            CardListMenuConfig.class,
            CardPriceMenuConfig.class,
            CardPinMenuConfig.class,
            CardSerialMenuConfig.class,
            BankQrMenuConfig.class,
            PaymentHistoryMenuConfig.class,
            ServerPaymentHistoryMenuConfig.class,
            StreakMenuConfig.class,

            // --- Menus (Bedrock) ---
            FormsConfig.class,

            // --- Milestones & Rewards ---
            MilestonesPlayerConfig.class,
            MilestonesServerConfig.class,
            StreakConfig.class,
            NaplandauConfig.class,
            MocNapConfig.class,
            MocNapServerConfig.class,

            // --- Integrations ---
            DiscordConfig.class
    );

    private final Map<Class<?>, Path> configPaths = new HashMap<>();

    private final YamlConfigurationProperties properties = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
            .addSerializer(Key.class, new KeySerializer())
            .addSerializer(Sound.class, new SoundComponentSerializer())
            .setDeserializationCoercionTypes(BOOLEAN_TO_STRING, NUMBER_TO_STRING)
            .setNameFormatter(NameFormatters.LOWER_KEBAB_CASE)
            .header("""
                    SimpPay @ 2025
                    Made by typical.smc
                    """)
            .build();

    public ConfigManager(SPPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        initPaths();
        registerAll();
    }

    private void initPaths() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        for (Class<?> clazz : configClasses) {
            configPaths.put(clazz, getConfigPath(clazz));
        }
    }

    private Path getConfigPath(Class<?> clazz) {
        String fileName = getConfigFileName(clazz.getSimpleName()) + ".yml";
        if (clazz.isAnnotationPresent(Folder.class)) {
            String folderName = clazz.getAnnotation(Folder.class).value();
            File folder = new File(plugin.getDataFolder(), folderName);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            return Paths.get(folder.getPath(), fileName);
        }
        return Paths.get(plugin.getDataFolder().getPath(), fileName);
    }

    private void registerAll() {
        plugin.getLogger().info("Loading all configurations");
        configs = loadSnapshot(Map.of());
        plugin.getLogger().info("All configurations loaded successfully");
    }

    /**
     * Build a complete new configuration snapshot without modifying the one
     * currently used by scheduled tasks and command handlers.
     */
    private Map<Class<?>, Object> loadSnapshot(Map<Class<?>, Object> previous) {
        Map<Class<?>, Object> next = new HashMap<>();
        for (Class<?> rawClass : configClasses) {
            next.put(rawClass, loadConfig(rawClass, previous));
        }
        return Map.copyOf(next);
    }

    private <T> T loadConfig(Class<T> cfgClass, Map<Class<?>, Object> previous) {
        Path path = configPaths.get(cfgClass);
        try {
            MigrationRegistry.migrateIfNeeded(cfgClass, path);
            YamlConfigurationStore<T> store = new YamlConfigurationStore<>(cfgClass, properties);
            store.update(path);
            T loaded = store.load(path);
            if (loaded == null) {
                throw new IllegalStateException("Config loader returned null");
            }
            return loaded;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load config for " + cfgClass.getSimpleName() + ": " + e.getMessage());
            T previousConfig = cfgClass.cast(previous.get(cfgClass));
            if (previousConfig != null) {
                plugin.getLogger().warning("Keeping last good config for " + cfgClass.getSimpleName());
                return previousConfig;
            }
            try {
                plugin.getLogger().warning("Using defaults for " + cfgClass.getSimpleName());
                return cfgClass.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to initialize config " + cfgClass.getSimpleName(), ex);
            }
        }
    }

    /**
     * Reload configs atomically so async milestone/bossbar tasks never see
     * the temporary empty map created by the old clear-and-repopulate logic.
     */
    public synchronized void reloadAll() {
        plugin.getLogger().info("Reloading all configurations");
        Map<Class<?>, Object> next = loadSnapshot(configs);
        configs = next;
        plugin.getLogger().info("All configurations reloaded successfully");
    }

    /**
     * Retrieve a loaded configuration instance by its class.
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(Class<T> cls) {
        return (T) configs.get(cls);
    }

    private String getConfigFileName(String name) {
        var builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i != 0) {
                    builder.append('-');
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
