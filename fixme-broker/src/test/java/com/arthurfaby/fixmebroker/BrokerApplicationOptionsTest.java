package com.arthurfaby.fixmebroker;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerApplicationOptionsTest {

    private static BrokerApplication parse(String... args) {
        BrokerApplication app = new BrokerApplication();
        new CommandLine(app).parseArgs(args);
        return app;
    }

    @Test
    void defaultsAreLocalhostAndPort5000() {
        BrokerApplication app = parse();

        assertThat(app.host()).isEqualTo("localhost");
        assertThat(app.port()).isEqualTo(5000);
    }

    @Test
    void hostAndPortCanBeOverridden() {
        BrokerApplication app = parse("--host", "example.com", "--port", "6001");

        assertThat(app.host()).isEqualTo("example.com");
        assertThat(app.port()).isEqualTo(6001);
    }

    @Test
    void portHasAShortAlias() {
        assertThat(parse("-p", "7002").port()).isEqualTo(7002);
    }
}
