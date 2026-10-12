package at.redi2go.photonics.common;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceReloaderListener {
    private static final Set<Runnable> DEPENDANTS = ConcurrentHashMap.newKeySet();

    static {
        ((ReloadableResourceManager) Minecraft.getInstance().getResourceManager())
                .registerReloadListener((sharedState, prepareExecutor, preparationBarrier, applyExecutor) ->
                        preparationBarrier.wait(Unit.INSTANCE)
                                .thenRunAsync(() -> {
                                    for (var runnable : DEPENDANTS)
                                        runnable.run();
                                }, applyExecutor));
    }

    public static void add(Runnable runnable) {
        DEPENDANTS.add(runnable);
    }

    public static void remove(Runnable runnable) {
        DEPENDANTS.remove(runnable);
    }
}
