// The precipitation has a range of 0.00 to 1.00, and so does intensity.

public class Weather {
    private double chanceOFPrecipitation;
    private double intensity;
    private double temperature;

    public Weather(double chanceOFPrecipitation, double intensity, double temperature) {
        this.chanceOFPrecipitation = chanceOFPrecipitation;
        this.intensity = intensity;
        this.temperature = temperature;
    }

    public double getCOF() {
        return chanceOFPrecipitation;
    }

    public double getIntensity() {
        return intensity;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getDanger() {
        return chanceOFPrecipitation * (Math.max(intensity * (Math.exp(-Math.pow((temperature + 5), 2) / 50)), (Math.pow(intensity, 2) * (1/(1 + Math.exp(-0.1 * (temperature - 15)))))));
    }
}