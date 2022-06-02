/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package assignment;

import java.util.LinkedList;

/**
 *
 * @author Ryan Ng
 */
class ATC implements Runnable {

    FuelTruck fuelTruck;
    Runway runway;
    Gate gate;
    Airport airport;
    PlaneStates state;
    Statistics stat;
    private boolean checkRunwayDepart = true;
    private final String threadName = "ATC: ";

    //linked list object to store list of planes waiting to land
    LinkedList<Plane> planeWaitingList;

    ATC(FuelTruck fuelTruck, Runway runway, Gate gate, Airport airport, Statistics stat) {
        this.fuelTruck = fuelTruck;
        this.runway = runway;
        this.gate = gate;
        this.airport = airport;
        this.stat = stat;
        planeWaitingList = new LinkedList<Plane>();
    }

    public void run() {
        System.out.println("ATC is online, ready to start simulation.");
    }

    //code to check availability of runway for ARRIVAL
    public void checkRunwayForArrival(Plane plane) {
        System.out.println(threadName + "Received landing request from Plane " + plane.id + ", checking available runways...");
        //Plane only given runway if and only if runway permits available and airport capacity not full
        if (runway.runwaySem.availablePermits() != 0
                && airport.airportCapacity.get() != airport.MAX_CAPACITY) {
            System.out.println(threadName + "Runway is available for arrival!");
            try {
                runway.runwaySem.acquire();
                airport.airportCapacity.incrementAndGet();
                System.out.println(threadName + "Plane " + plane.id + " has taken runway for arrival.");
                plane.state = PlaneStates.ONRUNWAYARRIVAL;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } //otherwise, add plane to a waiting queue
        else {
            if (planeWaitingList.indexOf(plane) == -1) {
                if(airport.airportCapacity.get() != airport.MAX_CAPACITY)
                    System.out.println(threadName + "The runway is occupied, please wait in queue.");
                else if (airport.airportCapacity.get() == airport.MAX_CAPACITY)
                    System.out.println(threadName + "The airport is occupied, please wait in queue.");
            }
            addPlaneToQueue(plane);
            synchronized (planeWaitingList) {
                try {
                    planeWaitingList.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //function to check availability of runway for DEPARTURE
    public void checkRunwayForDeparture(Plane plane) {
        if (checkRunwayDepart) {
            System.out.println(threadName + "Received departure request from Plane " + plane.id + ", checking available runways...");
            checkRunwayDepart = false;
        }
        try {
            //sleep to give planes waiting in queue priority for landing
            Plane.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (runway.runwaySem.availablePermits() != 0) {
            System.out.println(threadName + "Runway is available for departure!");
            try {
                runway.runwaySem.acquire();
                System.out.println(threadName + "Plane " + plane.id + " has taken runway for departure.");
                plane.state = PlaneStates.ONRUNWAYDEPARTURE;
                checkRunwayDepart = true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //function to check availability of gate
    public void checkGate(Plane plane) {
        if (gate.gateSem.availablePermits() != 0) {
            System.out.println(threadName + "Gates are available.");
            try {
                gate.gateSem.acquire();
                System.out.println(threadName + "Plane " + plane.id + " has taken a gate, runway is now available again.");
                runway.runwaySem.release();
                landPlaneOnRunway();
                plane.state = PlaneStates.ATGATE;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //function to check availability of fuel truck
    public void checkFuelTruck(Plane plane) {
        if (fuelTruck.fuelTruckSem.availablePermits() != 0 && !plane.refueled) {
            System.out.println(threadName + "Fuel Truck is available!");
            try {
                fuelTruck.fuelTruckSem.acquire();
                System.out.println(threadName + "Plane " + plane.id + " has taken the Fuel Truck!");
                System.out.println(threadName + "Plane " + plane.id + " is currently refuelling...");
                //sleep to simulate refuelling taking some time
                plane.sleep(1000);
                fuelTruck.fuelTruckSem.release();
                plane.refueled = true;
                System.out.println("Refueled Plane " + plane.id + ".");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //function to append plane object to a linked list queue
    public void addPlaneToQueue(Plane plane) {
        synchronized (planeWaitingList) {
            ((LinkedList<Plane>) planeWaitingList).offer(plane);
            
            //set start time on timer
            stat.setStartTime(System.nanoTime());
            
            System.out.println(threadName + "Plane " + plane.id + " has been entered into waiting queue.");
            System.out.println("Planes in queue now: " + planeWaitingList.size());
        }
    }

    //function that wakes thread to try and access runway
    public void landPlaneOnRunway() {
        synchronized (planeWaitingList) {
            if (airport.airportCapacity.get() != airport.MAX_CAPACITY && !planeWaitingList.isEmpty()) {
                System.out.println(threadName + "Found a plane in queue.");
                System.out.println(threadName + "Listening for landing requests...");
                planeWaitingList.pollFirst();
                
                //set end time on timer
                stat.setEndTime(System.nanoTime());
                stat.calculateDuration();
                
                planeWaitingList.notify();
            } else if (airport.airportCapacity.get() == airport.MAX_CAPACITY && !planeWaitingList.isEmpty()) {
                System.out.println(threadName + "Both gates are now occupied, airport full.");
                System.out.println(threadName + "Planes will have to continue waiting in airspace...");
            } else if (airport.airportCapacity.get() == 0 && planeWaitingList.isEmpty()) {
                System.out.println(threadName + "Queue empty. Simulation has ended.");
            }
        }
    }
}
