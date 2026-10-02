package com.safeguardian.app.domain.ai.ml

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Phase10PrivacySecurityTest {

    private val projectRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun testAndroidManifestContainsNoInternetPermission() {
        val manifestFile = File(projectRoot, "src/main/AndroidManifest.xml")
        val altManifest = File(projectRoot, "app/src/main/AndroidManifest.xml")
        val file = if (manifestFile.exists()) manifestFile else altManifest

        assertTrue("AndroidManifest.xml must exist", file.exists())
        val manifestContent = file.readText()

        assertFalse(
            "CRITICAL PRIVACY VIOLATION: android.permission.INTERNET must NOT be requested!",
            manifestContent.contains("android.permission.INTERNET", ignoreCase = true)
        )
        assertFalse(
            "CRITICAL PRIVACY VIOLATION: ACCESS_NETWORK_STATE must not be requested",
            manifestContent.contains("android.permission.ACCESS_NETWORK_STATE", ignoreCase = true)
        )
    }

    @Test
    fun testNoRuntimeNetworkDependenciesInBuildGradle() {
        val gradleFile = File(projectRoot, "build.gradle.kts")
        val appGradleFile = File(projectRoot, "app/build.gradle.kts")
        val file = if (appGradleFile.exists()) appGradleFile else gradleFile

        assertTrue("build.gradle.kts must exist", file.exists())
        val gradleContent = file.readText()

        val forbiddenLibraries = listOf(
            "okhttp",
            "retrofit",
            "volley",
            "firebase-analytics",
            "crashlytics",
            "com.google.android.gms:play-services-ads",
            "mixpanel",
            "segment",
            "amplitude"
        )

        for (lib in forbiddenLibraries) {
            assertFalse(
                "CRITICAL SECURITY VIOLATION: Forbidden telemetry/network library '$lib' found in build.gradle.kts",
                gradleContent.contains(lib, ignoreCase = true)
            )
        }
    }

    @Test
    fun testModelIntegrityVerificationSuccessAndTamperDetection() {
        // Valid hashes
        val motionSha = "b9e8d4bf75810d37173d3381683ec8d0d77911fbbc53f9addd43bd1f44df7e82"
        val audioSha = "098b4421fe200f94cd3b0816e86a01a38ec0c94aa4fea08af184fb7410eb96c4"
        val visionSha = "926042edde1ddf6322e2eae65ccae6e24a04e5818976e98621e59a2e1d834b23"

        assertEquals(motionSha, ModelIntegrityVerifier.TRUSTED_MODELS["motion_model.tflite"]?.sha256Hex)
        assertEquals(audioSha, ModelIntegrityVerifier.TRUSTED_MODELS["audio_model.tflite"]?.sha256Hex)
        assertEquals(visionSha, ModelIntegrityVerifier.TRUSTED_MODELS["vision_model.tflite"]?.sha256Hex)

        // Test tamper detection with corrupted byte array
        val tamperedBytes = "corrupted_payload_data_simulation".toByteArray()
        val result = ModelIntegrityVerifier.verifyChecksum("motion_model.tflite", tamperedBytes)

        assertTrue("Tampered artifact must be identified as Corrupted", result is ModelIntegrityResult.Corrupted)
        val corrupted = result as ModelIntegrityResult.Corrupted
        assertNotEquals(corrupted.expectedHash, corrupted.actualHash)

        // Test dimension mismatch verification
        val wrongInputShape = intArrayOf(1, 32) // expected [1, 16]
        val rightOutputShape = intArrayOf(1, 4)
        val dimResult = ModelIntegrityVerifier.verifyTensorDimensions("motion_model.tflite", wrongInputShape, rightOutputShape)
        assertTrue("Mismatched tensor shape must be caught", dimResult is ModelIntegrityResult.DimensionMismatch)
    }

    @Test
    fun testEmergencyMessageOmitsPrecisePIIOrRawTelemetry() {
        val event = com.safeguardian.app.domain.model.EmergencyEvent(
            id = "priv-event-1",
            eventType = "Fall Detected",
            confidence = 0.92f,
            sensorEvidence = com.safeguardian.app.domain.model.SensorEvidence(
                eventChainDescription = "Motion fall + Audio impact"
            )
        )
        val location = com.safeguardian.app.domain.response.GeoLocation(37.422, -122.084, 5.0f)

        val message = com.safeguardian.app.domain.response.EmergencyMessageGenerator.generateMessage(event, location)

        // Verify message contains only vital info
        assertTrue("Must state SafeGuardian Alert", message.contains("SafeGuardian Alert"))
        assertTrue("Must state Event", message.contains("Fall Detected"))
        assertTrue("Must include map link", message.contains("https://maps.google.com/?q=37.422,-122.084"))

        // Must NEVER contain raw audio, sensor arrays, or device serial numbers
        assertFalse("Must not expose raw buffer data", message.contains("ShortArray"))
        assertFalse("Must not expose raw accelerometer data", message.contains("peakAccelMagnitude"))
        assertFalse("Must not expose device identifiers", message.contains("IMEI") || message.contains("ANDROID_ID"))
    }
}
