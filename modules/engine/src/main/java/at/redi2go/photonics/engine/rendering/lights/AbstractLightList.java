package at.redi2go.photonics.engine.rendering.lights;

import at.redi2go.photonics.game.minecraft.Id;
import at.redi2go.photonics.game.minecraft.IMinecraft;
import at.redi2go.photonics.game.minecraft.core.IBlockPos;
import at.redi2go.photonics.game.minecraft.world.level.IBlock;
import at.redi2go.photonics.game.minecraft.world.level.ILevel;
import at.redi2go.photonics.game.minecraft.world.level.chunk.IChunkSection;
import at.redi2go.photonics.engine.iris.IrisPack;
import at.redi2go.photonics.engine.config.PhConfig;
import at.redi2go.photonics.engine.config.PhConfigWatcher;
import at.redi2go.photonics.engine.config.lights.LightRegistry;
import at.redi2go.photonics.engine.iris.pipeline.uniforms.IrisDynamicUniformHolder;
import at.redi2go.photonics.engine.iris.pipeline.uniforms.IrisUniformHolder;
import at.redi2go.photonics.engine.iris.pipeline.uniforms.IUniformUpdateFrequency;
import at.redi2go.photonics.engine.RenderingComponent;
import at.redi2go.photonics.engine.rendering.SectionCopy;
import at.redi2go.photonics.engine.rendering.SectionManager;
import at.redi2go.photonics.engine.UniformUpdater;
import at.redi2go.photonics.engine.rendering.WorldOrigin;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public abstract class AbstractLightList implements Runnable, RenderingComponent {
    private static final int MAX_SECTIONS_PER_RUN = 48;
    private static final IBlock BLOCK_LAVA = IBlock.fromIdOrThrow(Id.fromNamespaceAndPath("minecraft", "lava"));

    private Thread compilerThread;
    private final ReentrantLock lock = new ReentrantLock();

    private boolean needsReload = false;
    private volatile boolean closed;
    private LightRegistry lightRegistry;
    private PhConfigWatcher<LightRegistry> lightsRegistryObserver;

    private final int maxLights;
    private final Supplier<WorldOrigin> worldOriginSupplier;

    private final SectionManager.SectionQueue sectionQueue;

    private final Object2LongMap<Vector3i> sectionHashes = new Object2LongOpenHashMap<>();

    private final ListMultimap<Vector3i, TracedLightPosition> tracedLightPositions;
    private final UniformUpdater uniformUpdater = new UniformUpdater();

    protected LightList lights;
    protected LightList mostRecentLights;

    @SuppressWarnings("UnstableApiUsage")
    public AbstractLightList(
            SectionManager sectionManager,
            int maxLights,
            Supplier<WorldOrigin> worldOriginSupplier
    ) {
        this.maxLights = maxLights;
        this.worldOriginSupplier = worldOriginSupplier;

        this.sectionQueue = sectionManager.newSectionQueue(true);

        this.tracedLightPositions = MultimapBuilder.hashKeys()
                .arrayListValues()
                .build();

        this.lightRegistry = PhConfig.getLightRegistry();

    }

    protected final void startCompiler() {
        if (compilerThread != null) throw new IllegalStateException("Light compiler already started");
        lightsRegistryObserver = PhConfig.watch(
                ignored -> PhConfig.getLightRegistry(),
                this::setLightRegistry
        );

        try {
            compilerThread = new Thread(this, "Photonics Light List Compiler");
            compilerThread.setDaemon(true);
            compilerThread.start();
        } catch (RuntimeException | Error failure) {
            lightsRegistryObserver.close();
            throw failure;
        }
    }

    private void setLightRegistry(LightRegistry lightRegistry) {
        if (closed) return;
        this.lightRegistry = lightRegistry;

        needsReload = true;
        compilerThread.interrupt();
    }

    @Override
    public void run() {
        while (true) {
            if (closed) return;
            if (Thread.interrupted() && !needsReload) return;

            try {
                sectionQueue.awaitTask();

                boolean needsUpload = false;

                var unloadedSections = sectionQueue.drainUnloadQueue();
                if (!unloadedSections.isEmpty()) {
                    needsUpload = true;
                    unloadSections(unloadedSections);
                }


                needsUpload |= reloadLights();

                var loadedSections = sectionQueue.drain(MAX_SECTIONS_PER_RUN);
                if (!loadedSections.isEmpty() && addNewSections(loadedSections))
                    needsUpload = true;

                if (needsUpload) {
                    var newLights = trimLights();
                    storeLights(newLights);
                }
            } catch (InterruptedException e) {
                if (closed || !needsReload) return;
            }
        }
    }

    // Compiler stages

    private void unloadSections(List<Vector3i> unloadedSections) {
        for (var section : unloadedSections) {
            tracedLightPositions.removeAll(section);
            sectionHashes.removeLong(section);
        }
    }

    private boolean reloadLights() {
        if (!needsReload) return false;
        needsReload = false;

        for (var section : tracedLightPositions.keySet()) {
            var lights = tracedLightPositions.get(section);

            for (var itr = lights.listIterator(); itr.hasNext(); ) {
                var lightPosition = itr.next();
                var newLightInfo = lightRegistry.get(lightPosition.blockState());

                if (newLightInfo != null) {
                    itr.set(new TracedLightPosition(
                            lightPosition.blockId(),
                            lightPosition.pos(),
                            lightPosition.blockState(),
                            newLightInfo
                    ));
                } else itr.remove();
            }
        }

        return true;
    }

    private boolean addNewSections(List<SectionCopy> newSections) {
        var level = IMinecraft.getLevel();
        if (level == null) return false;

        var shaderPack = IrisPack.getCurrentPack();
        boolean changed = false;

        for (var section : newSections) {
            var sectionHash = section.computeSectionHash(null);
            if (sectionHashes.put(section.pos(), sectionHash) == sectionHash) continue;

            changed = true;
            var lights = tracedLightPositions.get(section.pos());
            lights.clear();

            section.forEachBlock((blockChunkOffset, blockPos, block) -> {
                var light = lightRegistry.get(block);
                if (light == null || !light.isTraced()) return;

                if (!shouldCullLight(section, level, blockPos))
                    lights.add(
                            new TracedLightPosition(
                                    shaderPack.map(e -> e.ph$getBlockId(block)).orElse(-1),
                                    new Vector3d(blockPos.ph$x(), blockPos.ph$y(), blockPos.ph$z()).add(0.5, 0.5, 0.5),
                                    block,
                                    light
                            )
                    );
            });

            if (lights.isEmpty())
                tracedLightPositions.removeAll(section.pos());
        }

        return changed;
    }

    private boolean shouldCullLight(
            SectionCopy blockOwner,
            ILevel level,
            IBlockPos blockPos
    ) {
        IChunkSection section = blockOwner;
        Vector3i sectionPos = blockOwner.pos();

        for (var offset : NEIGHBORS) {
            var neighborBlockPos = blockPos.ph$offset(offset);
            var blockSectionPos = SectionCopy.getSectionCoord(neighborBlockPos);

            if (!blockSectionPos.equals(sectionPos)) {
                if (blockSectionPos.equals(blockOwner.pos())) {
                    section = blockOwner;
                } else {
                    var chunkAccess = level.ph$getChunkOrNull(sectionPos.x, sectionPos.z);
                    if (chunkAccess == null) continue;

                    var newSection = chunkAccess.ph$sections()[level.ph$getSectionIndexFromSectionY(sectionPos.y)];
                    if (newSection == null) continue;

                    section = newSection;
                }
            }

            if (section.ph$hasOnlyAir()) continue;

            var blockState = section.ph$getBlockState(
                    neighborBlockPos.ph$x() & 15,
                    neighborBlockPos.ph$y() & 15,
                    neighborBlockPos.ph$z() & 15
            );

            if (blockState.ph$is(BLOCK_LAVA)) continue;

            if (blockState.ph$isAir() || !blockState.ph$isViewBlocking(level, neighborBlockPos))
                return false;
        }

        return true;
    }

    private LightList trimLights() {
        var loadedLights = tracedLightPositions.values().toArray(TracedLightPosition[]::new);
        if (loadedLights.length < maxLights) {
            return new LightList(loadedLights, WorldOrigin.get());
        }

        Vector3d cameraPosition = IMinecraft.getCameraPos();
        int mod = (int) System.nanoTime();

        Arrays.sort(
                loadedLights,
                Comparator.comparingDouble(light -> -light.getLuminance(cameraPosition, mod))
        );

        return new LightList(
                Arrays.copyOf(loadedLights, maxLights),
                WorldOrigin.get()
        );
    }

    protected abstract void storeLight(int index, Vector4f[] light);

    protected abstract void storeMapping(int beforeIndex, int afterIndex);

    protected abstract void clearMapping();

    protected abstract void prepareUpload();

    private void storeLights(LightList lights) throws InterruptedException {
        if (Objects.equals(mostRecentLights, lights)) return;

        lock.lockInterruptibly();

        try {
            this.lights = lights;
            var worldOrigin = lights.origin();

            for (int i = 0; i < lights.size(); i++) {
                var light = lights.get(i);

                storeLight(
                        i,
                        light.lightInfo().toVector4Array(
                                new Vector3f(worldOrigin.applyOffset(light.pos())),
                                light.blockId()
                        )
                );
            }

            if (mostRecentLights != null)
                lights.createMapping(mostRecentLights)
                        .forEachIndex(this::storeMapping);

            prepareUpload();
            uniformUpdater.updateNextFrame();
        } finally {
            lock.unlock();
        }
    }

    // Upload

    protected abstract void upload();

    @Override
    public void onFrameBegin() {
        lock.lock();

        try {
            upload();
            uniformUpdater.updateAll();

            if (lights != mostRecentLights) {
                mostRecentLights = lights;
                clearMapping();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void registerUniforms(IrisUniformHolder uniforms) {
        uniforms.uniform3f(IUniformUpdateFrequency.perFrame(), "light_list_offset", () -> {
            var listOrigin = mostRecentLights == null ? null : mostRecentLights.origin();
            var realOrigin = worldOriginSupplier.get();

            if (listOrigin == null || realOrigin == null)
                return new Vector3f(0f);

            return new Vector3f(realOrigin.sub(listOrigin, new Vector3d()));
        });
    }

    @Override
    public void registerDynamicUniforms(IrisDynamicUniformHolder dynamicUniforms) {
        dynamicUniforms.uniform1i("light_list_size", () -> mostRecentLights == null ? 0 : mostRecentLights.size(), uniformUpdater.newNotifier());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        needsReload = false;
        if (lightsRegistryObserver != null)
            lightsRegistryObserver.close();
        if (compilerThread == null) return;
        compilerThread.interrupt();

        boolean interrupted = false;
        while (compilerThread.isAlive()) {
            try {
                compilerThread.join();
            } catch (InterruptedException e) {
                interrupted = true;
                compilerThread.interrupt();
            }
        }

        sectionHashes.clear();
        tracedLightPositions.clear();
        lights = null;
        mostRecentLights = null;
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    private static final Vector3i[] NEIGHBORS = new Vector3i[]{
            new Vector3i(0, 1, 0),
            new Vector3i(0, -1, 0),

            new Vector3i(1, 0, 0),
            new Vector3i(-1, 0, 0),

            new Vector3i(0, 0, 1),
            new Vector3i(0, 0, -1),
    };
}
