package priv.jv.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@ConfigurationProperties(prefix = "netty")
@Configuration
@Data
public class RoutesConfig {

    private List<String> routeHosts;
}
