package fr.iglee42.placeableingots;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IngotBlock extends BaseEntityBlock {

    public static final IntegerProperty COUNT = IntegerProperty.create("count",0,64);

    public IngotBlock() {
        super(Properties.copy(Blocks.IRON_BLOCK).noOcclusion().strength(5,6));
        registerDefaultState(defaultBlockState().setValue(COUNT,0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(COUNT));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter p_60556_, BlockPos p_60557_, CollisionContext p_60558_) {
        return Shapes.box(0,0,0,1, (double) ((int) Math.ceil((double) state.getValue(COUNT) / 8)) /8,1);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new IngotBlockEntity(blockPos,blockState);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof IngotBlockEntity ingotBlockEntity) {
            ingotBlockEntity.flushDelayedSync();
            ingotBlockEntity.markForSync();
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState p_49232_) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }

        if (type == PlaceableIngots.INGOT_BLOCK_ENTITY.get()) {
            BlockEntityTicker<IngotBlockEntity> ticker = (lvl, pos, blockState, blockEntity) ->
                    IngotBlockEntity.serverTick(lvl, pos, blockState, blockEntity);
            return (BlockEntityTicker<T>) ticker;
        }

        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult p_60508_) {
        if (level.isClientSide()) return InteractionResult.sidedSuccess(true);
        if (super.use(state, level, pos, player, hand, p_60508_).consumesAction()) return InteractionResult.CONSUME;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof IngotBlockEntity be)) return InteractionResult.PASS;
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (player.isCrouching()) return InteractionResult.PASS;
        player.addItem(be.removeLastIngot());
        if (be.getIngots().isEmpty()) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return InteractionResult.SUCCESS;
    }

    @Override
    public List<ItemStack> getDrops(BlockState p_287732_, LootParams.Builder p_287596_) {
        return List.of();
    }



    @Override
    public void onRemove(BlockState bs, Level level, BlockPos pos, BlockState newState, boolean p_60519_) {
        if (level.getBlockEntity(pos) instanceof IngotBlockEntity be && newState.getBlock() != this){
            be.getIngots().forEach(i->{
                Block.popResource(level,pos,i);
            });
        }
        if (level.getBlockState(pos.offset(0,1,0)).is(PlaceableIngots.INGOT_BLOCK.get())){
            level.destroyBlock(pos.offset(0,1,0),true);
        }
        super.onRemove(bs, level, pos, newState, p_60519_);
    }
}
