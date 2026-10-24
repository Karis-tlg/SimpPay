package org.simpmc.simppay.handler.coins;

import org.bukkit.Bukkit;
import org.simpmc.simppay.config.ConfigManager;
import org.simpmc.simppay.config.types.CoinsConfig;
import org.simpmc.simppay.handler.CoinsHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bridges the optional CoinsEngine plugin without linking its classes at startup.
 * The upstream 2.5.x artifact is no longer reliably available from its Maven repository.
 */
public class CoinsEngineHandler extends CoinsHandler {

    private final Class<?> apiClass;
    private final Class<?> currencyClass;
    private final Object currency;

    public CoinsEngineHandler() {
        isAsync = false;
        if (!Bukkit.getPluginManager().isPluginEnabled("CoinsEngine")) {
            throw new IllegalStateException("CoinsEngine is not installed/enabled");
        }
        try {
            apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            currencyClass = Class.forName("su.nightexpress.coinsengine.api.currency.Currency");
            currency = apiClass.getMethod("getCurrency", String.class).invoke(
                    null, ConfigManager.getInstance().getConfig(CoinsConfig.class).coinsEngineCurrency);
            if (currency == null) throw new IllegalStateException("CoinsEngine currency not found");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CoinsEngine API is incompatible", e);
        }
    }

    private Object call(String name, UUID uuid, Object... args) {
        try {
            Class<?>[] types = args.length == 0
                    ? new Class<?>[]{UUID.class, currencyClass}
                    : new Class<?>[]{UUID.class, currencyClass, double.class};
            Method method = apiClass.getMethod(name, types);
            return method.invoke(null, args.length == 0
                    ? new Object[]{uuid, currency}
                    : new Object[]{uuid, currency, ((Number)args[0]).doubleValue()});
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("CoinsEngine API signature changed: " + name, e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("CoinsEngine operation failed: " + name, e);
        }
    }

    @Override
    public void take(UUID uuid, int amount) {
        call("removeBalance", uuid, amount);
    }

    @Override
    public int look(UUID uuid) {
        return ((Number) call("getBalance", uuid)).intValue();
    }

    @Override
    public void give(UUID uuid, int amount) {
        call("addBalance", uuid, amount);
    }

    @Override
    public void set(UUID uuid, int amount) {
        call("setBalance", uuid, amount);
    }
}
