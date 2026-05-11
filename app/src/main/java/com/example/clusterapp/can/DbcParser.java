package com.example.clusterapp.can;

import java.util.ArrayList;
import java.util.List;

/**
 * Very small DBC parser – extracts only the signals we need for built‑in debug screens.
 * Supports SG_ lines and the optional factor/offset/min/max/unit parts.
 * The parser is lightweight and runs on the device (no external libs).
 */
public final class DbcParser {
    private DbcParser() {}

    /** Parse a raw DBC file (as a String) and return all signals.
     *  This method ignores messages, nodes, etc. – it only extracts SG_ definitions.
     */
    public static List<CanSignal> parse(String dbcText) {
        List<CanSignal> signals = new ArrayList<>();
        if (dbcText == null) return signals;
        for (String line : dbcText.split("\n")) {
            line = line.trim();
            if (!line.startsWith("SG_")) continue;
            // Format:
            // SG_ <name> : <startBit>|<len>@<byteOrder><sign> (<factor>,<offset>) [<min>|<max>] "<unit>" <receiver>
            // Example: SG_ STEER_TORQUE : 7|16@0- (1,0) [-500|500] "" EPS
            try {
                // Split name from rest
                int nameEnd = line.indexOf(':');
                if (nameEnd < 0) continue;
                String name = line.substring(3, nameEnd).trim();
                String rest = line.substring(nameEnd + 1).trim();

                // startBit|len@byteOrderSign
                int colonIdx = rest.indexOf('@');
                String bitsPart = rest.substring(0, colonIdx).trim();
                String[] bits = bitsPart.split("\\|", 2);
                int startBit = Integer.parseInt(bits[0].trim());
                int length = Integer.parseInt(bits[1].trim());
                char endianChar = rest.charAt(colonIdx + 1);
                boolean isBigEndian = endianChar == '1'; // Motorola = 1, Intel = 0
                char signedChar = rest.charAt(colonIdx + 2);
                boolean isSigned = signedChar == '-';

                // factor/offset part
                int factorStart = rest.indexOf('(');
                int factorEnd = rest.indexOf(')');
                String factorOffset = rest.substring(factorStart + 1, factorEnd);
                String[] fo = factorOffset.split(",");
                double factor = Double.parseDouble(fo[0].trim());
                double offset = Double.parseDouble(fo[1].trim());

                // min/max part
                int minStart = rest.indexOf('[');
                int minEnd = rest.indexOf(']');
                String[] minMax = rest.substring(minStart + 1, minEnd).split("\\|");
                double min = Double.parseDouble(minMax[0].trim());
                double max = Double.parseDouble(minMax[1].trim());

                // unit part – quoted string (may be empty)
                int unitStart = rest.indexOf('"', minEnd);
                int unitEnd = rest.indexOf('"', unitStart + 1);
                String unit = (unitStart >= 0 && unitEnd > unitStart) ?
                        rest.substring(unitStart + 1, unitEnd) : "";

                // receiver – token after the closing quote
                String afterQuote = rest.substring(unitEnd + 1).trim();
                String receiver = afterQuote.isEmpty() ? "" : afterQuote.split(" ")[0];

                signals.add(new CanSignal(name, startBit, length, isBigEndian, isSigned,
                        factor, offset, min, max, unit, receiver));
            } catch (Exception ignored) {
                // Silently skip unparsable lines – not critical for debugging screens.
            }
        }
        return signals;
    }
}
