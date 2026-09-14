package de.simplegamble.gamble;

/**
 * Repräsentiert einen möglichen Multiplikator mit zwei Gewichten:
 * lowWeight gilt bei Einsatz = bet.min, highWeight bei Einsatz = bet.max.
 * Für Einsätze dazwischen wird linear interpoliert (siehe SimpleGamblePlugin#getEffectiveWeight).
 */
public class MultiplierEntry {

    private final double value;
    private final double lowWeight;
    private final double highWeight;

    public MultiplierEntry(double value, double lowWeight, double highWeight) {
        this.value = value;
        this.lowWeight = lowWeight;
        this.highWeight = highWeight;
    }

    public double getValue() {
        return value;
    }

    public double getLowWeight() {
        return lowWeight;
    }

    public double getHighWeight() {
        return highWeight;
    }

    /**
     * Interpoliertes Gewicht für einen gegebenen Fortschritt zwischen Mindest- (r=0)
     * und Höchsteinsatz (r=1).
     */
    public double getWeightAt(double r) {
        double clamped = Math.max(0.0, Math.min(1.0, r));
        return lowWeight + (highWeight - lowWeight) * clamped;
    }
}
