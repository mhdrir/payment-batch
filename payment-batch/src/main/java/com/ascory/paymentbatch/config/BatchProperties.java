package com.ascory.paymentbatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Externalised batch configuration so the application can run inside Docker
 * (/app/input, /app/output) as well as in tests (temporary directories).
 *
 * @param inputDirectory    directory that is scanned for *.csv input files
 * @param outputDirectory   directory the result files are written to
 * @param allowedCurrencies currencies accepted by the currency validator
 */
@ConfigurationProperties(prefix = "app.batch")
public record BatchProperties(

        @DefaultValue("/app/input") Path inputDirectory,

        @DefaultValue("/app/output") Path outputDirectory,

        @DefaultValue({"EUR", "USD"}) Set<String> allowedCurrencies) {

    public BatchProperties {
        // normalise once at startup so the validator can do a cheap, case-insensitive lookup
        Set<String> normalised = new LinkedHashSet<>();
        allowedCurrencies.forEach(currency -> normalised.add(currency.toUpperCase(Locale.ROOT)));
        allowedCurrencies = Set.copyOf(normalised);
    }
}

