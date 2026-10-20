package com.workbench.workshop.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Runner BDD (Cucumber sobre JUnit Platform) — requisito do PDF: ao menos um fluxo
 * completo testado com BDD. Cobre o ciclo de vida da OS (ver
 * {@code src/test/resources/features/ordem_servico.feature}).
 *
 * <p>O sufixo {@code IntegrationTest} faz o Maven Failsafe executá-lo na fase
 * {@code verify} (ele sobe o contexto Spring + PostgreSQL via Testcontainers).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.workbench.workshop.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class OrderBddIntegrationTest {
}
