package fr.iglee42.placeableingots;

import com.mojang.logging.LogUtils;
import fr.iglee42.placeableingots.client.models.IngotBlockModel;
import fr.iglee42.placeableingots.config.ClientConfig;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.io.IOException;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(PlaceableIngots.MODID)
public class PlaceableIngots {
    public static final String MODID = "placeableingots";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final RegistryObject<Block> INGOT_BLOCK = BLOCKS.register("ingot_block", IngotBlock::new);
    public static final RegistryObject<BlockEntityType<?>> INGOT_BLOCK_ENTITY = BLOCK_ENTITIES.register("ingot_block", ()->
            BlockEntityType.Builder.of(IngotBlockEntity::new,INGOT_BLOCK.get()).build(null));

    public PlaceableIngots() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Network.init();
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
    }

    @SubscribeEvent
    public void useItemOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        ItemStack heldStack = event.getItemStack();

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!heldStack.is(Tags.Items.INGOTS) || !player.mayBuild() || !player.isCrouching()) {
            return;
        }

        BlockPos clickedPos = event.getHitVec().getBlockPos();
        LOGGER.info("[PI] RightClickBlock: server-side, item={}, pos={}, face={}, sneaking=YES", heldStack.getItem(), clickedPos, event.getFace());
        if (tryAddToExistingStacks(event, clickedPos, heldStack, player)) {
            return;
        }

        BlockPos placementPos = clickedPos.relative(event.getFace());
        if (tryPlaceNewStack(event, placementPos, heldStack, player)) {
            return;
        }
    }

    private boolean tryAddToExistingStacks(PlayerInteractEvent.RightClickBlock event, BlockPos startPos, ItemStack heldStack, Player player) {
        MutableBlockPos cursor = new MutableBlockPos();

        int buildLimit = event.getLevel().getMaxBuildHeight();
        for (int currentY = startPos.getY(); currentY < buildLimit; currentY++) {
            cursor.set(startPos.getX(), currentY, startPos.getZ());
            BlockState state = event.getLevel().getBlockState(cursor);
            if (!state.is(INGOT_BLOCK.get())) {
                break;
            }

            if (state.getValue(IngotBlock.COUNT) >= 64) {
                continue;
            }

            if (event.getLevel().getBlockEntity(cursor, INGOT_BLOCK_ENTITY.get()).map(be -> ((IngotBlockEntity) be).addIngot(heldStack)).orElse(false)) {
                LOGGER.info("[PI] Added ingot to existing stack at {}", cursor);
                consumeItem(heldStack, player);
                acknowledgeIngotPlacement(event, player);
                return true;
            }
        }

        return false;
    }

    private boolean tryPlaceNewStack(PlayerInteractEvent.RightClickBlock event, BlockPos placementPos, ItemStack heldStack, Player player) {
        if (!event.getLevel().getBlockState(placementPos).isAir()) {
            return false;
        }

        LOGGER.info("[PI] Placing new ingot_block at {}", placementPos);
        event.getLevel().setBlockAndUpdate(placementPos, INGOT_BLOCK.get().defaultBlockState());
        boolean added = event.getLevel().getBlockEntity(placementPos, INGOT_BLOCK_ENTITY.get())
                .map(be -> ((IngotBlockEntity) be).addIngot(heldStack))
                .orElse(false);

        if (!added) {
            LOGGER.warn("[PI] Failed to add first ingot to new stack at {} — removing block", placementPos);
            event.getLevel().removeBlock(placementPos, false);
            return false;
        }

        // Removed previous workaround (add+remove) as it did not resolve the issue and added complexity

        // Push a BE sync now
        event.getLevel().getBlockEntity(placementPos, INGOT_BLOCK_ENTITY.get())
                .ifPresent(be -> ((IngotBlockEntity) be).markForSync());

        // Also directly send the BE packet to the placing player to avoid missing initial client update
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            event.getLevel().getBlockEntity(placementPos, INGOT_BLOCK_ENTITY.get()).ifPresent(be -> {
                net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt = ((IngotBlockEntity) be).getUpdatePacket();
                if (pkt == null) {
                    pkt = net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(be);
                }
                if (pkt != null) {
                    sp.connection.send(pkt);
                }
                // Send first-ingot hint (item id) to client for immediate fallback coloring
                Network.sendFirstIngotHint(sp, placementPos, net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(heldStack.getItem()));
            });
        }

        // Additionally, force a block update immediately to flush client render
        BlockState postAddState = event.getLevel().getBlockState(placementPos);
        LOGGER.info("[PI] Post-add state at {}: COUNT={}", placementPos, postAddState.hasProperty(IngotBlock.COUNT) ? postAddState.getValue(IngotBlock.COUNT) : -1);
        event.getLevel().sendBlockUpdated(placementPos, postAddState, postAddState, Block.UPDATE_CLIENTS);

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(placementPos, INGOT_BLOCK.get(), 1);
            // Extra small delay to cover race conditions on first placement render
            serverLevel.scheduleTick(placementPos, INGOT_BLOCK.get(), 3);
            LOGGER.info("[PI] Scheduled ticks for {} (1 and 3)", placementPos);

            // Schedule a delayed explicit BE packet resend to all tracking players
            serverLevel.getServer().execute(() -> {
                event.getLevel().getBlockEntity(placementPos, INGOT_BLOCK_ENTITY.get()).ifPresent(be -> {
                    net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket tmp = ((IngotBlockEntity) be).getUpdatePacket();
                    if (tmp == null) tmp = net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(be);
                    final net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket finalPkt = tmp;
                    if (finalPkt != null) {
                        serverLevel.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(placementPos), false)
                                .forEach(p -> p.connection.send(finalPkt));
                        LOGGER.info("[PI] Delayed resend: BE packet sent to tracking players at {}", placementPos);
                    }
                });
            });
        }

        acknowledgeIngotPlacement(event, player);
        consumeItem(heldStack, player);
        return true;
    }

    private void acknowledgeIngotPlacement(PlayerInteractEvent.RightClickBlock event, Player player) {
        event.setUseItem(Event.Result.ALLOW);
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
        if (player != null) {
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void consumeItem(ItemStack stack, Player player){
        if (player == null || player.getAbilities().instabuild) {
            return;
        }

        stack.shrink(1);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) throws IOException, IllegalAccessException {
            ClientConfig.load();
            ItemBlockRenderTypes.setRenderLayer(INGOT_BLOCK.get(), RenderType.cutoutMipped());
        }


        @SubscribeEvent
        public static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event)
        {
            event.register("ingot_block", IngotBlockModel.INSTANCE);
        }

    }
}
