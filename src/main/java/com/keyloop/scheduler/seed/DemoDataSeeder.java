package com.keyloop.scheduler.seed;

import com.keyloop.scheduler.persistence.entity.CustomerEntity;
import com.keyloop.scheduler.persistence.entity.DealershipEntity;
import com.keyloop.scheduler.persistence.entity.DealershipServiceTypeEntity;
import com.keyloop.scheduler.persistence.entity.ServiceBayEntity;
import com.keyloop.scheduler.persistence.entity.ServiceTypeEntity;
import com.keyloop.scheduler.persistence.entity.TechnicianEntity;
import com.keyloop.scheduler.persistence.entity.VehicleEntity;
import com.keyloop.scheduler.persistence.repo.CustomerRepository;
import com.keyloop.scheduler.persistence.repo.DealershipRepository;
import com.keyloop.scheduler.persistence.repo.DealershipServiceTypeRepository;
import com.keyloop.scheduler.persistence.repo.ServiceBayRepository;
import com.keyloop.scheduler.persistence.repo.ServiceTypeRepository;
import com.keyloop.scheduler.persistence.repo.TechnicianRepository;
import com.keyloop.scheduler.persistence.repo.VehicleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class DemoDataSeeder implements ApplicationRunner {

    private final DealershipRepository dealerships;
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final ServiceTypeRepository serviceTypes;
    private final DealershipServiceTypeRepository offerings;
    private final ServiceBayRepository bays;
    private final TechnicianRepository technicians;

    public DemoDataSeeder(
            DealershipRepository dealerships,
            CustomerRepository customers,
            VehicleRepository vehicles,
            ServiceTypeRepository serviceTypes,
            DealershipServiceTypeRepository offerings,
            ServiceBayRepository bays,
            TechnicianRepository technicians
    ) {
        this.dealerships = dealerships;
        this.customers = customers;
        this.vehicles = vehicles;
        this.serviceTypes = serviceTypes;
        this.offerings = offerings;
        this.bays = bays;
        this.technicians = technicians;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (dealerships.findById(SeedIds.DEALERSHIP).isPresent()) {
            return;
        }

        dealerships.save(new DealershipEntity(SeedIds.DEALERSHIP, "Keyloop Motors Oxford", "Europe/London"));

        customers.save(new CustomerEntity(SeedIds.CUSTOMER, "Alex Rivera", "alex.rivera@example.com"));
        customers.save(new CustomerEntity(SeedIds.OTHER_CUSTOMER, "Jordan Blake", "jordan.blake@example.com"));

        vehicles.save(new VehicleEntity(
                SeedIds.VEHICLE, SeedIds.CUSTOMER, "WBA3A5C50EF123456", "AB12CDE", "BMW", "3 Series"));
        vehicles.save(new VehicleEntity(
                SeedIds.OTHER_VEHICLE, SeedIds.OTHER_CUSTOMER, "WVWZZZ3CZWE123456", "XY99ZZZ", "VW", "Golf"));

        serviceTypes.save(new ServiceTypeEntity(SeedIds.SERVICE_MOT, "MOT", "MOT test", 60, "MOT"));
        serviceTypes.save(new ServiceTypeEntity(SeedIds.SERVICE_MAJOR, "MAJOR", "Major service", 180, "MECHANICAL"));

        offerings.save(new DealershipServiceTypeEntity(SeedIds.DEALERSHIP, SeedIds.SERVICE_MOT));
        offerings.save(new DealershipServiceTypeEntity(SeedIds.DEALERSHIP, SeedIds.SERVICE_MAJOR));

        bays.save(new ServiceBayEntity(SeedIds.BAY_1, SeedIds.DEALERSHIP, "Bay 1", Set.of("MOT", "MECHANICAL")));
        bays.save(new ServiceBayEntity(SeedIds.BAY_2, SeedIds.DEALERSHIP, "Bay 2", Set.of("MOT", "MECHANICAL")));

        technicians.save(new TechnicianEntity(SeedIds.TECH_PAT, SeedIds.DEALERSHIP, "Pat Okonkwo", Set.of("MOT", "MECHANICAL")));
        technicians.save(new TechnicianEntity(SeedIds.TECH_SAM, SeedIds.DEALERSHIP, "Sam Chen", Set.of("MOT")));
    }
}
