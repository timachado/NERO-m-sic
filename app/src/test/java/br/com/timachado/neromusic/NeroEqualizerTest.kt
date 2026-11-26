package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroEqualizerTest {
    @Test fun validSessionIsEnabledInFullFeaturesBuilds() {
        assertEquals(BuildConfig.FULL_FEATURES, neroEqualizerCanAttach(123))
    }
    @Test fun invalidSessionNeverUsesGlobalEffect() {
        assertFalse(neroEqualizerCanAttach(0))
        assertFalse(neroEqualizerCanAttach(-1))
    }
}
