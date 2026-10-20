package au.chipomho.showcase.fleet.domain;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The trucks this service knows about, in truck id order.
 */
public record Fleet(List<Truck> trucks) {

    public Fleet {
        trucks = List.copyOf(trucks);
    }

    /**
     * Haul trucks HT-101, HT-102 and so on.
     */
    public static Fleet ofHaulTrucks(int count) {
        if (count < 1 || count > 899) {
            throw new IllegalArgumentException("A fleet has from 1 to 899 trucks, not " + count);
        }
        return new Fleet(IntStream.rangeClosed(101, 100 + count)
                .mapToObj(number -> new Truck("HT-" + number, "Cat 793F"))
                .toList());
    }

    public Optional<Truck> find(String truckId) {
        return trucks.stream().filter(truck -> truck.id().equals(truckId)).findFirst();
    }
}
