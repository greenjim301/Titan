package com.lisi.titan;

import android.annotation.TargetApi;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.NoiseSuppressor;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.UUID;

public class TitanAudioEffects {

    private static final String TAG = "TitanAudioEffects";

    // UUIDs for Software Audio Effects that we want to avoid using.
    // The implementor field will be set to "The Android Open Source Project".
    private static final UUID AOSP_ACOUSTIC_ECHO_CANCELER =
            UUID.fromString("bb392ec0-8d4d-11e0-a896-0002a5d5c51b");
    private static final UUID AOSP_NOISE_SUPPRESSOR =
            UUID.fromString("c06c8400-8e06-11e0-9cb6-0002a5d5c51b");

    // Contains the available effect descriptors returned from the
    // AudioEffect.getEffects() call. This result is cached to avoid doing the
    // slow OS call multiple times.
    private static @Nullable AudioEffect.Descriptor[] cachedEffects = null;

    // Contains the audio effect objects. Created in enable() and destroyed
    // in release().
    private @Nullable  AcousticEchoCanceler aec = null;
    private @Nullable  NoiseSuppressor ns = null;

    // Affects the final state given to the setEnabled() method on each effect.
    // The default state is set to "disabled" but each effect can also be enabled
    // by calling setAEC() and setNS().
    // To enable an effect, both the shouldEnableXXX member and the static
    // canUseXXX() must be true.
    private boolean shouldEnableAec = false;
    private boolean shouldEnableNs = false;

    // Returns true if all conditions for supporting HW Acoustic Echo Cancellation (AEC) are
    // fulfilled.
    @TargetApi(18)
    public static boolean isAcousticEchoCancelerSupported() {
        return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_AEC, AOSP_ACOUSTIC_ECHO_CANCELER);
    }

    // Returns true if all conditions for supporting HW Noise Suppression (NS) are fulfilled.
    @TargetApi(18)
    public static boolean isNoiseSuppressorSupported() {
        return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_NS, AOSP_NOISE_SUPPRESSOR);
    }

    public TitanAudioEffects() {
        Log.e(TAG, "ctor" );
    }

    // Call this method to enable or disable the platform AEC. It modifies
    // |shouldEnableAec| which is used in enable() where the actual state
    // of the AEC effect is modified. Returns true if HW AEC is supported and
    // false otherwise.
    public boolean setAEC(boolean enable) {
        Log.e(TAG, "setAEC(" + enable + ")");
        if (!isAcousticEchoCancelerSupported()) {
            Log.w(TAG, "Platform AEC is not supported");
            shouldEnableAec = false;
            return false;
        }
        if (aec != null && (enable != shouldEnableAec)) {
            Log.e(TAG, "Platform AEC state can't be modified while recording");
            return false;
        }
        shouldEnableAec = enable;
        return true;
    }

    // Call this method to enable or disable the platform NS. It modifies
    // |shouldEnableNs| which is used in enable() where the actual state
    // of the NS effect is modified. Returns true if HW NS is supported and
    // false otherwise.
    public boolean setNS(boolean enable) {
        Log.e(TAG, "setNS(" + enable + ")");
        if (!isNoiseSuppressorSupported()) {
            Log.w(TAG, "Platform NS is not supported");
            shouldEnableNs = false;
            return false;
        }
        if (ns != null && (enable != shouldEnableNs)) {
            Log.e(TAG, "Platform NS state can't be modified while recording");
            return false;
        }
        shouldEnableNs = enable;
        return true;
    }

    public void enable(int audioSession) {
        Log.e(TAG, "enable(audioSession=" + audioSession + ")");
        assertTrue(aec == null);
        assertTrue(ns == null);

        if (isAcousticEchoCancelerSupported()) {
            // Create an AcousticEchoCanceler and attach it to the AudioRecord on
            // the specified audio session.
            aec = AcousticEchoCanceler.create(audioSession);
            if (aec != null) {
                boolean enabled = aec.getEnabled();
                boolean enable = shouldEnableAec && isAcousticEchoCancelerSupported();
                if (aec.setEnabled(enable) != AudioEffect.SUCCESS) {
                    Log.e(TAG, "Failed to set the AcousticEchoCanceler state");
                }
                Log.e(TAG,
                        "AcousticEchoCanceler: was " + (enabled ? "enabled" : "disabled") + ", enable: "
                                + enable + ", is now: " + (aec.getEnabled() ? "enabled" : "disabled"));
            } else {
                Log.e(TAG, "Failed to create the AcousticEchoCanceler instance");
            }
        }

        if (isNoiseSuppressorSupported()) {
            // Create an NoiseSuppressor and attach it to the AudioRecord on the
            // specified audio session.
            ns = NoiseSuppressor.create(audioSession);
            if (ns != null) {
                boolean enabled = ns.getEnabled();
                boolean enable = shouldEnableNs && isNoiseSuppressorSupported();
                if (ns.setEnabled(enable) != AudioEffect.SUCCESS) {
                    Log.e(TAG, "Failed to set the NoiseSuppressor state");
                }
                Log.e(TAG,
                        "NoiseSuppressor: was " + (enabled ? "enabled" : "disabled") + ", enable: " + enable
                                + ", is now: " + (ns.getEnabled() ? "enabled" : "disabled"));
            } else {
                Log.e(TAG, "Failed to create the NoiseSuppressor instance");
            }
        }
    }

    // Releases all native audio effect resources. It is a good practice to
    // release the effect engine when not in use as control can be returned
    // to other applications or the native resources released.
    public void release() {
        Log.e(TAG, "release");
        if (aec != null) {
            aec.release();
            aec = null;
        }
        if (ns != null) {
            ns.release();
            ns = null;
        }
    }


    // Helper method which throws an exception when an assertion has failed.
    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    // Returns the cached copy of the audio effects array, if available, or
    // queries the operating system for the list of effects.
    private static @Nullable AudioEffect.Descriptor[] getAvailableEffects() {
        if (cachedEffects != null) {
            return cachedEffects;
        }
        // The caching is best effort only - if this method is called from several
        // threads in parallel, they may end up doing the underlying OS call
        // multiple times. It's normally only called on one thread so there's no
        // real need to optimize for the multiple threads case.
        cachedEffects = AudioEffect.queryEffects();
        return cachedEffects;
    }

    // Returns true if an effect of the specified type is available. Functionally
    // equivalent to (NoiseSuppressor|AutomaticGainControl|...).isAvailable(), but
    // faster as it avoids the expensive OS call to enumerate effects.
    @TargetApi(18)
    private static boolean isEffectTypeAvailable(UUID effectType, UUID blackListedUuid) {
        AudioEffect.Descriptor[] effects = getAvailableEffects();
        if (effects == null) {
            return false;
        }
        for (AudioEffect.Descriptor d : effects) {
            if (d.type.equals(effectType)) {
                return true;//!d.uuid.equals(blackListedUuid);
            }
        }
        return false;
    }
}
