package gregtech.core.unification.material.internal;

import com.google.common.base.Preconditions;
import gregtech.api.GTValues;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.registry.IMaterialRegistryManager;
import gregtech.api.unification.material.registry.MaterialRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class MaterialRegistryManager implements IMaterialRegistryManager {

    private static MaterialRegistryManager INSTANCE;

    private final Object2ObjectMap<String, MaterialRegistryImpl> registries = new Object2ObjectOpenHashMap<>();
    private final Int2ObjectMap<MaterialRegistryImpl> networkIds = new Int2ObjectOpenHashMap<>();

    @Nullable
    private Collection<Material> registeredMaterials;

    private final MaterialRegistryImpl gregtechRegistry = createInternalRegistry();

    private Phase registrationPhase = Phase.PRE;

    private MaterialRegistryManager() {}

    public static MaterialRegistryManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MaterialRegistryManager();
        }
        return INSTANCE;
    }

    @Nonnull
    @Override
    public MaterialRegistry createRegistry(@Nonnull String modid) {
        if (getPhase() != Phase.PRE) {
            throw new IllegalStateException("Cannot create registries in phase " + getPhase());
        }

        Preconditions.checkArgument(!registries.containsKey(modid),
                "Material registry already exists for modid %s", modid);
        MaterialRegistryImpl registry = new MaterialRegistryImpl(modid);
        registries.put(modid, registry);
        networkIds.put(registry.getNetworkId(), registry);
        return registry;
    }

    @Nonnull
    @Override
    public MaterialRegistry getRegistry(@Nonnull String modid) {
        MaterialRegistry registry = registries.get(modid);
        return registry != null ? registry : gregtechRegistry;
    }

    @Nonnull
    @Override
    public MaterialRegistry getRegistry(int networkId) {
        MaterialRegistry registry = networkIds.get(networkId);
        return registry != null ? registry : gregtechRegistry;
    }

    @Nonnull
    @Override
    public Collection<MaterialRegistry> getRegistries() {
        if (getPhase() == Phase.PRE) {
            throw new IllegalStateException("Cannot get all material registries during phase " + getPhase());
        }
        return Collections.unmodifiableCollection(registries.values());
    }

    @Nonnull
    @Override
    public Collection<Material> getRegisteredMaterials() {
        if (registeredMaterials == null ||
                (getPhase() != Phase.CLOSED && getPhase() != Phase.FROZEN)) {
            throw new IllegalStateException("Cannot retrieve all materials before registration");
        }
        return registeredMaterials;
    }

    @Nullable
    @Override
    public Material getMaterial(@Nonnull String name) {
        if (!name.isEmpty()) {
            String modid;
            String materialName;
            int index = name.indexOf(':');
            if (index >= 0) {
                modid = name.substring(0, index);
                materialName = name.substring(index + 1);
            } else {
                modid = GTValues.MODID;
                materialName = name;
            }
            return getRegistry(modid).getObject(materialName);
        }
        return null;
    }

    @Nonnull
    @Override
    public Phase getPhase() {
        return registrationPhase;
    }

    public void unfreezeRegistries() {
        registries.values().forEach(MaterialRegistryImpl::unfreeze);
        registrationPhase = Phase.OPEN;
    }

    public void closeRegistries() {
        registries.values().forEach(MaterialRegistryImpl::closeRegistry);
        Collection<Material> collection = new ArrayList<>();
        for (MaterialRegistry registry : registries.values()) {
            collection.addAll(registry.getAllMaterials());
        }
        registeredMaterials = Collections.unmodifiableCollection(collection);
        registrationPhase = Phase.CLOSED;
    }

    public void freezeRegistries() {
        registries.values().forEach(MaterialRegistryImpl::freeze);
        registrationPhase = Phase.FROZEN;
    }

    @Nonnull
    private MaterialRegistryImpl createInternalRegistry() {
        MaterialRegistryImpl registry = new MaterialRegistryImpl(GTValues.MODID);
        this.registries.put(GTValues.MODID, registry);
        return registry;
    }

    @Nonnull
    public Material getDefaultFallback() {
        return gregtechRegistry.getFallbackMaterial();
    }
}
