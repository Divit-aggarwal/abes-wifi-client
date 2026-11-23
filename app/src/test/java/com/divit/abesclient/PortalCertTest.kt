package com.divit.abesclient

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Certificates captured from the real ABES portal (192.168.1.254:8090) on 2026-11-23. */
class PortalCertTest {
    private fun load(name: String) = javaClass.classLoader!!.getResourceAsStream(name).use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }

    @Test fun acceptsOnlyThePortalKey() {
        assertTrue(PortalCert.isPortal(load("portal-leaf.pem")))
        assertFalse(PortalCert.isPortal(load("portal-ca.pem"))) // same chain, different key
        assertFalse(PortalCert.isPortal(null))
    }
}
