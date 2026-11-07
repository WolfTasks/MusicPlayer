package com.wolf.musicplayer.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which controllers may connect to [MusicService].
 *
 * The service has to be exported so Android Auto, Automotive OS and System UI can bind to it,
 * which means any installed app can attempt to connect. Without this check, every app could
 * browse the local music library (titles, artists, file paths) without holding
 * READ_MEDIA_AUDIO, or make the service play arbitrary URIs with this app's permissions.
 *
 * Allowed callers (modelled after UAMP's PackageValidator):
 *  - this app itself (in-app MediaController and the media notification controller)
 *  - the Android system process
 *  - trusted controllers (MEDIA_CONTENT_CONTROL holders such as System UI / Bluetooth, or
 *    notification listeners the user explicitly enabled)
 *  - apps signed with the platform key (e.g. Automotive OS car media host)
 *  - a fixed allow-list of Google media hosts (Android Auto, Automotive Assistant, ...),
 *    verified by package name AND signing certificate, so an app that merely reuses the
 *    package name on a device without Android Auto is still rejected.
 */
@Singleton
class MediaCallerValidator
    @Inject
    constructor(
        private val packages: PackageInspector,
    ) {
        @OptIn(UnstableApi::class)
        fun isAllowed(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): Boolean {
            val packageName = controller.packageName
            val uid = controller.uid
            val allowed =
                when {
                    isOwnApp(controller) -> true
                    session.isMediaNotificationController(controller) && packageName == packages.ownPackageName -> true
                    uid == Process.SYSTEM_UID -> true
                    controller.isTrusted -> true
                    !packages.uidOwnsPackage(uid, packageName) -> false
                    else -> isSignedByKnownHost(packageName)
                }
            if (!allowed) {
                Log.i(TAG, "Rejecting media controller $packageName (uid $uid)")
            }
            return allowed
        }

        fun isOwnApp(controller: MediaSession.ControllerInfo): Boolean =
            controller.packageName == packages.ownPackageName && controller.uid == packages.ownUid

        private fun isSignedByKnownHost(packageName: String): Boolean {
            val callerCerts = packages.signingCertificatesSha256(packageName)
            if (callerCerts.isEmpty()) return false
            if (callerCerts.any { it in packages.platformCertificatesSha256() }) return true
            val knownCerts = KNOWN_HOSTS[packageName] ?: return false
            return callerCerts.any { it in knownCerts }
        }

        companion object {
            private const val TAG = "MediaCallerValidator"

            /**
             * Release signing certificates (SHA-256, lowercase hex, no separators) of Google media hosts.
             * Source: UAMP allowed_media_browser_callers.xml (github.com/android/uamp).
             */
            val KNOWN_HOSTS: Map<String, Set<String>> =
                mapOf(
                    // Android Auto (prod release key + rotated prod release key)
                    "com.google.android.projection.gearhead" to
                        setOf(
                            "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
                            "1ca8dcc0bed3cbd872d2cb791200c0292ca9975768a82d676b8b424fb65b5295",
                        ),
                    // Android Auto Desktop Head Unit / simulator
                    "com.google.android.autosimulator" to
                        setOf("1975b2f17177bc89a5dff31f9e64a6cae281a53dc1d1d59b1d147fe1c82afa00"),
                    // Google Assistant on Android Automotive OS
                    "com.google.android.carassistant" to
                        setOf("74b6fbf710e8d90d44d340125889b42306a62c4379d0e5a66220e3a68abf90e2"),
                    // Google app (Assistant voice control)
                    "com.google.android.googlequicksearchbox" to
                        setOf("f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"),
                )
        }
    }

/** Thin seam over [PackageManager] so caller validation can be unit tested. */
interface PackageInspector {
    val ownPackageName: String
    val ownUid: Int

    fun uidOwnsPackage(
        uid: Int,
        packageName: String,
    ): Boolean

    /** SHA-256 digests (lowercase hex) of the package's current signing certificates; empty if unknown. */
    fun signingCertificatesSha256(packageName: String): Set<String>

    fun platformCertificatesSha256(): Set<String>
}

@Singleton
class AndroidPackageInspector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PackageInspector {
        private val packageManager: PackageManager get() = context.packageManager

        override val ownPackageName: String get() = context.packageName
        override val ownUid: Int get() = Process.myUid()

        private val platformCerts: Set<String> by lazy { signingCertificatesSha256(PLATFORM_PACKAGE) }

        override fun uidOwnsPackage(
            uid: Int,
            packageName: String,
        ): Boolean = packageManager.getPackagesForUid(uid)?.contains(packageName) == true

        override fun platformCertificatesSha256(): Set<String> = platformCerts

        @SuppressLint("PackageManagerGetSignatures")
        override fun signingCertificatesSha256(packageName: String): Set<String> {
            val signatures =
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val info: PackageInfo =
                            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                        val signingInfo = info.signingInfo ?: return emptySet()
                        // A package with multiple signers must match all of them; reject that edge case.
                        if (signingInfo.hasMultipleSigners()) return emptySet()
                        signingInfo.signingCertificateHistory
                    } else {
                        @Suppress("DEPRECATION")
                        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                        @Suppress("DEPRECATION")
                        info.signatures?.takeIf { it.size == 1 }
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                } ?: return emptySet()
            return signatures.map { sha256Hex(it.toByteArray()) }.toSet()
        }

        private companion object {
            const val PLATFORM_PACKAGE = "android"
        }
    }

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
