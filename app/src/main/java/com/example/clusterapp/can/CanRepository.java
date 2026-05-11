package com.example.clusterapp.can;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds CAN message and signal definitions for each bus, and is the central reference
 * for the CAN decoding pipeline described below.
 *
 * <h2>CAN bus topology</h2>
 * The Honda Civic (2016-2017) exposes two buses through the headunit middleware:
 * <ul>
 *   <li><b>FCAN</b> (Fast CAN, 500 kbit/s) — powertrain and chassis ECUs: engine, transmission,
 *       VSA (stability control), EPS (steering), wheel speeds, braking.</li>
 *   <li><b>BCAN</b> (Body CAN, 125 kbit/s) — body control module, instrument cluster, ADAS
 *       camera, SCM stalk, doors, seatbelts, lighting, cruise control HUD.</li>
 * </ul>
 * Signal definitions for both buses are compiled from the OpenDBC files in {@code docs/opendbc/}
 * and hard-coded in {@link CanDefinitions} — no DBC files are read at runtime.
 *
 * <h2>Listener registration</h2>
 * Raw frame callbacks are obtained from {@code IVehicleInfoManagerApService}, hosted by
 * {@code com.mitsubishielectric.ada.appservice.vehicleinfomanager.VehicleInfoManagerApService}.
 * Two separate listener interfaces exist:
 * <pre>
 *   BCAN frames → IVehicleBcanInformationListener  (register via transaction 25)
 *   FCAN frames → IVehicleFcanInformationListener  (register via transaction 35)
 * </pre>
 * Registration is performed in {@code VehicleDataSource.registerBcanListener()} and
 * {@code registerFcanListener()} immediately after the service connects.  Each method calls
 * {@code IBinder.transact(tx, data, reply, 0)} with the listener's own {@code IBinder} written
 * into the data Parcel.
 *
 * <h2>Callback protocol</h2>
 * When a CAN frame arrives the middleware fires transaction code {@code 1} on the registered
 * listener binder.  The inbound Parcel contains, in order:
 * <ol>
 *   <li>{@code enforceInterface(descriptor)} — interface token</li>
 *   <li>{@code int type} — frame type identifier (may be the DBC message ID or a
 *       Mitsubishi-internal code; must be verified empirically on the device)</li>
 *   <li>{@code int present} — 1 if a Bundle follows, 0 if not</li>
 *   <li>{@code Bundle info} — frame payload.  For the known BCAN TRICOM frame
 *       ({@code type == 318333520}) the Bundle contains pre-decoded keys
 *       {@code C_TRICOM_RANGE} and {@code C_TRICOM_RANGE_UNIT}.  For other frames
 *       the Bundle is expected to carry the raw 8-byte CAN payload as a {@code byte[]}
 *       under one of the keys {@code "data"}, {@code "payload"}, {@code "frame"}, or
 *       {@code "msg"}.</li>
 * </ol>
 *
 * <h2>Frame decoding</h2>
 * Once raw bytes are extracted from the Bundle, {@link CanDecoder#decode} looks up the
 * matching {@link CanMessage} by {@code type} / message ID, then iterates its
 * {@link CanSignal} list.  For each signal:
 * <ol>
 *   <li><b>Bit extraction</b> — bits are pulled from the 8-byte frame according to the DBC
 *       start-bit and length fields.
 *       <ul>
 *         <li>Motorola / big-endian ({@code isBigEndian = true}): {@code startBit} is the
 *             MSB position in DBC bit numbering (byte N covers bits 8N+7 … 8N).  Extraction
 *             walks from MSB toward LSB within each byte, then jumps to bit 7 of the next
 *             byte when crossing a byte boundary.</li>
 *         <li>Intel / little-endian ({@code isBigEndian = false}): {@code startBit} is the
 *             LSB; bits are read at consecutive increasing positions.</li>
 *       </ul>
 *   </li>
 *   <li><b>Sign extension</b> — if {@code isSigned} is true the raw unsigned integer is
 *       two's-complement extended to 64 bits.</li>
 *   <li><b>Physical value</b> — {@code physical = raw * factor + offset} as defined by the
 *       DBC {@code (factor, offset)} tuple.</li>
 * </ol>
 * Decoded values are stored in a {@code ConcurrentHashMap<String, Double>} (one per bus) in
 * {@code VehicleDataSource} and read on the main thread each poll tick to populate
 * {@link com.example.clusterapp.VehicleState} fields prefixed with {@code fcan} or {@code bcan}.
 */
public final class CanRepository {
    private final Map<String, List<CanMessage>> busMessages = new HashMap<>();

    public void addBus(String busName, List<CanMessage> messages) {
        busMessages.put(busName, messages);
    }

    public List<CanMessage> getMessages(String busName) {
        List<CanMessage> msgs = busMessages.get(busName);
        return msgs != null ? msgs : Collections.<CanMessage>emptyList();
    }

    /** Flat list of all signals across all messages on this bus (used by DbcScreen). */
    public List<CanSignal> getSignals(String busName) {
        List<CanMessage> msgs = busMessages.get(busName);
        if (msgs == null) return Collections.<CanSignal>emptyList();
        List<CanSignal> out = new ArrayList<>();
        for (CanMessage m : msgs) out.addAll(m.signals);
        return out;
    }
}
