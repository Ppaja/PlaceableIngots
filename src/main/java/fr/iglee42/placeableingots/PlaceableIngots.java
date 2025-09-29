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

        event.getLevel().setBlockAndUpdate(placementPos, INGOT_BLOCK.get().defaultBlockState());
        boolean added = event.getLevel().getBlockEntity(placementPos, INGOT_BLOCK_ENTITY.get())
                .map(be -> ((IngotBlockEntity) be).addIngot(heldStack))
                .orElse(false);

        if (!added) {
            event.getLevel().removeBlock(placementPos, false);
            return false;
        }

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(placementPos, INGOT_BLOCK.get(), 1);
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
