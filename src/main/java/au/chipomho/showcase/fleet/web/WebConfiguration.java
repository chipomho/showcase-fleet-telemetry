package au.chipomho.showcase.fleet.web;

import au.chipomho.showcase.fleet.FleetProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfiguration implements WebMvcConfigurer {

    private final FleetProperties properties;

    WebConfiguration(FleetProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST");
    }
}
