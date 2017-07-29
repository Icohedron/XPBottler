package io.github.icohedron.xpbottler;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.entity.spawn.EntitySpawnCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Plugin(id = "xpbottler", name = "XPBottler", version = "1.0.0-S5.1-SNAPSHOT-1",
        description = "Store your experience in bottles!")
public class XPBottler {

    private final int maxLimit = 64 * 27 * 2; // Maximum bottles at a time: one double-chest full

    private final ItemStackSnapshot experienceBottleSnapshot = ItemStack.builder().itemType(ItemTypes.EXPERIENCE_BOTTLE).quantity(1).build().createSnapshot();

    private final Text prefix = Text.of(TextColors.GRAY, "[", TextColors.GOLD, "XPBottler", TextColors.GRAY, "] ");
    private final String configFileName = "xpbottler.conf";

    @Inject @ConfigDir(sharedRoot = false) private Path configDir;
    @Inject private Logger logger;

    private int xpPerBottle;
    private boolean consumeBottles;

    @Listener
    public void onInitialization(GameInitializationEvent event) {
        loadConfig();
        registerCommands();
        logger.info("Finished initialization");
    }

    private void loadConfig() {
        File configFile = new File(configDir.toFile(), configFileName);
        ConfigurationLoader<CommentedConfigurationNode> configurationLoader = HoconConfigurationLoader.builder().setFile(configFile).build();
        ConfigurationNode rootNode;

        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                Sponge.getAssetManager().getAsset(this, configFileName).get().copyToFile(configFile.toPath());
            } catch (IOException e) {
                logger.error("Failed to create default config file");
                e.printStackTrace();
            }
        }

        try {
            rootNode = configurationLoader.load();
            xpPerBottle = rootNode.getNode("xp_per_bottle").getInt(7);
            consumeBottles = rootNode.getNode("consume_bottles").getBoolean(true);
        } catch (IOException e) {
            logger.error("An error occurred while reading config: ");
            e.printStackTrace();
        }
    }

    private void registerCommands() {
        CommandSpec bottle = CommandSpec.builder()
                .permission("xpbottler.command.bottle")
                .arguments(GenericArguments.onlyOne(GenericArguments.string(Text.of("amount"))))
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(Text.of(prefix, TextColors.RED, "Only a player may execute this command"));
                        return CommandResult.empty();
                    }

                    Player player = (Player) src;
                    int experience = player.get(Keys.TOTAL_EXPERIENCE).get();

                    String arg = (String) args.getOne("amount").get();
                    if (arg.equalsIgnoreCase("max")) {
                        int maxBottles = experience / xpPerBottle;

                        if (consumeBottles) {
                            int glassBottles = countGlassBottles(player);
                            maxBottles = glassBottles > maxBottles ? maxBottles : glassBottles;
                        }

                        if (maxBottles > maxLimit) {
                            maxBottles = maxLimit;
                        }

                        return bottleXP(player, experience, maxBottles);
                    } else {
                        try {
                            int bottles = Integer.parseInt(arg);
                            if (bottles < 0) {
                                src.sendMessage(Text.of(prefix, TextColors.RED, "'", arg, "' is too small!"));
                                return CommandResult.empty();
                            }
                            if (bottles > maxLimit) {
                                src.sendMessage(Text.of(prefix, TextColors.RED, "'", arg, "' is too large!"));
                                return CommandResult.empty();
                            }

                            return bottleXP(player, experience, bottles);
                        } catch (NumberFormatException e) {
                            src.sendMessage(Text.of(prefix, TextColors.RED, "'", arg, "' is not a valid amount!"));
                            return CommandResult.empty();
                        }
                    }
                })
                .build();

        Sponge.getCommandManager().register(this, bottle, "bottle");
    }

    private CommandResult bottleXP(Player player, int experience, int bottles) {
        String noun = "experience bottles";
        if (bottles == 1) {
            noun = "experience bottle";
        }

        if (consumeBottles) {
            if (countGlassBottles(player) < bottles) {
                player.sendMessage(Text.of(prefix, TextColors.RED, "You do not have enough glass bottles in your inventory to create " + bottles + " " + noun + "!"));
                return CommandResult.empty();
            }
        }

        int deductedXP = bottles * xpPerBottle;
        if (experience < deductedXP) {
            player.sendMessage(Text.of(prefix, TextColors.RED, "You do not have enough experience to create " + bottles + " " + noun + "!"));
            return CommandResult.empty();
        }

        ItemStack[] bottleItemStacks = createXPBottles(bottles);
        for (int i = 0; i < bottleItemStacks.length; i++) {
            Location location = player.getLocation();
            Entity entity = location.getExtent().createEntity(EntityTypes.ITEM, location.getPosition());
            entity.offer(Keys.REPRESENTED_ITEM, bottleItemStacks[i].createSnapshot());
            entity.offer(Keys.PICKUP_DELAY, 0);
            location.getExtent().spawnEntity(entity, Cause.source(EntitySpawnCause.builder().entity(entity).type(SpawnTypes.PLUGIN).build()).build());

            if (consumeBottles) {
                player.getInventory().query(ItemTypes.GLASS_BOTTLE).poll(bottleItemStacks[i].getQuantity());
            }
        }

        player.offer(Keys.TOTAL_EXPERIENCE, experience - deductedXP);

        player.sendMessage(Text.of(prefix, TextColors.YELLOW, "Successfully created " + bottles + " " + noun + "!"));
        return CommandResult.success();
    }

    private int countGlassBottles(Player player) {
        int numBottles = 0;
        for (Inventory slot : player.getInventory().query(ItemTypes.GLASS_BOTTLE).slots()) {
            numBottles += slot.peek().get().getQuantity();
        }
        return numBottles;
    }

    private ItemStack[] createXPBottles(int quantity) {
        int stacks = quantity / 64;
        int remainder = quantity % 64;
        int one = remainder > 0 ? 1 : 0;
        ItemStack[] itemStacks = new ItemStack[stacks + one];

        for (int i = 0; i < stacks; i++) {
            itemStacks[i] = ItemStack.builder().itemType(ItemTypes.EXPERIENCE_BOTTLE).quantity(64).build();
        }

        if (one == 1) {
            itemStacks[itemStacks.length - 1] = ItemStack.builder().itemType(ItemTypes.EXPERIENCE_BOTTLE).quantity(remainder).build();
        }

        return itemStacks;
    }

    @Listener
    public void onReloadEvent(GameReloadEvent event) {
        loadConfig();
        logger.info("Reloaded configuration");
    }
}
