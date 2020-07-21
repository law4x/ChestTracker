package red.jackf.chesttracker.tracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import red.jackf.chesttracker.ChestTracker;
import red.jackf.chesttracker.config.InteractRememberType;
import red.jackf.chesttracker.render.RenderManager;

import java.util.List;
import java.util.stream.Collectors;

public class Tracker {
    private static final Tracker TRACKER = new Tracker();
    private BlockPos lastInteractedPos = BlockPos.ORIGIN;

    public void setLastPos(BlockPos newPos) {
        this.lastInteractedPos = newPos.toImmutable();
    }

    public static Tracker getInstance() {
        return TRACKER;
    }

    public <T extends ScreenHandler> void handleScreen(HandledScreen<T> screen) {
        if (MinecraftClient.getInstance().player == null)
            return;

        String className = screen.getClass().getSimpleName();
        if (ChestTracker.CONFIG.miscOptions.debugPrint) {
            ChestTracker.sendDebugMessage(MinecraftClient.getInstance().player, validScreenToTrack(className) ?
                    new TranslatableText("chesttracker.gui_class_name_tracked", className).formatted(Formatting.GREEN) :
                    new TranslatableText("chesttracker.gui_class_name_not_tracked", className).formatted(Formatting.RED));
        }

        if (!validScreenToTrack(className))
            return;

        ScreenHandler handler = screen.getScreenHandler();
        List<ItemStack> items = handler.slots.stream()
                .filter(slot -> !(slot.inventory instanceof PlayerInventory))
                .filter(Slot::hasStack)
                .map(Slot::getStack)
                .collect(Collectors.toList());

        LocationStorage storage = LocationStorage.get();
        if (storage == null) return;
        storage.mergeItems(this.lastInteractedPos, MinecraftClient.getInstance().player.world, items);
    }

    public void handleInteract(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        boolean blockHasBE = world.getBlockState(hitResult.getBlockPos()).getBlock().hasBlockEntity();
        if (ChestTracker.CONFIG.miscOptions.blockInteractionType == InteractRememberType.ALL || blockHasBE) {
            Tracker.getInstance().setLastPos(hitResult.getBlockPos());
        }
        if (ChestTracker.CONFIG.miscOptions.debugPrint)
            ChestTracker.sendDebugMessage(player, new TranslatableText("chesttracker.block_clicked_" + (blockHasBE ? "be_provider" : "not_be_provider"),
                Registry.BLOCK.getId(world.getBlockState(hitResult.getBlockPos()).getBlock()))
                .formatted(blockHasBE ? Formatting.GREEN : Formatting.YELLOW));
    }

    public ActionResult searchForItem(ItemStack toFind) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return ActionResult.PASS;
        LocationStorage storage = LocationStorage.get();
        if (storage == null) return ActionResult.PASS;
        if (ChestTracker.CONFIG.miscOptions.debugPrint)
            ChestTracker.sendDebugMessage(client.player, new TranslatableText("chesttracker.searching_for_item", toFind).formatted(Formatting.GREEN));

        List<Location> results = storage.findItems(client.player.clientWorld.getDimensionRegistryKey().getValue(), toFind);
        if (results.size() > 0) {
            RenderManager.getInstance().addRenderList(results.stream().map(Location::getPosition).collect(Collectors.toList()), client.world.getTime());
            client.player.closeHandledScreen();
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    public boolean validScreenToTrack(String screenClass) {
        return !ChestTracker.CONFIG.trackedScreens.blocklist.contains(screenClass);
    }
}
