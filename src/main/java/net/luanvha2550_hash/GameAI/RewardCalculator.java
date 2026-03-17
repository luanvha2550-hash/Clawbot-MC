package net.luanvha2550_hash.GameAI;

import net.luanvha2550_hash.Entity.EntityDetails;
import net.luanvha2550_hash.GameAI.StateActions.Action;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Calcula recompensas para aprendizado por reforço.
 * Extraído de RLAgent.java para separar responsabilidade de cálculo de recompensa.
 *
 * Domínios:
 * - Combate: ataques, defesas, posicionamento
 * - Projeto: mineração, construção, coleta
 * - Sobrevivência: saúde, fome, oxigênio
 */
public class RewardCalculator {

    /**
     * Calcula recompensa baseada no estado e ação tomada.
     * Método principal extraído de RLAgent.calculateReward() (~200 linhas).
     */
    public int calculateReward(int botX, int botY, int botZ, List<EntityDetails> nearbyEntities,
                               List<String> nearbyBlocks, double distanceToHostileEntity, int botHealth,
                               double distanceToDanger, List<ItemStack> hotBarItems, String selectedItem,
                               String timeOfDay, String dimension, int botHungerLevel, int botOxygenLevel,
                               ItemStack offhandItem, Map<String, ItemStack> armorItems,
                               Action actionTaken, double risk, double pod) {

        boolean hasWardenNearby = nearbyEntities.stream().anyMatch(e -> "Warden".equals(e.getName()));
        boolean hasSculkNearby = nearbyBlocks.stream().anyMatch(b -> b.contains("Sculk Sensor") || b.contains("Sculk Shrieker"));
        List<EntityDetails> hostileEntities = nearbyEntities.stream().filter(EntityDetails::isHostile).toList();
        boolean hasWoolItems = hotBarItems.stream()
            .anyMatch(item -> item.getItem().getName().getString().toLowerCase().contains("wool") ||
                              item.getItem().getName().getString().toLowerCase().contains("carpet"));

        int reward = 0;

        // 1. Distância para entidade hostil
        reward += calcHostileDistanceReward(distanceToHostileEntity, actionTaken, hostileEntities);

        // 2. Saúde
        reward += calcHealthReward(botHealth, actionTaken);

        // 3. Distância para perigo
        reward += calcDangerDistanceReward(distanceToDanger, actionTaken);

        // 4. Equipamento
        reward += calcEquipmentReward(selectedItem, offhandItem, hostileEntities);

        // 5. Tempo do dia
        reward += "day".equals(timeOfDay) ? 5 : "night".equals(timeOfDay) ? -10 : 0;

        // 6. Dimensão
        reward += switch (dimension) {
            case "minecraft:overworld" -> 1;
            case "minecraft:nether" -> -5;
            case "minecraft:end" -> -10;
            default -> -20;
        };

        // 7. Ajuste por risco
        double riskWeight = (risk >= 0.5) ? 1.5 : 1.0;
        reward = (int) Math.round(reward * riskWeight);

        // 8. Ajuste por PoD
        if (pod >= 0.5) reward -= (int) Math.round(pod * 10);

        // 9. Cenários de alto risco
        if (risk > 0.7 && actionTaken == Action.ATTACK && distanceToHostileEntity <= 5 && distanceToHostileEntity != 0) {
            reward += 20;
        } else if (risk > 0.7 && (actionTaken == Action.MOVE_BACKWARD || actionTaken == Action.TURN_LEFT || actionTaken == Action.TURN_RIGHT)) {
            reward += 10;
        }

        // 10. Ancient City / Deep Dark
        if (hasWoolItems && (hasWardenNearby || hasSculkNearby)) reward += 10;

        // 11. Fome e oxigênio
        reward += calcSurvivalReward(botHungerLevel, botOxygenLevel);

        return reward;
    }

    private int calcHostileDistanceReward(double distance, Action action, List<EntityDetails> hostile) {
        if (distance > 10) return 10;
        if (distance <= 5) {
            int r = -10;
            if (action == Action.ATTACK) r += 15;
            else if (action == Action.SHOOT_ARROW) {
                boolean hasRangedTarget = hostile.stream().anyMatch(e ->
                    e.getName().equals("Creeper") || e.getName().equals("Skeleton") || e.getName().equals("Witch"));
                r += hasRangedTarget ? 10 : 5;
            } else if (action == Action.STAY) r -= 5;
            return r;
        }
        if (distance > 5 && distance <= 20 && action == Action.SHOOT_ARROW) {
            boolean hasPriority = hostile.stream().anyMatch(e ->
                e.getName().equals("Creeper") || e.getName().equals("Skeleton") ||
                e.getName().equals("Pillager") || e.getName().equals("Witch") || e.getName().equals("Blaze"));
            return hasPriority ? 25 : 15;
        }
        return 0;
    }

    private int calcHealthReward(int health, Action action) {
        if (health > 15) return 10;
        if (health <= 5) {
            return -20 + ((action == Action.STAY || action == Action.USE_ITEM) ? 10 : 0);
        }
        return 5;
    }

    private int calcDangerDistanceReward(double distance, Action action) {
        if (distance > 10) return 10;
        if (distance <= 5) {
            int r = -15;
            if (action == Action.MOVE_BACKWARD || action == Action.TURN_LEFT || action == Action.TURN_RIGHT) r += 10;
            return r;
        }
        return 0;
    }

    private int calcEquipmentReward(String selectedItem, ItemStack offhand, List<EntityDetails> hostile) {
        if (!hostile.isEmpty()) {
            if ((selectedItem.contains("Sword") || selectedItem.contains("Bow") ||
                 selectedItem.contains("Axe") || selectedItem.contains("Crossbow") ||
                 selectedItem.contains("Trident")) && offhand.getItem().getName().getString().equalsIgnoreCase("shield")) {
                return 20;
            }
            if ((selectedItem.contains("Pickaxe") || selectedItem.contains("Hoe")) &&
                offhand.getItem().getName().getString().equalsIgnoreCase("shield")) {
                return 15;
            }
            if (selectedItem.contains("Air") && offhand.getItem().getName().getString().equalsIgnoreCase("shield")) {
                return 10;
            }
        }
        return -5;
    }

    private int calcSurvivalReward(int hunger, int oxygen) {
        int r = 0;
        if (hunger <= 6) r -= 10;
        else if (hunger > 16) r += 5;
        if (oxygen < 60) r -= 20;
        else if (oxygen >= 150) r += 10;
        return r;
    }

    /**
     * Calcula Probabilidade de Morte (PoD) baseada na mudança de estado.
     */
    public double assessRiskOutcome(State initialState, State postActionState, Action action) {
        double pod = 0.0;

        if (postActionState.getBotHealth() < initialState.getBotHealth()) {
            pod += (initialState.getBotHealth() - postActionState.getBotHealth()) * 0.5;
        }
        if (postActionState.getBotHungerLevel() < initialState.getBotHungerLevel()) {
            pod += (initialState.getBotHungerLevel() - postActionState.getBotHungerLevel()) * 0.3;
        }
        if (postActionState.getFrostLevel() > initialState.getFrostLevel()) {
            pod += (postActionState.getFrostLevel() - initialState.getFrostLevel()) * 0.2;
        }

        if (initialState.getDistanceToDangerZone() != 0 && postActionState.getDistanceToDangerZone() != 0) {
            if (postActionState.getDistanceToDangerZone() < initialState.getDistanceToDangerZone()) {
                pod += (initialState.getDistanceToDangerZone() - postActionState.getDistanceToDangerZone()) * 0.4;
            }
        }

        if (initialState.getDistanceToHostileEntity() != 0 && postActionState.getDistanceToHostileEntity() != 0) {
            if (postActionState.getDistanceToHostileEntity() < initialState.getDistanceToHostileEntity()) {
                pod += (initialState.getDistanceToHostileEntity() - postActionState.getDistanceToHostileEntity()) * 0.6;
            }
        }

        return Math.min(1.0, pod);
    }
}
