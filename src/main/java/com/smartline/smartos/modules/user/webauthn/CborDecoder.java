package com.smartline.smartos.modules.user.webauthn;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal CBOR (RFC 8949) decoder covering the subset used by WebAuthn
 * attestation objects and COSE keys: integers, byte/text strings, arrays,
 * maps, tags, booleans, null and floats. Indefinite-length items are rejected.
 */
public final class CborDecoder {

  public record Decoded(Object value, int nextOffset) {
  }

  private CborDecoder() {
  }

  public static Object decode(byte[] data) {
    Decoded decoded = decodeAt(data, 0);
    if (decoded.nextOffset() != data.length) {
      throw new WebAuthnException("Trailing bytes after CBOR value");
    }
    return decoded.value();
  }

  public static Decoded decodeAt(byte[] data, int offset) {
    if (offset < 0 || offset >= data.length) {
      throw new WebAuthnException("Truncated CBOR data");
    }
    int initial = data[offset++] & 0xff;
    int major = initial >>> 5;
    int additional = initial & 0x1f;

    if (major == 7) {
      return decodeSimple(data, offset, additional);
    }

    Length length = readLength(data, offset, additional);
    offset = length.nextOffset();

    switch (major) {
      case 0 -> {
        return new Decoded(length.value(), offset);
      }
      case 1 -> {
        return new Decoded(-1 - length.value(), offset);
      }
      case 2 -> {
        int len = checkedLength(length.value());
        requireAvailable(data, offset, len);
        byte[] bytes = new byte[len];
        System.arraycopy(data, offset, bytes, 0, len);
        return new Decoded(bytes, offset + len);
      }
      case 3 -> {
        int len = checkedLength(length.value());
        requireAvailable(data, offset, len);
        String text = new String(data, offset, len, StandardCharsets.UTF_8);
        return new Decoded(text, offset + len);
      }
      case 4 -> {
        int count = checkedLength(length.value());
        List<Object> list = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
          Decoded item = decodeAt(data, offset);
          list.add(item.value());
          offset = item.nextOffset();
        }
        return new Decoded(list, offset);
      }
      case 5 -> {
        int count = checkedLength(length.value());
        Map<Object, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
          Decoded key = decodeAt(data, offset);
          offset = key.nextOffset();
          Decoded value = decodeAt(data, offset);
          offset = value.nextOffset();
          map.put(key.value(), value.value());
        }
        return new Decoded(map, offset);
      }
      case 6 -> {
        // Tag: the semantics are not needed for attestation/COSE here.
        return decodeAt(data, offset);
      }
      default -> throw new WebAuthnException("Unsupported CBOR major type " + major);
    }
  }

  private static Decoded decodeSimple(byte[] data, int offset, int additional) {
    switch (additional) {
      case 20:
        return new Decoded(Boolean.FALSE, offset);
      case 21:
        return new Decoded(Boolean.TRUE, offset);
      case 22:
        return new Decoded(null, offset);
      case 23:
        return new Decoded(null, offset);
      case 24: {
        requireAvailable(data, offset, 1);
        return new Decoded(data[offset] & 0xff, offset + 1);
      }
      case 25: {
        requireAvailable(data, offset, 2);
        return new Decoded(halfToFloat(data, offset), offset + 2);
      }
      case 26: {
        requireAvailable(data, offset, 4);
        int bits = ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16)
            | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
        return new Decoded(Float.intBitsToFloat(bits), offset + 4);
      }
      case 27: {
        requireAvailable(data, offset, 8);
        long bits = 0;
        for (int i = 0; i < 8; i++) {
          bits = (bits << 8) | (data[offset + i] & 0xffL);
        }
        return new Decoded(Double.longBitsToDouble(bits), offset + 8);
      }
      default:
        throw new WebAuthnException("Unsupported CBOR simple value " + additional);
    }
  }

  private static float halfToFloat(byte[] data, int offset) {
    int half = ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    int sign = (half >>> 15) & 0x1;
    int exponent = (half >>> 10) & 0x1f;
    int mantissa = half & 0x3ff;
    float value;
    if (exponent == 0) {
      value = (float) (mantissa * Math.pow(2, -24));
    } else if (exponent == 0x1f) {
      value = mantissa == 0 ? Float.POSITIVE_INFINITY : Float.NaN;
    } else {
      value = (float) ((1 + mantissa / 1024.0) * Math.pow(2, exponent - 15));
    }
    return sign == 1 ? -value : value;
  }

  private static Length readLength(byte[] data, int offset, int additional) {
    if (additional < 24) {
      return new Length(additional, offset);
    }
    int bytes;
    switch (additional) {
      case 24 -> bytes = 1;
      case 25 -> bytes = 2;
      case 26 -> bytes = 4;
      case 27 -> bytes = 8;
      case 31 -> throw new WebAuthnException("Indefinite-length CBOR is not supported");
      default -> throw new WebAuthnException("Invalid CBOR length encoding");
    }
    requireAvailable(data, offset, bytes);
    long value = 0;
    for (int i = 0; i < bytes; i++) {
      value = (value << 8) | (data[offset + i] & 0xffL);
    }
    if (value < 0) {
      throw new WebAuthnException("CBOR length overflow");
    }
    return new Length(value, offset + bytes);
  }

  private static int checkedLength(long value) {
    if (value > Integer.MAX_VALUE) {
      throw new WebAuthnException("CBOR item too large");
    }
    return (int) value;
  }

  private static void requireAvailable(byte[] data, int offset, int length) {
    if (offset < 0 || length < 0 || offset + length > data.length) {
      throw new WebAuthnException("Truncated CBOR data");
    }
  }

  private record Length(long value, int nextOffset) {
  }
}
