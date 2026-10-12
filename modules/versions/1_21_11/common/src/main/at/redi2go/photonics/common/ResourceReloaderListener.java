package at.redi2go.photonics.common;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceReloaderListener {
    private static final Set<Runnable> DEPENDANTS = ConcurrentHashMap.newKeySet();

    static {
        ((ReloadableResourceManager) Minecraft.getInstance().getResourceManager())
                .registerReloadListener((sharedState, applyExecutor, preparationBarrier, executor) ->
                        preparationBarrier.wait(Unit.INSTANCE).thenRun(() -> {
                            for (Runnable dependant : DEPENDANTS)
                                dependant.run();
                        }));
    }

    private ResourceReloaderListener() {
    }

    public static void add(Runnable listener) {
        DEPENDANTS.add(listener);
    }

    public static void remove(Runnable listener) {
        DEPENDANTS.remove(listener);
    }
}
