package com.arthurfaby.fixmemarket;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketApplicationOptionsTest {

    private static MarketApplication parse(String... args) {
        MarketApplication app = new MarketApplication();
        new CommandLine(app).parseArgs(args);
        return app;
    }

    @Test
    void inlineInstrumentsAreParsedIntoTheBook() {
        MarketApplication app = parse("--instruments=AAPL:1000,GOOG:500");

        assertThat(app.instruments()).containsOnly(
                Assertions.entry("AAPL", 1000),
                Assertions.entry("GOOG", 500));
    }

    @Test
    void whitespaceAroundSymbolsAndQuantitiesIsTrimmed() {
        MarketApplication app = parse("--instruments= AAPL : 1000 , GOOG:500 ");

        assertThat(app.instruments()).containsEntry("AAPL", 1000).containsEntry("GOOG", 500);
    }

    @Test
    void aPairWithoutAQuantityIsRejected() {
        assertThatThrownBy(() -> parse("--instruments=AAPL"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("SYMBOL:QUANTITY");
    }

    @Test
    void aNonNumericQuantityIsRejected() {
        assertThatThrownBy(() -> parse("--instruments=AAPL:abc"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("must be a valid integer");
    }

    @Test
    void instrumentsAreLoadedFromAPropertiesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("market.properties");
        Files.writeString(file, "AAPL=1000\nGOOG=500\n", StandardCharsets.UTF_8);

        MarketApplication app = parse("--instruments-file=" + file);

        assertThat(app.instruments()).containsEntry("AAPL", 1000).containsEntry("GOOG", 500);
    }

    @Test
    void aMissingPropertiesFileIsRejected(@TempDir Path dir) {
        Path missing = dir.resolve("nope.properties");

        assertThatThrownBy(() -> parse("--instruments-file=" + missing))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("does not exist");
    }

    @Test
    void aNonNumericQuantityInTheFileIsRejected(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("market.properties");
        Files.writeString(file, "AAPL=lots\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parse("--instruments-file=" + file))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bothSourcesCanBeCombined(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("market.properties");
        Files.writeString(file, "TSLA=200\n", StandardCharsets.UTF_8);

        MarketApplication app = parse("--instruments=AAPL:1000", "--instruments-file=" + file);

        assertThat(app.instruments()).containsEntry("AAPL", 1000).containsEntry("TSLA", 200);
    }

    @Test
    void theBookIsEmptyWhenNoInstrumentIsDeclared() {
        assertThat(parse().instruments()).isEmpty();
    }
}
