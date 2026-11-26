package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroTogetherTest{
 @Test fun validatesInvite(){
   assertEquals("0123456789abcdef0123456789abcdef",togetherCode(" 0123456789ABCDEF0123456789ABCDEF "))
   assertNull(togetherCode("123"));assertNull(togetherCode("z".repeat(32)))
 }
}
