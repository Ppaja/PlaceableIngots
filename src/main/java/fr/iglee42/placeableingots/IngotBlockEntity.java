package fr.iglee42.placeableingots;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static fr.iglee42.placeableingots.IngotBlock.COUNT;

public class IngotBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    private List<ItemStack> ingots = new ArrayList<>();
    private ResourceLocation lastItemId;

    public IngotBlockEntity(BlockPos pos, BlockState state) {
        super(PlaceableIngots.INGOT_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        ingots.forEach(s->list.add(s.save(new CompoundTag())));
        tag.put("items",list);
        if (lastItemId != null) {
            tag.putString("last_item", lastItemId.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        ingots.clear();
        tag.getList("items",ListTag.TAG_COMPOUND).forEach(t-> ingots.add(ItemStack.of((CompoundTag) t)));
        if (tag.contains("last_item")) {
            String id = tag.getString("last_item");
            if (!id.isEmpty()) {
                lastItemId = ResourceLocation.tryParse(id);
            }
        }
        super.load(tag);
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

        ingots.add(ingot.copyWithCount(1));
        // track last item id for client fallback rendering
        net.minecraft.world.item.Item item = ingot.getItem();
        net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        if (key != null) {
            lastItemId = key;
        }
        markForSync();
        LOGGER.info("[PI] BE addIngot: size={}, pos={}", ingots.size(), getBlockPos());
        return true;
    }


    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        ingots.forEach(s->list.add(s.save(new CompoundTag())));
        tag.put("items",list);
        if (lastItemId != null) {
            tag.putString("last_item", lastItemId.toString());
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        LOGGER.info("[PI] BE onDataPacket: client updated, size={}, pos={}", ingots.size(), getBlockPos());
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        LOGGER.info("[PI] BE handleUpdateTag: client updated, size={}, pos={}", ingots.size(), getBlockPos());
    }


    public ItemStack removeLastIngot() {
        if (ingots.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = ingots.remove(ingots.size() - 1);
        markForSync();
        LOGGER.info("[PI] BE removeLastIngot: size={}, pos={}", ingots.size(), getBlockPos());
        return stack.copyWithCount(1);
    }

    public void markForSync()
    {
        // Update block state first, then send BE data
        updateBlockState();
        sendVanillaUpdatePacket();
        setChanged();
        LOGGER.info("[PI] BE markForSync: stateCount={}, size={}, pos={}",
                level != null && level.getBlockState(worldPosition).hasProperty(IngotBlock.COUNT) ? level.getBlockState(worldPosition).getValue(IngotBlock.COUNT) : -1,
                ingots.size(), getBlockPos());
    }

    public final void sendVanillaUpdatePacket()
    {
        final ClientboundBlockEntityDataPacket packet = getUpdatePacket();
        final BlockPos pos = getBlockPos();
        if (packet != null && level instanceof ServerLevel serverLevel)
        {
            var recipients = serverLevel.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
            int count = 0;
            for (var e : recipients) {
                e.connection.send(packet);
                count++;
            }
            LOGGER.info("[PI] BE sendVanillaUpdatePacket: sent to {} players at {}", count, pos);
            if (count == 0) {
                // If no one tracked this chunk yet, retry next tick
                serverLevel.getServer().execute(() -> {
                    var againRecipients = serverLevel.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
                    int again = 0;
                    for (var e : againRecipients) {
                        e.connection.send(getUpdatePacket());
                        again++;
                    }
                    LOGGER.info("[PI] BE resend next tick: sent to {} players at {}", again, pos);
                });
            }
        }
    }

    private void updateBlockState() {
        if (level == null) {
            return;
        }

        BlockState currentState = level.getBlockState(worldPosition);
        if (!(currentState.getBlock() instanceof IngotBlock)) {
            return;
        }

        int ingotCount = Math.min(ingots.size(), 64);
        if (currentState.getValue(COUNT) != ingotCount) {
            level.setBlock(worldPosition, currentState.setValue(COUNT, ingotCount), Block.UPDATE_CLIENTS);
        }
    }

    public ResourceLocation getLastItemId() {
        return lastItemId;
    }
}
