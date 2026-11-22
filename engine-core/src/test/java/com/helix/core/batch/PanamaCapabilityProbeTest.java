package com.helix.core.batch;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Panama Vector API Capability Probe Tests")
class PanamaCapabilityProbeTest {

    @Test
    @DisplayName("Verify preferred double vector species is resolvable and has positive lane count")
    void testPreferredDoubleSpeciesResolvable() {
        VectorSpecies<Double> species = DoubleVector.SPECIES_PREFERRED;
        assertNotNull(species, "DoubleVector.SPECIES_PREFERRED must not be null");
        assertTrue(species.length() >= 1, "Preferred vector species lane count must be at least 1");
    }

    @Test
    @DisplayName("Verify basic vector addition operates without hardware trap")
    void testBasicVectorOperation() {
        VectorSpecies<Double> species = DoubleVector.SPECIES_PREFERRED;
        double[] a = new double[species.length()];
        double[] b = new double[species.length()];
        for (int i = 0; i < species.length(); i++) {
            a[i] = i * 1.5;
            b[i] = 2.0;
        }

        DoubleVector va = DoubleVector.fromArray(species, a, 0);
        DoubleVector vb = DoubleVector.fromArray(species, b, 0);
        DoubleVector vc = va.add(vb);

        double[] result = new double[species.length()];
        vc.intoArray(result, 0);

        for (int i = 0; i < species.length(); i++) {
            assertEquals(a[i] + b[i], result[i], 1e-9);
        }
    }
}
