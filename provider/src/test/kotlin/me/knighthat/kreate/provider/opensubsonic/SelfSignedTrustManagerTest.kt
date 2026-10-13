/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate.provider.opensubsonic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class SelfSignedTrustManagerTest {

    /** Trusts nothing, like the system with a private CA it doesn't know. */
    private class NoSystemTrust : X509TrustManager {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
            throw CertificateException("Trust anchor not found")

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
    }

    private val rootA = certificate("root_a")
    private val rootB = certificate("root_b")
    private val leafA = certificate("leaf_a")
    private val leafB = certificate("leaf_b")
    private val selfSignedLeaf = certificate("self_signed_leaf")

    private var pin: CertificatePin? = null
    private val manager = SelfSignedTrustManager(NoSystemTrust(), { pin }, { pin = it })

    private fun certificate(name: String): X509Certificate =
        javaClass.getResourceAsStream("/certs/$name.pem")!!.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }

    private fun check(vararg chain: X509Certificate, host: String? = HOST) =
        manager.check(arrayOf(*chain), "ECDHE_ECDSA", host) {
            NoSystemTrust().checkServerTrusted(arrayOf(*chain), "ECDHE_ECDSA")
        }

    @Test
    fun whatTheSystemTrustsNeedsNoPin() {
        manager.check(arrayOf(leafA, rootA), "ECDHE_ECDSA", HOST) { }

        assertNull(pin)
    }

    @Test
    fun theFirstSelfSignedRootIsRemembered() {
        check(leafA, rootA)

        assertEquals(CertificatePin(HOST, rootA.sha256()), pin)
    }

    @Test
    fun theRememberedRootIsTrustedAgain() {
        pin = CertificatePin(HOST, rootA.sha256())

        check(leafA, rootA)

        assertEquals(CertificatePin(HOST, rootA.sha256()), pin)
    }

    @Test
    fun aDifferentRootIsRefusedAsAChange() {
        pin = CertificatePin(HOST, rootA.sha256())

        val error = assertThrows(CertificateChangedException::class.java) { check(leafB, rootB) }

        assertEquals(HOST, error.host)
        assertEquals(rootB.sha256(), error.fingerprint)
        assertEquals(rootA.sha256(), error.pinnedFingerprint)
        assertEquals(CertificatePin(HOST, rootA.sha256()), pin)
    }

    @Test
    fun aSelfSignedLeafIsRememberedItself() {
        check(selfSignedLeaf)

        assertEquals(CertificatePin(HOST, selfSignedLeaf.sha256()), pin)
    }

    @Test
    fun aChainWithoutASelfSignedCertificateIsRefused() {
        val error = assertThrows(CertificateException::class.java) { check(leafA) }

        assertEquals("Trust anchor not found", error.message)
        assertNull(pin)
    }

    @Test
    fun aLeafThatTheRootDidNotSignIsRefused() {
        assertThrows(CertificateException::class.java) { check(leafB, rootA) }
        assertNull(pin)
    }

    @Test
    fun withoutTheHostOnlyTheSystemDecides() {
        assertThrows(CertificateException::class.java) { check(leafA, rootA, host = null) }
        assertNull(pin)
    }

    @Test
    fun aPinOfAnotherHostDoesNotCount() {
        pin = CertificatePin("other.test", rootB.sha256())

        check(leafA, rootA)

        assertEquals(CertificatePin(HOST, rootA.sha256()), pin)
    }

    @Test
    fun fingerprintsArePairsOfUppercaseHex() {
        assertTrue(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}").matches(rootA.sha256()))
    }

    private companion object {
        const val HOST = "music.test"
    }
}
