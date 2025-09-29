package fr.iglee42.placeableingots;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class Network {

    private static final String PROTO = "1";
    private static SimpleChannel channel;

    // client-only hint cache for first-ingot color before NBT arrives
    public static final Map<BlockPos, ResourceLocation> CLIENT_HINT = new ConcurrentHashMap<>();

    public static void init() {
        channel = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(PlaceableIngots.MODID, "main"))
                .networkProtocolVersion(() -> PROTO)
                .clientAcceptedVersions(PROTO::equals)
                .serverAcceptedVersions(PROTO::equals)
                .simpleChannel();

        channel.messageBuilder(S2CFirstIngotHint.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFirstIngotHint::encode)
                .decoder(S2CFirstIngotHint::decode)
                .consumerMainThread(S2CFirstIngotHint::handle)
                .add();
    }

    public static void sendFirstIngotHint(net.minecraft.server.level.ServerPlayer player, BlockPos pos, ResourceLocation itemId) {
        if (itemId == null || channel == null) return;
        channel.send(PacketDistributor.PLAYER.with(() -> player), new S2CFirstIngotHint(pos, itemId));
    }

    public static class S2CFirstIngotHint {
        public final BlockPos pos;
        public final ResourceLocation itemId;

        public S2CFirstIngotHint(BlockPos pos, ResourceLocation itemId) {
            this.pos = pos;
            this.itemId = itemId;
        }

        public static void encode(S2CFirstIngotHint msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeResourceLocation(msg.itemId);
        }

        public static S2CFirstIngotHint decode(FriendlyByteBuf buf) {
            return new S2CFirstIngotHint(buf.readBlockPos(), buf.readResourceLocation());
        }

        public static void handle(S2CFirstIngotHint msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                CLIENT_HINT.put(msg.pos, msg.itemId);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}


