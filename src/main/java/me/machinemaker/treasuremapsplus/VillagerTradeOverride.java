/*
 * GNU General Public License v3
 *
 * TreasureMapsPlus, a plugin to alter treasure maps
 *
 * Copyright (C) 2023 Machine-Maker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package me.machinemaker.treasuremapsplus;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import me.machinemaker.mirror.paper.PaperMirror;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

import static me.machinemaker.treasuremapsplus.Utils.sneaky;
import static net.kyori.adventure.text.Component.translatable;

public final class VillagerTradeOverride {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private static final Class<?> TREASURE_MAP_TRADE_LISTING_CLASS = PaperMirror.findMinecraftClass(
        "world.entity.npc.VillagerTrades$TreasureMapForEmeralds",
        "world.entity.npc.VillagerTrades$k"
    );
    private static final MethodHandles.Lookup LOOKUP = sneaky(() -> MethodHandles.privateLookupIn(TREASURE_MAP_TRADE_LISTING_CLASS, MethodHandles.lookup()));
    private static final MethodHandle EMERALD_COST = sneaky(() -> LOOKUP.findGetter(TREASURE_MAP_TRADE_LISTING_CLASS, "emeraldCost", int.class));
    private static final MethodHandle DESTINATION = sneaky(() -> LOOKUP.findGetter(TREASURE_MAP_TRADE_LISTING_CLASS, "destination", TagKey.class));
    private static final MethodHandle DISPLAY_NAME = sneaky(() -> LOOKUP.findGetter(TREASURE_MAP_TRADE_LISTING_CLASS, "displayName", String.class));
    private static final MethodHandle MAX_USES = sneaky(() -> LOOKUP.findGetter(TREASURE_MAP_TRADE_LISTING_CLASS, "maxUses", int.class));
    private static final MethodHandle VILLAGER_XP = sneaky(() -> LOOKUP.findGetter(TREASURE_MAP_TRADE_LISTING_CLASS, "villagerXp", int.class));

    private VillagerTradeOverride() {
    }

    public static void setup(final boolean replaceBuriedTreasure, final boolean replaceMansion) {
        final Int2ObjectMap<VillagerTrades.ItemListing[]> cartographer = VillagerTrades.TRADES.get(VillagerProfession.CARTOGRAPHER);
        for (final VillagerTrades.ItemListing[] listings : cartographer.values()) {
            for (int i = 0; i < listings.length; i++) {
                final VillagerTrades.ItemListing listing = listings[i];
                if (TREASURE_MAP_TRADE_LISTING_CLASS.isInstance(listing)) {
                    listings[i] = createOverride(listing, replaceBuriedTreasure, replaceMansion);
                }
            }
        }
    }

    private static VillagerTrades.ItemListing createOverride(final VillagerTrades.ItemListing original, final boolean replaceBuriedTreasure, final boolean replaceMansion) {
        final String name = (String) sneaky(() -> DISPLAY_NAME.invoke(original));
        if (name.endsWith("monument")) {
            return replaceBuriedTreasure ? new OverrideListing(original) : original;
        } else if (name.endsWith("mansion")) {
            return replaceMansion ? new OverrideListing(original) : original;
        } else {
            LOGGER.warn("Unhandled villager trade type \"{}\"", name);
            return original;
        }
    }

    private record OverrideListing(VillagerTrades.ItemListing original) implements VillagerTrades.ItemListing {

        @SuppressWarnings("unchecked")
        @Override
            public @Nullable MerchantOffer getOffer(final Entity entity, final RandomSource random) {
                if (!entity.getLevel().paperConfig().environment.treasureMaps.enabled) {
                    return null;
                }
                try {
                    final ItemStack map = new ItemStack(Items.MAP);
                    final org.bukkit.inventory.ItemStack bukkitStack = Utils.getBukkitStackMirror(map);
                    final ItemMeta meta = bukkitStack.getItemMeta();
                    meta.displayName(translatable((String) DISPLAY_NAME.invoke(this.original)));
                    meta.lore(List.of(TreasureMapsPlus.LORE));
                    meta.getPersistentDataContainer().set(TreasureMapsPlus.IS_MAP, PersistentDataType.BYTE, (byte) 1);
                    meta.getPersistentDataContainer().set(TreasureMapsPlus.MAP_STRUCTURE_TAG_KEY, PersistentDataType.STRING, ((TagKey<Structure>) DESTINATION.invoke(this.original)).location().toString());
                    bukkitStack.setItemMeta(meta);
                    return new MerchantOffer(
                        new ItemStack(Items.EMERALD, (int) EMERALD_COST.invoke(this.original)),
                        new ItemStack(Items.COMPASS),
                        map,
                        (int) MAX_USES.invoke(this.original),
                        (int) VILLAGER_XP.invoke(this.original),
                        0.2F
                    );
                } catch (final Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            }
        }
}
