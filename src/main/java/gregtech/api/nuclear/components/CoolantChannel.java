package gregtech.api.nuclear.components;

import gregtech.api.nuclear.ReactorComponent;
import gregtech.api.unification.material.Material;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class CoolantChannel extends ReactorComponent {
    private final Material coolant;
    private int weight;
    private final List<Pair<FuelRod, FuelRod>> fuelRodPairs = new ObjectArrayList<>();


    public CoolantChannel(int maxTemperature, double thermalConductivity, Material coolant) {
        super(true, maxTemperature, coolant.getCoolantProperties().getModeratorFactor(), thermalConductivity);
        this.coolant = coolant;
        this.weight = 0;
    }

    public void addFuelRodPairToMap(FuelRod fuelRodA, FuelRod fuelRodB) {
        fuelRodPairs.add(Pair.of(fuelRodA, fuelRodB));
    }

    List<Pair<FuelRod, FuelRod>> getFuelRodPairMap() {
        return fuelRodPairs;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public Material getCoolant() {
        return coolant;
    }

    public void computeWeightFromFuelRodMap() {
        this.weight = fuelRodPairs.size() - 1;
    }

    @Override
    public CoolantChannel copy() {
        return new CoolantChannel(this.getMaxTemperature(), this.getThermalConductivity(), coolant);
    }
}
