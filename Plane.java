public class Plane {
    private String model;
    private double maxSpeed;
    private double currentSpeed;
    private double fuelCapacity;
    private double direction;
    private double x;
    private double y;

    public Plane(String model, double maxSpeed, double fuelCapacity) {
        this.model = model;
        this.maxSpeed = maxSpeed;
        this.fuelCapacity = fuelCapacity;
    }

    public String getModel() {
        return model;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getFuelCapacity() {
        return fuelCapacity;
    }

    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public double getDirection() {
        return direction;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double setY(double y) {
        this.y = y;
        return y;
    }

    public double setX(double x) {
        this.x = x;
        return x;
    }
}
