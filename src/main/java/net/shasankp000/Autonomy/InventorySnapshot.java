package net.shasankp000.Autonomy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Snapshot of the bot's inventory state.
 */
public class InventorySnapshot {

    private final Map<String, Integer> items;
    private final int woodCount;
    private final int stoneCount;
    private final int ironCount;
    private final int diamondCount;
    private final int foodCount;
    private final int toolCount;
    private final int weaponCount;
    private final int armorCount;

    private final int bestWeaponTier;  // 0=none, 1=wood/stone, 2=iron, 3=diamond, 4=netherite
    private final int bestArmorTier;
    private final boolean hasCompleteArmor;

    /**
     * Default constructor for empty inventory.
     */
    public InventorySnapshot() {
        this.items = new HashMap<>();
        this.woodCount = 0;
        this.stoneCount = 0;
        this.ironCount = 0;
        this.diamondCount = 0;
        this.foodCount = 0;
        this.toolCount = 0;
        this.weaponCount = 0;
        this.armorCount = 0;
        this.bestWeaponTier = 0;
        this.bestArmorTier = 0;
        this.hasCompleteArmor = false;
    }

    /**
     * Full constructor.
     */
    public InventorySnapshot(Map<String, Integer> items, int woodCount, int stoneCount,
                            int ironCount, int diamondCount, int foodCount,
                            int toolCount, int weaponCount, int armorCount,
                            int bestWeaponTier, int bestArmorTier, boolean hasCompleteArmor) {
        this.items = items != null ? items : new HashMap<>();
        this.woodCount = woodCount;
        this.stoneCount = stoneCount;
        this.ironCount = ironCount;
        this.diamondCount = diamondCount;
        this.foodCount = foodCount;
        this.toolCount = toolCount;
        this.weaponCount = weaponCount;
        this.armorCount = armorCount;
        this.bestWeaponTier = bestWeaponTier;
        this.bestArmorTier = bestArmorTier;
        this.hasCompleteArmor = hasCompleteArmor;
    }

    // ========== Getters ==========

    public int getWoodCount() { return woodCount; }
    public int getStoneCount() { return stoneCount; }
    public int getIronCount() { return ironCount; }
    public int getDiamondCount() { return diamondCount; }
    public int getFoodCount() { return foodCount; }
    public int getToolCount() { return toolCount; }
    public int getWeaponCount() { return weaponCount; }
    public int getArmorCount() { return armorCount; }
    public int getBestWeaponTier() { return bestWeaponTier; }
    public int getBestArmorTier() { return bestArmorTier; }
    public boolean hasCompleteArmor() { return hasCompleteArmor; }
    public Map<String, Integer> getItems() { return Collections.unmodifiableMap(items); }

    /**
     * Check if inventory contains a specific item.
     */
    public boolean hasItem(String itemId) {
        return items.containsKey(itemId) && items.get(itemId) > 0;
    }

    /**
     * Get count of a specific item.
     */
    public int getItemCount(String itemId) {
        return items.getOrDefault(itemId, 0);
    }

    /**
     * Create a builder for constructing InventorySnapshot.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for InventorySnapshot.
     */
    public static class Builder {
        private Map<String, Integer> items = new HashMap<>();
        private int woodCount = 0;
        private int stoneCount = 0;
        private int ironCount = 0;
        private int diamondCount = 0;
        private int foodCount = 0;
        private int toolCount = 0;
        private int weaponCount = 0;
        private int armorCount = 0;
        private int bestWeaponTier = 0;
        private int bestArmorTier = 0;
        private boolean hasCompleteArmor = false;

        public Builder items(Map<String, Integer> items) {
            this.items = items;
            return this;
        }

        public Builder addItem(String itemId, int count) {
            this.items.put(itemId, count);
            return this;
        }

        public Builder woodCount(int count) {
            this.woodCount = count;
            return this;
        }

        public Builder stoneCount(int count) {
            this.stoneCount = count;
            return this;
        }

        public Builder ironCount(int count) {
            this.ironCount = count;
            return this;
        }

        public Builder diamondCount(int count) {
            this.diamondCount = count;
            return this;
        }

        public Builder foodCount(int count) {
            this.foodCount = count;
            return this;
        }

        public Builder toolCount(int count) {
            this.toolCount = count;
            return this;
        }

        public Builder weaponCount(int count) {
            this.weaponCount = count;
            return this;
        }

        public Builder armorCount(int count) {
            this.armorCount = count;
            return this;
        }

        public Builder bestWeaponTier(int tier) {
            this.bestWeaponTier = tier;
            return this;
        }

        public Builder bestArmorTier(int tier) {
            this.bestArmorTier = tier;
            return this;
        }

        public Builder hasCompleteArmor(boolean complete) {
            this.hasCompleteArmor = complete;
            return this;
        }

        public InventorySnapshot build() {
            return new InventorySnapshot(items, woodCount, stoneCount, ironCount,
                diamondCount, foodCount, toolCount, weaponCount, armorCount,
                bestWeaponTier, bestArmorTier, hasCompleteArmor);
        }
    }
}