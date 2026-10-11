public class Plane {
    private String model;
    private double efficiency; // Efficiency in terms of fuel consumption per distance
    private double maxSpeed;
    private double currentSpeed;
    private double fuelCapacity;
    private double direction;
    private double x;
    private double y;

    public Plane(String model, double efficiency, double maxSpeed, double fuelCapacity) {
        this.model = model;
        this.maxSpeed = maxSpeed;
        this.fuelCapacity = fuelCapacity;
        this.efficiency = efficiency;
    }

    public void accelerate(double speed) {
        if (speed <= maxSpeed) {
            this.currentSpeed = speed;
            fuelCapacity -= speed / efficiency; // Decrease fuel based on speed and efficiency
        } else {
            System.out.println("Aircraft is at maximum speed.");
        }
    }

    public void travel(double distance) {
        if (currentSpeed > 0) {
            double time = distance / currentSpeed; // Time = Distance / Speed
            fuelCapacity -= distance / efficiency; // Decrease fuel based on distance and efficiency
            x += distance * Math.cos(Math.toRadians(direction)); // Update x position based on direction
            y += distance * Math.sin(Math.toRadians(direction)); // Update y position based on direction
            System.out.println("Traveled " + distance + " km in " + time + " hours.");
        } else {
            System.out.println("Plane is not moving.");
        }
    }

    public void changeDirection(double newDirection) {
        this.direction = newDirection % 360; // Ensure direction is within 0-359 degrees
    }

    public String getModel() {
        return model;
    }

    public double getEfficiency() {
        return efficiency;
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
