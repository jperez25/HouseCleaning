package com.occ.housecleaning.bdd.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** Entry point: runs every Cucumber feature under classpath:features/cucumber. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/cucumber")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.occ.housecleaning.bdd.cucumber.steps")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-report.html, io.qameta.allure.cucumber8jvm.AllureCucumber8Jvm")
public class CucumberTest {
}
