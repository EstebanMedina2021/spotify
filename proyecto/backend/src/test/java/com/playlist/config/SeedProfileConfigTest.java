package com.playlist.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * The demo user has a public password, so its seed must load in the dev profile only
 * (DECISIONES-FINALES section 14). Reads the real configuration files, not a test copy.
 */
class SeedProfileConfigTest {

    private static final String SEED = "classpath:db/migration/V2__seed_dev.sql";
    private static final String DATA_LOCATIONS = "spring.sql.init.data-locations";

    private Object property(String file, String key) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
        return sources.stream().map(source -> source.getProperty(key)).filter(value -> value != null).findFirst().orElse(null);
    }

    @Test
    void baseConfiguration_doesNotLoadTheDemoUserSeed() throws IOException {
        assertThat(property("application.yml", DATA_LOCATIONS)).isNull();
    }

    @Test
    void devProfile_loadsTheDemoUserSeed() throws IOException {
        assertThat(property("application-dev.yml", DATA_LOCATIONS)).isEqualTo(SEED);
    }

    @Test
    void schemaStillLoadsInEveryProfile() throws IOException {
        assertThat(property("application.yml", "spring.sql.init.schema-locations"))
                .isEqualTo("classpath:db/migration/V1__schema.sql");
    }
}
