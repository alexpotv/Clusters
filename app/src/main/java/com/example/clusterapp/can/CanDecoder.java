package com.example.clusterapp.can;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes a raw CAN frame payload into physical signal values.
 *
 * DBC bit numbering (used for both Motorola and Intel start bits):
 *   Byte 0 → bits 7..0,  Byte 1 → bits 15..8,  Byte N → bits (8N+7)..(8N)
 *
 * Motorola (big-endian): startBit is the MSB position; bits flow toward lower
 *   bit indices within each byte, then wrap to bit 7 of the next byte.
 *
 * Intel (little-endian): startBit is the LSB position; bits are consecutive.
 *
 * Physical = raw_value * factor + offset
 */
public final class CanDecoder {

    /**
     * Find the message with the given ID in the list and decode all its signals.
     * Returns an empty map if the message ID is not found or the frame is null/empty.
     */
    public static Map<String, Double> decode(int msgId, byte[] frame,
                                              List<CanMessage> messages) {
        if (frame == null || frame.length == 0) return Collections.emptyMap();
        for (CanMessage msg : messages) {
            if (msg.id == msgId) return decodeMessage(frame, msg);
        }
        return Collections.emptyMap();
    }

    private static Map<String, Double> decodeMessage(byte[] frame, CanMessage msg) {
        Map<String, Double> result = new HashMap<>();
        for (CanSignal sig : msg.signals) {
            long raw = sig.isBigEndian
                    ? extractMotorola(frame, sig.startBit, sig.length)
                    : extractIntel(frame, sig.startBit, sig.length);
            if (sig.isSigned) raw = signExtend(raw, sig.length);
            result.put(sig.name, raw * sig.factor + sig.offset);
        }
        return result;
    }

    /**
     * Motorola (big-endian) extraction.
     * startBit = MSB position in DBC bit numbering.
     * Traverse: decrement within byte, then jump to bit 7 of next byte.
     */
    private static long extractMotorola(byte[] frame, int startBit, int length) {
        long result = 0;
        int pos = startBit;
        for (int i = length - 1; i >= 0; i--) {
            int byteIdx = pos / 8;
            int bitIdx  = pos % 8;
            if (byteIdx < frame.length) {
                result |= ((long)(frame[byteIdx] >> bitIdx) & 1L) << i;
            }
            if (bitIdx == 0) pos += 15;  // cross byte boundary to next byte's MSB
            else             pos -= 1;
        }
        return result;
    }

    /**
     * Intel (little-endian) extraction.
     * startBit = LSB position; bits are consecutive in increasing bit-position order.
     */
    private static long extractIntel(byte[] frame, int startBit, int length) {
        long result = 0;
        for (int i = 0; i < length; i++) {
            int pos     = startBit + i;
            int byteIdx = pos / 8;
            int bitIdx  = pos % 8;
            if (byteIdx < frame.length) {
                result |= ((long)(frame[byteIdx] >> bitIdx) & 1L) << i;
            }
        }
        return result;
    }

    /** Two's-complement sign extension for a value of the given bit length. */
    private static long signExtend(long raw, int length) {
        long signBit = 1L << (length - 1);
        return (raw & signBit) != 0 ? raw | (~0L << length) : raw;
    }

    private CanDecoder() {}
}
