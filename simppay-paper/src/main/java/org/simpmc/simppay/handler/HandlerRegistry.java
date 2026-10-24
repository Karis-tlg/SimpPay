package org.simpmc.simppay.handler;

import lombok.Getter;
import org.simpmc.simppay.config.ConfigManager;
import org.simpmc.simppay.config.types.BankingConfig;
import org.simpmc.simppay.config.types.CardConfig;
import org.simpmc.simppay.config.types.CoinsConfig;
import org.simpmc.simppay.handler.coins.DefaultCoinsHandler;
import org.simpmc.simppay.util.MessageUtil;

@Getter
public class HandlerRegistry {

    private PaymentHandler cardHandler;
    private PaymentHandler bankHandler;
    private CoinsHandler coinsHandler;

    public HandlerRegistry() {
        init();
    }

    private void init() {
        CardConfig cardConfig = ConfigManager.getInstance().getConfig(CardConfig.class);
        BankingConfig bankingConfig = ConfigManager.getInstance().getConfig(BankingConfig.class);
        CoinsConfig coinsConfig = ConfigManager.getInstance().getConfig(CoinsConfig.class);

        cardHandler = cardConfig.cardApi.handlerFactory.get();
        bankHandler = bankingConfig.bankApi.handlerFactory.get();
        try {
            coinsHandler = coinsConfig.pointsProvider.handlerFactory.get();
        } catch (Exception | LinkageError e) {
            MessageUtil.warn("Configured points provider is unavailable (" + e.getClass().getSimpleName() + "); coin rewards are disabled until a provider is installed/configured");
            coinsHandler = new DefaultCoinsHandler();
        }

        MessageUtil.info("Registered handlers: ");
        MessageUtil.info("Card Handler: " + cardHandler.getClass().getSimpleName());
        MessageUtil.info("Bank Handler: " + bankHandler.getClass().getSimpleName());
        MessageUtil.info("Coins Handler: " + coinsHandler.getClass().getSimpleName());
    }

    public void reload() {
        init();
    }
}
