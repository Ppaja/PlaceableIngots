package fr.iglee42.placeableingots;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static fr.iglee42.placeableingots.IngotBlock.COUNT;

public class IngotBlockEntity extends BlockEntity {

    private final List<ItemStack> ingots = new ArrayList<>();
    private boolean delayedSyncQueued;

    public IngotBlockEntity(BlockPos pos, BlockState state) {
        super(PlaceableIngots.INGOT_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        ingots.forEach(s->list.add(s.save(new CompoundTag())));
        tag.put("items",list);
    }

    @Override
    public void load(CompoundTag tag) {
        ingots.clear();
        tag.getList("items", ListTag.TAG_COMPOUND).forEach(t -> ingots.add(ItemStack.of((CompoundTag) t)));
        super.load(tag);
        delayedSyncQueued = false;
        refreshClientRendering();
    }

    public List<ItemStack> getIngots() {
        return ingots;
    }

    public boolean addIngot(ItemStack ingot){
        if (ingot.isEmpty()) {
            return false;
        }

        if (ingots.size() >= 64) {
            return false;
        }

        boolean scheduleDelayedRefresh = ingots.isEmpty();

        ingots.add(ingot.copyWithCount(1));
        markForSync();

        if (scheduleDelayedRefresh) {
            queueDelayedSync();
        }
        return true;
    }


    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        ingots.forEach(s->list.add(s.save(new CompoundTag())));
        tag.put("items",list);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }


    public ItemStack removeLastIngot() {
        if (ingots.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = ingots.remove(ingots.size() - 1);
        markForSync();
        if (ingots.isEmpty()) {
            delayedSyncQueued = false;
        }
        return stack.copyWithCount(1);
    }

    public void markForSync()
    {
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockState updatedState = updateBlockState();
        sendVanillaUpdatePacket();
        level.sendBlockUpdated(worldPosition, updatedState, updatedState, Block.UPDATE_CLIENTS);
        setChanged();
    }

    private void queueDelayedSync()
    {
        if (delayedSyncQueued || level == null || level.isClientSide()) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof IngotBlock)) {
            return;
        }

        delayedSyncQueued = true;
        serverLevel.scheduleTick(worldPosition, state.getBlock(), 2);
    }

    public void flushDelayedSync()
    {
        if (!delayedSyncQueued || level == null || level.isClientSide()) {
            return;
        }

        delayedSyncQueued = false;
        markForSync();
    }

    public final void sendVanillaUpdatePacket()
    {
        final ClientboundBlockEntityDataPacket packet = getUpdatePacket();
        final BlockPos pos = getBlockPos();
        if (packet != null && level instanceof ServerLevel serverLevel)
        {
            serverLevel.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false).forEach(e -> e.connection.send(packet));
        }
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet)
    {
        super.onDataPacket(connection, packet);
        refreshClientRendering();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag)
    {
        super.handleUpdateTag(tag);
        refreshClientRendering();
    }

    private BlockState updateBlockState() {
        if (level == null) {
            return getBlockState();
        }

        BlockState currentState = level.getBlockState(worldPosition);
        if (!(currentState.getBlock() instanceof IngotBlock)) {
            return currentState;
        }

        int ingotCount = Math.min(ingots.size(), 64);
        if (currentState.getValue(COUNT) != ingotCount) {
            BlockState updatedState = currentState.setValue(COUNT, ingotCount);
            level.setBlock(worldPosition, updatedState, Block.UPDATE_CLIENTS);
            return updatedState;
        }

        return currentState;
    }

    private void refreshClientRendering()
    {
        if (level != null && level.isClientSide())
        {
            requestModelDataUpdate();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }
}
