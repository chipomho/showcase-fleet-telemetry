package au.chipomho.showcase.fleet;

import au.chipomho.showcase.fleet.domain.AlertMonitor;
import au.chipomho.showcase.fleet.domain.AlertRule;
import au.chipomho.showcase.fleet.domain.Fleet;
import au.chipomho.showcase.fleet.domain.Simulator;
import au.chipomho.showcase.fleet.domain.Site;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

import java.time.Clock;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * The domain is plain Java; this is where it meets Spring.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FleetProperties.class)
public class FleetConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Site site() {
        return Site.pilbaraNorthPit();
    }

    @Bean
    Fleet fleet(FleetProperties properties) {
        return Fleet.ofHaulTrucks(properties.simulator().trucks());
    }

    @Bean
    AlertMonitor alertMonitor(FleetProperties properties) {
        return new AlertMonitor(AlertRule.standard(), properties.alerts().window(), properties.alerts().minimumReadings());
    }

    @Bean
    Simulator simulator(Site site, Fleet fleet, FleetProperties properties) {
        var settings = properties.simulator();
        var random = Optional.ofNullable(settings.seed()).map(SplittableRandom::new).orElseGet(SplittableRandom::new);
        return new Simulator(site, fleet, random, settings.tick().toMillis() / 1000.0 * settings.timeScale());
    }

    @Bean
    SimpleAsyncTaskScheduler simulatorScheduler() {
        var scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setThreadNamePrefix("simulator-");
        scheduler.setVirtualThreads(true);
        return scheduler;
    }
}
