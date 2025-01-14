package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FluidTankList implements IMultipleTankHandler, INBTSerializable<NBTTagCompound> {

    private final MultiFluidTankEntry[] fluidTanks;
    private final boolean allowSameFluidFill;

    public FluidTankList(boolean allowSameFluidFill, IFluidTank... fluidTanks) {
        ArrayList<MultiFluidTankEntry> list = new ArrayList<>();
        for (IFluidTank tank : fluidTanks) list.add(wrapIntoEntry(tank));
        this.fluidTanks = list.toArray(new MultiFluidTankEntry[0]);
        this.allowSameFluidFill = allowSameFluidFill;
    }

    public FluidTankList(boolean allowSameFluidFill, @Nonnull List<? extends IFluidTank> fluidTanks) {
        ArrayList<MultiFluidTankEntry> list = new ArrayList<>();
        for (IFluidTank tank : fluidTanks) list.add(wrapIntoEntry(tank));
        this.fluidTanks = list.toArray(new MultiFluidTankEntry[0]);
        this.allowSameFluidFill = allowSameFluidFill;
    }

    public FluidTankList(boolean allowSameFluidFill, @Nonnull IMultipleTankHandler parent, IFluidTank... additionalTanks) {
        ArrayList<MultiFluidTankEntry> list = new ArrayList<>(parent.getFluidTanks());
        for (IFluidTank tank : additionalTanks) list.add(wrapIntoEntry(tank));
        this.fluidTanks = list.toArray(new MultiFluidTankEntry[0]);
        this.allowSameFluidFill = allowSameFluidFill;
    }

    private MultiFluidTankEntry wrapIntoEntry(IFluidTank tank) {
        return tank instanceof MultiFluidTankEntry entry ? entry : new MultiFluidTankEntry(this, tank);
    }

    @Nonnull
    @Override
    public List<MultiFluidTankEntry> getFluidTanks() {
        return Collections.unmodifiableList(Arrays.asList(fluidTanks));
    }

    @Override
    public int getTanks() {
        return fluidTanks.length;
    }

    @Nonnull
    @Override
    public MultiFluidTankEntry getTankAt(int index) {
        return fluidTanks[index];
    }

    @Nonnull
    @Override
    public IFluidTankProperties[] getTankProperties() {
        ArrayList<IFluidTankProperties> propertiesList = new ArrayList<>();
        for (MultiFluidTankEntry fluidTank : fluidTanks) {
            Collections.addAll(propertiesList, fluidTank.getTankProperties());
        }
        return propertiesList.toArray(new IFluidTankProperties[0]);
    }

    @Override
    public boolean allowSameFluidFill() {
        return allowSameFluidFill;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) {
            return 0;
        }
        int totalInserted = 0;
        boolean inputFluidCopied = false;
        // flag value indicating whether the fluid was stored in 'distinct' slot at least once
        boolean distinctSlotVisited = false;

        MultiFluidTankEntry[] fluidTanks = this.fluidTanks.clone();
        Arrays.sort(fluidTanks, IMultipleTankHandler.ENTRY_COMPARATOR);

        // search for tanks with same fluid type first
        for (MultiFluidTankEntry tank : fluidTanks) {
            // if the fluid to insert matches the tank, insert the fluid
            if (resource.isFluidEqual(tank.getFluid())) {
                int inserted = tank.fill(resource, doFill);
                if (inserted > 0) {
                    totalInserted += inserted;
                    if (resource.amount - inserted <= 0) {
                        return totalInserted;
                    }
                    if (!inputFluidCopied) {
                        inputFluidCopied = true;
                        resource = resource.copy();
                    }
                    resource.amount -= inserted;
                }
                // regardless of whether the insertion succeeded, presence of identical fluid in
                // a slot prevents distinct fill to other slots
                if (!tank.allowSameFluidFill()) {
                    distinctSlotVisited = true;
                }
            }
        }
        // if we still have fluid to insert, loop through empty tanks until we find one that can accept the fluid
        for (MultiFluidTankEntry tank : fluidTanks) {
            // if the tank uses distinct fluid fill (allowSameFluidFill disabled) and another distinct tank had
            // received the fluid, skip this tank
            boolean usesDistinctFluidFill = tank.allowSameFluidFill();
            if ((usesDistinctFluidFill || !distinctSlotVisited) && tank.getFluidAmount() == 0) {
                int inserted = tank.fill(resource, doFill);
                if (inserted > 0) {
                    totalInserted += inserted;
                    if (resource.amount - inserted <= 0) {
                        return totalInserted;
                    }
                    if (!inputFluidCopied) {
                        inputFluidCopied = true;
                        resource = resource.copy();
                    }
                    resource.amount -= inserted;
                    if (!usesDistinctFluidFill) {
                        distinctSlotVisited = true;
                    }
                }
            }
        }
        // return the amount of fluid that was inserted
        return totalInserted;
    }

    @Nullable
    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) {
            return null;
        }
        int amountLeft = resource.amount;
        FluidStack totalDrained = null;
        for (IFluidTank handler : fluidTanks) {
            if (!resource.isFluidEqual(handler.getFluid())) {
                continue;
            }
            FluidStack drain = handler.drain(amountLeft, doDrain);
            if (drain != null) {
                if (totalDrained == null) {
                    totalDrained = drain;
                } else {
                    totalDrained.amount += drain.amount;
                }
                amountLeft -= drain.amount;
                if (amountLeft <= 0) {
                    return totalDrained;
                }
            }
        }
        return totalDrained;
    }

    @Nullable
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) {
            return null;
        }
        FluidStack totalDrained = null;
        for (IFluidTank handler : fluidTanks) {
            if (totalDrained == null) {
                totalDrained = handler.drain(maxDrain, doDrain);
                if (totalDrained != null) {
                    maxDrain -= totalDrained.amount;
                }
            } else {
                if (!totalDrained.isFluidEqual(handler.getFluid())) {
                    continue;
                }
                FluidStack drain = handler.drain(maxDrain, doDrain);
                if (drain != null) {
                    totalDrained.amount += drain.amount;
                    maxDrain -= drain.amount;
                }
            }
            if (maxDrain <= 0) {
                return totalDrained;
            }
        }
        return totalDrained;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound fluidInventory = new NBTTagCompound();
        NBTTagList tanks = new NBTTagList();
        for (int i = 0; i < this.getTanks(); i++) {
            tanks.appendTag(this.fluidTanks[i].trySerialize());
        }
        fluidInventory.setTag("Tanks", tanks);
        return fluidInventory;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        NBTTagList tanks = nbt.getTagList("Tanks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < Math.min(fluidTanks.length, tanks.tagCount()); i++) {
            this.fluidTanks[i].tryDeserialize(tanks.getCompoundTagAt(i));
        }
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean lineBreak) {
        StringBuilder stb = new StringBuilder("FluidTankList[").append(this.fluidTanks.length).append(";");
        for (int i = 0; i < this.fluidTanks.length; i++) {
            if (i != 0) stb.append(',');
            stb.append(lineBreak ? "\n  " : " ");

            FluidStack fluid = this.fluidTanks[i].getFluid();
            if (fluid == null || fluid.amount == 0) {
                stb.append("None 0 / ").append(this.fluidTanks[i].getCapacity());
            } else {
                stb.append(fluid.getFluid().getName()).append(' ').append(fluid.amount)
                        .append(" / ").append(this.fluidTanks[i].getCapacity());
            }
        }
        if (lineBreak) stb.append('\n');
        return stb.append(']').toString();
    }
}
