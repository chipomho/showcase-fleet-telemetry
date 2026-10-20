package au.chipomho.showcase.fleet;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * @param allowedOrigins browser origins allowed to call the API and open the live feed
 */
@ConfigurationProperties("fleet")
public record FleetProperties(@DefaultValue("https://fleet-dashboard.showcases.chipomho.local") List<String> allowedOrigins,
                              @DefaultValue Simulator simulator, @DefaultValue Alerts alerts) {

    /**
     * @param trucks    the size of the fleet, whether or not the simulator drives it
     * @param tick      how often the simulator produces a reading for every truck
     * @param timeScale simulated seconds per real second, to make the haul cycle quicker to watch
     * @param seed      fixes the simulator's random choices; random when not set
     */
    public record Simulator(@DefaultValue("true") boolean enabled, @DefaultValue("6") int trucks,
                            @DefaultValue("1s") Duration tick, @DefaultValue("4") double timeScale, Long seed) {
    }

    /**
     * @param window          how far back each truck's readings are kept for the alert rules
     * @param minimumReadings how many readings a window needs before the rules look at it
     */
    public record Alerts(@DefaultValue("30s") Duration window, @DefaultValue("3") int minimumReadings) {
    }
}
