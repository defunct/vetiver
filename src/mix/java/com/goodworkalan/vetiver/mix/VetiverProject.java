package com.goodworkalan.vetiver.mix;

import com.goodworkalan.mix.ProjectModule;
import com.goodworkalan.mix.builder.Builder;
import com.goodworkalan.mix.cookbook.JavaProject;

/**
 * Builds the project definition for Ventiver.
 *
 * @author Alan Gutierrez
 */
public class VetiverProject implements ProjectModule {
    /**
     * Build the project definition for Ventiver.
     *
     * @param builder
     *          The project builder.
     */
    public void build(Builder builder) {
        builder
            .cookbook(JavaProject.class)
                .produces("com.github.bigeasy.vetiver/vetiver/0.1")
                .depends()
                    .development("org.testng/testng-jdk15/5.10")
                    .end()
                .end()
            .end();
    }
}
