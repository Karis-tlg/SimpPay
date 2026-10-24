package org.simpmc.simppay.commands.root;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import org.simpmc.simppay.SPPlugin;
import org.simpmc.simppay.menu.StreakMenuView;
import org.simpmc.simppay.util.FloodgateUtil;

/**
 * Phase 5: Command to open the streak menu.
 * Supports both Java Edition (GUI) and Bedrock Edition (Forms).
 */
public class StreakCommand {
    public StreakCommand() {
        new CommandAPICommand("streak")
                .withPermission(CommandPermission.NONE)
                .withAliases("streaks", "naplientiep", "chuoinapthe")
                .executesPlayer((player, args) -> {
                    // Check if player is from Bedrock Edition
                    if (SPPlugin.getInstance().isFloodgateEnabled() && FloodgateUtil.isBedrockPlayer(player)) {
                        // Resolve Bedrock-only classes lazily. They are not present on Java-only servers.
                        try {
                            Class<?> formClass = Class.forName("org.simpmc.simppay.forms.StreakForm");
                            Object form = formClass.getMethod("getStreakForm", org.bukkit.entity.Player.class).invoke(null, player);
                            Class<?> utilClass = Class.forName("org.simpmc.simppay.util.FloodgateUtil");
                            utilClass.getMethod("sendForm", java.util.UUID.class, Object.class)
                                    .invoke(null, player.getUniqueId(), form);
                        } catch (ReflectiveOperationException e) {
                            SPPlugin.getInstance().getLogger().warning("Unable to open Bedrock streak form: " + e);
                        }
                    } else {
                        // Open Java GUI
                        StreakMenuView.openMenu(player);
                    }
                })
                .register();
    }
}
