package com.smartline.smartos.modules.user.webauthn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CborDecoderTest {

  @Test
  void decodesUnsignedAndNegativeIntegers() {
    assertEquals(0L, CborDecoder.decode(new byte[] {0x00}));
    assertEquals(23L, CborDecoder.decode(new byte[] {0x17}));
    assertEquals(24L, CborDecoder.decode(new byte[] {0x18, 0x18}));
    assertEquals(1000L, CborDecoder.decode(new byte[] {0x19, 0x03, (byte) 0xe8}));
    assertEquals(-1L, CborDecoder.decode(new byte[] {0x20}));
    assertEquals(-7L, CborDecoder.decode(new byte[] {0x26}));
  }

  @Test
  void decodesByteStringsTextArraysMapsAndBooleans() {
    assertArrayEquals(new byte[] {1, 2, 3},
        assertInstanceOf(byte[].class, CborDecoder.decode(new byte[] {0x43, 0x01, 0x02, 0x03})));
    assertEquals("abc", CborDecoder.decode(new byte[] {0x63, 'a', 'b', 'c'}));
    assertEquals(List.of(1L, 2L), CborDecoder.decode(new byte[] {(byte) 0x82, 0x01, 0x02}));
    assertEquals(Map.of(1L, 2L),
        CborDecoder.decode(new byte[] {(byte) 0xa1, 0x01, 0x02}));
    assertEquals(Boolean.TRUE, CborDecoder.decode(new byte[] {(byte) 0xf5}));
    assertEquals(Boolean.FALSE, CborDecoder.decode(new byte[] {(byte) 0xf4}));
  }

  @Test
  void decodesNestedAttestationShapedMaps() {
    byte[] cbor = new byte[] {
        (byte) 0xa3, // map(3)
        0x63, 'f', 'm', 't', 0x64, 'n', 'o', 'n', 'e',
        0x67, 'a', 't', 't', 'S', 't', 'm', 't', (byte) 0xa0, // {}
        0x68, 'a', 'u', 't', 'h', 'D', 'a', 't', 'a',
        0x43, 0x01, 0x02, 0x03
    };

    Map<?, ?> decoded = assertInstanceOf(Map.class, CborDecoder.decode(cbor));
    assertEquals("none", decoded.get("fmt"));
    assertEquals(Map.of(), decoded.get("attStmt"));
    assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) decoded.get("authData"));
  }

  @Test
  void decodesTaggedValuesAndReportsOffsets() {
    // tag(24) wrapping the integer 1
    assertEquals(1L, CborDecoder.decode(new byte[] {(byte) 0xd8, 0x18, 0x01}));

    CborDecoder.Decoded decoded = CborDecoder.decodeAt(
        new byte[] {0x63, 'a', 'b', 'c', (byte) 0xff}, 0);
    assertEquals("abc", decoded.value());
    assertEquals(4, decoded.nextOffset());
  }

  @Test
  void rejectsTruncatedAndTrailingData() {
    assertThrows(WebAuthnException.class, () -> CborDecoder.decode(new byte[0]));
    assertThrows(WebAuthnException.class, () -> CborDecoder.decode(new byte[] {0x43, 0x01}));
    assertThrows(WebAuthnException.class, () -> CborDecoder.decode(new byte[] {0x01, 0x02}));
    assertThrows(WebAuthnException.class, () -> CborDecoder.decodeAt(new byte[] {0x01}, 5));
  }

  @Test
  void rejectsIndefiniteLengthItems() {
    WebAuthnException error = assertThrows(WebAuthnException.class,
        () -> CborDecoder.decode(new byte[] {0x5f, 0x41, 0x01, (byte) 0xff}));
    assertTrue(error.getMessage().contains("Indefinite-length"));
  }

  @Test
  void rejectsReservedSimpleValues() {
    // Major type 7, additional information 28 is reserved by RFC 8949.
    assertThrows(WebAuthnException.class, () -> CborDecoder.decode(new byte[] {(byte) 0xfc}));
  }
}
