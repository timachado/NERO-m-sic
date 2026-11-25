package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroAccountLibraryStoreTest{
    @Test fun accountsUseDistinctFileNames(){
        val a="00000000-0000-0000-0000-000000000001"
        val b="00000000-0000-0000-0000-000000000002"
        assertNotEquals(NeroAccountLibraryStore.fileName(a),NeroAccountLibraryStore.fileName(b))
    }
    @Test fun rejectsInvalidAccountPath(){
        try{
            NeroAccountLibraryStore.fileName("../nero-online-favorites")
            fail("Expected rejection")
        }catch(_:IllegalArgumentException){}
    }
}
