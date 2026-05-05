package com.aibot;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * A fake player entity that stands still and swings its sword in a locked direction.
 * <p>
 * The bot never moves, never turns, and never pathfinds. It simply attacks
 * whatever is in front of it using Minecraft's real attack cooldown system.
 * It auto-manages weapons from a nearby chest and deposits damaged ones into a barrel.
 */
public class BotPlayer extends ServerPlayerEntity {

    private final ServerPlayerEntity owner;

    // Locked attack direction — set once on spawn, never changes
    private final float lockedYaw;
    private final float lockedPitch;

    // Cached block positions — found once on spawn
    private BlockPos chestPos;
    private BlockPos barrelPos;

    private BotState state = BotState.IDLE;
    private int tickCounter = 0;
    private boolean initialized = false;

    public BotPlayer(MinecraftServer server, ServerWorld world,
                     GameProfile profile, ServerPlayerEntity owner,
                     float yaw, float pitch) {
        super(server, world, profile, SyncedClientOptions.createDefault());
        this.owner = owner;
        this.lockedYaw = yaw;
        this.lockedPitch = pitch;
    }

    // ─── Getters ────────────────────────────────────────────────────────

    public ServerPlayerEntity getOwner() {
        return owner;
    }

    public float getLockedYaw() {
        return lockedYaw;
    }

    public float getLockedPitch() {
        return lockedPitch;
    }

    public BlockPos getChestPos() {
        return chestPos;
    }

    public BlockPos getBarrelPos() {
        return barrelPos;
    }

    public BotState getBotState() {
        return state;
    }

    // ─── Lifecycle ──────────────────────────────────────────────────────

    @Override
    public boolean isDisconnected() {
        return false;
    }

    @Override
    public void tick() {
        // Lock rotation every tick — bot never turns
        this.setYaw(lockedYaw);
        this.setPitch(lockedPitch);
        this.setHeadYaw(lockedYaw);
        this.setBodyYaw(lockedYaw);

        // Prevent any movement
        this.setVelocity(Vec3d.ZERO);

        // Initialize storage on first tick (world is fully loaded by then)
        if (!initialized) {
            initialized = true;
            initStorage((ServerWorld) this.getWorld());
        }

        tickCounter++;
        runBotLogic();

        // Call super to handle basic entity ticking (NOT the full player tick
        // which tries to send packets). We manually handle everything.
        // super.tick() would crash without a real network handler, so we
        // only call the entity-level baseTick.
        this.baseTick();
    }

    // ─── Storage Initialization ─────────────────────────────────────────

    /**
     * Scans for the nearest chest and barrel within 16 blocks.
     * Called once on first tick after spawn.
     */
    private void initStorage(ServerWorld world) {
        chestPos = findNearestChest(world, this.getBlockPos());
        barrelPos = findNearestBarrel(world, this.getBlockPos());

        if (chestPos == null) {
            sendOwnerMessage("No chest found within 16 blocks. Bot is idle.", Formatting.RED);
            state = BotState.IDLE;
            return;
        }

        if (barrelPos == null) {
            sendOwnerMessage("No barrel found within 16 blocks. Damaged items will be dropped.", Formatting.YELLOW);
            // Bot still operates — barrel is optional
        }

        sendOwnerMessage("Bot ready! Chest at " + formatPos(chestPos) +
                (barrelPos != null ? ", Barrel at " + formatPos(barrelPos) : " (no barrel)"),
                Formatting.GREEN);

        state = BotState.SWAPPING; // go fetch first item
    }

    private BlockPos findNearestChest(ServerWorld world, BlockPos origin) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterateOutwards(origin, 16, 16, 16)) {
            Block b = world.getBlockState(pos).getBlock();
            if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST) {
                double dist = pos.getSquaredDistance(origin);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = pos.toImmutable();
                }
            }
        }
        return nearest;
    }

    private BlockPos findNearestBarrel(ServerWorld world, BlockPos origin) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterateOutwards(origin, 16, 16, 16)) {
            if (world.getBlockState(pos).getBlock() == Blocks.BARREL) {
                double dist = pos.getSquaredDistance(origin);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = pos.toImmutable();
                }
            }
        }
        return nearest;
    }

    // ─── Tick Logic ─────────────────────────────────────────────────────

    private void runBotLogic() {
        switch (state) {
            case IDLE:
                // Do nothing — bot is waiting, no weapon available
                break;

            case SWAPPING:
                // Execute the swap with a small delay to avoid chest spam
                if (tickCounter % 5 == 0) {
                    doSwap();
                }
                break;

            case ATTACKING:
                ItemStack held = this.getMainHandStack();

                // Check if item broke or was somehow removed
                if (held.isEmpty()) {
                    state = BotState.SWAPPING;
                    break;
                }

                // Check durability threshold (10%)
                if (getDurabilityPercent(held) <= 0.10f) {
                    state = BotState.SWAPPING;
                    break;
                }

                // Try to swing
                tryAttack();
                break;
        }
    }

    // ─── Attack Logic ───────────────────────────────────────────────────

    /**
     * Attempts to attack whatever entity is in the bot's fixed line of sight.
     * Uses the real attack cooldown system so every hit is a full-charge swing.
     */
    private void tryAttack() {
        // Read the real attack cooldown (0.0 = just swung, 1.0 = fully charged)
        float cooldown = this.getAttackCooldownProgress(0.5f);

        if (cooldown < 0.95f) return; // not ready yet

        // Cast a ray from the bot's eyes in the locked facing direction
        Vec3d eyePos = this.getEyePos();
        Vec3d lookVec = getRotationVector(lockedPitch, lockedYaw);
        Vec3d reachEnd = eyePos.add(lookVec.multiply(3.5)); // 3.5 block reach

        // Build a bounding box along the ray for broad-phase check
        Box searchBox = new Box(eyePos, reachEnd).expand(1.0);

        // Check for entity hit using ProjectileUtil
        EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
                this.getWorld(),
                this,
                eyePos,
                reachEnd,
                searchBox,
                e -> !e.isSpectator() && e.canHit() && e != this
        );

        if (entityHit != null) {
            // Hit an entity — use vanilla attack which handles knockback, crits, enchants, etc.
            this.attack(entityHit.getEntity());
        }

        // Reset attack cooldown regardless (swing animation)
        this.resetLastAttackedTicks();
    }

    // ─── Item Management ────────────────────────────────────────────────

    /**
     * Full swap sequence: deposit current damaged item, fetch next best item.
     */
    private void doSwap() {
        // 1. Deposit current item if it exists
        ItemStack current = this.getMainHandStack();
        if (!current.isEmpty()) {
            depositToBarrel(current.copy());
            this.getInventory().setStack(this.getInventory().selectedSlot, ItemStack.EMPTY);
        }

        // 2. Fetch next item from chest
        ItemStack next = takeBestItemFromChest();

        if (next.isEmpty()) {
            sendOwnerMessage("Chest is empty. Bot is idle.", Formatting.RED);
            state = BotState.IDLE;
            return;
        }

        // 3. Equip it to main hand
        this.getInventory().setStack(this.getInventory().selectedSlot, next);
        sendOwnerMessage("Equipped: " + next.getName().getString(), Formatting.AQUA);
        state = BotState.ATTACKING;
    }

    /**
     * Takes the best available weapon from the chest, using a priority scoring system.
     */
    private ItemStack takeBestItemFromChest() {
        if (chestPos == null) return ItemStack.EMPTY;

        BlockEntity be = this.getWorld().getBlockEntity(chestPos);
        if (!(be instanceof Inventory chest)) return ItemStack.EMPTY;

        // Score all items and pick the best
        int bestSlot = -1;
        int bestScore = -1;

        for (int i = 0; i < chest.size(); i++) {
            ItemStack stack = chest.getStack(i);
            if (stack.isEmpty()) continue;

            int score = scoreItem(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if (bestSlot == -1) return ItemStack.EMPTY;

        ItemStack taken = chest.getStack(bestSlot).split(1);
        chest.markDirty();
        return taken;
    }

    /**
     * Scores an item for weapon priority.
     * Higher score = better weapon.
     */
    private int scoreItem(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        Item item = stack.getItem();

        // Swords — scored by material tier
        if (item instanceof SwordItem sword) {
            // Netherite = highest attack damage, so scoring by attack damage works
            if (item == Items.NETHERITE_SWORD) return 100;
            if (item == Items.DIAMOND_SWORD) return 90;
            if (item == Items.IRON_SWORD) return 80;
            if (item == Items.STONE_SWORD) return 70;
            if (item == Items.GOLDEN_SWORD) return 60;
            if (item == Items.WOODEN_SWORD) return 50;
            return 45; // Any other sword (modded, tagged)
        }

        // Axes — secondary weapon choice
        if (item instanceof AxeItem) {
            if (item == Items.NETHERITE_AXE) return 40;
            if (item == Items.DIAMOND_AXE) return 38;
            if (item == Items.IRON_AXE) return 36;
            if (item == Items.STONE_AXE) return 34;
            if (item == Items.GOLDEN_AXE) return 32;
            if (item == Items.WOODEN_AXE) return 30;
            return 28;
        }

        // Absolute fallback — take whatever is available
        return 1;
    }

    /**
     * Deposits a damaged item into the nearest barrel.
     * If no barrel exists or barrel is full, drops the item.
     */
    private void depositToBarrel(ItemStack damaged) {
        if (barrelPos == null) {
            this.dropItem(damaged, false);
            return;
        }

        BlockEntity be = this.getWorld().getBlockEntity(barrelPos);
        if (!(be instanceof Inventory barrel)) {
            this.dropItem(damaged, false);
            return;
        }

        for (int i = 0; i < barrel.size(); i++) {
            if (barrel.getStack(i).isEmpty()) {
                barrel.setStack(i, damaged);
                barrel.markDirty();
                return;
            }
        }

        // Barrel is full
        sendOwnerMessage("Barrel is full. Dropping damaged item.", Formatting.YELLOW);
        this.dropItem(damaged, false);
    }

    /**
     * Attempts to return held item to the chest.
     * Called when bot despawns via /ai stop.
     */
    public void returnItemToChest(ItemStack item) {
        if (chestPos == null || item.isEmpty()) {
            if (!item.isEmpty()) this.dropItem(item, false);
            return;
        }

        BlockEntity be = this.getWorld().getBlockEntity(chestPos);
        if (!(be instanceof Inventory chest)) {
            this.dropItem(item, false);
            return;
        }

        for (int i = 0; i < chest.size(); i++) {
            if (chest.getStack(i).isEmpty()) {
                chest.setStack(i, item);
                chest.markDirty();
                return;
            }
        }

        // Chest is full — drop it
        this.dropItem(item, false);
    }

    // ─── Utility ────────────────────────────────────────────────────────

    /**
     * Returns durability as a percentage (1.0 = full, 0.0 = broken).
     */
    public float getDurabilityPercent(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) return 1.0f;
        return 1.0f - ((float) stack.getDamage() / (float) stack.getMaxDamage());
    }

    /**
     * Sends a chat message to the bot's owner.
     */
    public void sendOwnerMessage(String message, Formatting color) {
        if (owner != null && !owner.isDisconnected()) {
            owner.sendMessage(
                    Text.literal("[AI Bot] ").formatted(Formatting.GOLD)
                            .append(Text.literal(message).formatted(color)),
                    false
            );
        }
    }

    private String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    /**
     * Calculates the rotation vector from pitch and yaw.
     * Same as Entity.getRotationVector but with explicit parameters.
     */
    private static Vec3d getRotationVector(float pitch, float yaw) {
        float pitchRad = pitch * ((float) Math.PI / 180F);
        float yawRad = -yaw * ((float) Math.PI / 180F);
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);
        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);
        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }
}
