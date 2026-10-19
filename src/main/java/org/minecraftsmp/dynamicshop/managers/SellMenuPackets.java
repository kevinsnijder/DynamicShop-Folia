package org.minecraftsmp.dynamicshop.managers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Sends client-only copies using the current server's packet constructor and menu revision. */
public final class SellMenuPackets {
    @FunctionalInterface interface Sender {
        void send(Player player, Map<Integer, ItemStack> items) throws ReflectiveOperationException;
    }
    private static final Map<Class<?>, Bridge> BRIDGES = new ConcurrentHashMap<>();

    private SellMenuPackets() {}

    public static void send(Player player, Map<Integer, ItemStack> items) throws ReflectiveOperationException {
        Class<?> playerClass = player.getClass();
        Bridge bridge = BRIDGES.get(playerClass);
        if (bridge == null) {
            String craftPackage = playerClass.getPackageName().replaceFirst("\\.entity$", "");
            bridge = new Bridge(playerClass,
                    Class.forName(craftPackage + ".inventory.CraftItemStack"),
                    Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket"),
                    Class.forName("net.minecraft.network.protocol.Packet"));
            BRIDGES.put(playerClass, bridge);
        }
        bridge.send(player, items);
    }

    static final class Bridge implements Sender {
        private final Method getHandle, getStateId, asNmsCopy, send;
        private final Field menu, windowId, connection;
        private final Constructor<?> slotPacket;

        Bridge(Class<?> playerClass, Class<?> craftItemClass, Class<?> slotClass, Class<?> packetClass)
                throws ReflectiveOperationException {
            getHandle = playerClass.getMethod("getHandle");
            menu = getHandle.getReturnType().getField("containerMenu");
            windowId = menu.getType().getField("containerId");
            getStateId = menu.getType().getMethod("getStateId");
            connection = getHandle.getReturnType().getField("connection");
            send = connection.getType().getMethod("send", packetClass);
            asNmsCopy = craftItemClass.getMethod("asNMSCopy", ItemStack.class);
            slotPacket = slotClass.getConstructor(int.class, int.class, int.class, asNmsCopy.getReturnType());
        }

        @Override public void send(Player player, Map<Integer, ItemStack> items) throws ReflectiveOperationException {
            Object handle = getHandle.invoke(player);
            Object container = menu.get(handle);
            int id = windowId.getInt(container);
            if (id < 1) return;
            int state = (int) getStateId.invoke(container);
            Object listener = connection.get(handle);
            for (var entry : items.entrySet()) {
                Object item = asNmsCopy.invoke(null, entry.getValue());
                Object packet = slotPacket.newInstance(id, state, entry.getKey(), item);
                send.invoke(listener, packet);
            }
        }
    }
}
