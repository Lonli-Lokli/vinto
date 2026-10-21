// A COPY of the portfolio's capture check, `tools/android-archive.mjs` in gulnya, as of gulnya commit 21330e2 (2026-10-20).
// Vinto must build without ../gulnya checked out, so its build calls this copy and reads no gulnya file. Change it
// there, then copy it again: gulnya's test fails when this file, after this header, differs from the original.
// Everything below is the original, byte for byte, from the blank line after this header.
//
// android-archive.mjs: what an .apk or .aab says about itself, read in memory with Node alone (no Android SDK).
//
//   import { zipEntries, apkPackage, aabPackage } from './android-archive.mjs';
//
// zipEntries inflates every file entry of a zip (an .apk or .aab is one). apkPackage and aabPackage read the
// <manifest package> an .apk's binary XML or an .aab's protobuf XML (aapt2's two formats) carries, which is the
// applicationId the build was made with. Used by capture-check.mjs.

import zlib from 'node:zlib';

/** Every file entry of a zip held in memory, inflated: [{ name, data }]. */
export function zipEntries(buf, label) {
  const floor = Math.max(0, buf.length - 0xffff - 22);
  let eocd = -1;
  for (let i = buf.length - 22; i >= floor; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new Error(`${label} is not a zip file`);
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  if (count === 0xffff || p === 0xffffffff) throw new Error(`${label} is a ZIP64 file, which this does not read`);
  const out = [];
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error(`${label}: central directory entry ${n} is damaged`);
    const method = buf.readUInt16LE(p + 10);
    const size = buf.readUInt32LE(p + 20);
    const nameLength = buf.readUInt16LE(p + 28);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLength);
    p += 46 + nameLength + buf.readUInt16LE(p + 30) + buf.readUInt16LE(p + 32);
    if (name.endsWith('/')) continue;
    if (buf.readUInt32LE(local) !== 0x04034b50) throw new Error(`${label}: ${name} has no local header`);
    // The local header's extra field may differ from the central one (zipalign pads it), so its own lengths count.
    const start = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
    const raw = buf.subarray(start, start + size);
    if (method === 0) out.push({ name, data: raw });
    else if (method === 8) out.push({ name, data: zlib.inflateRawSync(raw) });
    else throw new Error(`${label}: ${name} uses compression method ${method}, which this does not read`);
  }
  return out;
}

/** The strings of an Android binary XML string pool chunk. */
function stringPool(chunk) {
  const headerSize = chunk.readUInt16LE(2);
  const count = chunk.readUInt32LE(8);
  const utf8 = (chunk.readUInt32LE(16) & 0x100) !== 0;
  const start = chunk.readUInt32LE(20);
  const out = [];
  for (let i = 0; i < count; i++) {
    let p = start + chunk.readUInt32LE(headerSize + i * 4);
    if (utf8) {
      p += chunk[p] & 0x80 ? 2 : 1; // the length in characters, then the length in bytes
      let length = chunk[p];
      if (length & 0x80) {
        length = ((length & 0x7f) << 8) | chunk[p + 1];
        p += 2;
      } else {
        p += 1;
      }
      out.push(chunk.toString('utf8', p, p + length));
    } else {
      let length = chunk.readUInt16LE(p);
      p += 2;
      if (length & 0x8000) {
        length = ((length & 0x7fff) << 16) | chunk.readUInt16LE(p);
        p += 2;
      }
      out.push(chunk.toString('utf16le', p, p + length * 2));
    }
  }
  return out;
}

/** The package attribute of an APK's AndroidManifest.xml (Android binary XML): the applicationId. */
export function apkPackage(xml) {
  if (xml.readUInt16LE(0) !== 0x0003) throw new Error('AndroidManifest.xml is not binary XML');
  let strings = [];
  for (let p = xml.readUInt16LE(2); p < xml.length; p += xml.readUInt32LE(p + 4)) {
    const type = xml.readUInt16LE(p);
    if (type === 0x0001) strings = stringPool(xml.subarray(p, p + xml.readUInt32LE(p + 4)));
    if (type !== 0x0102) continue;
    // The first start element is <manifest>: its attribute extension follows the 16-byte node header.
    const ext = p + xml.readUInt16LE(p + 2);
    if (strings[xml.readUInt32LE(ext + 4)] !== 'manifest') throw new Error('the manifest does not start with <manifest>');
    const first = xml.readUInt16LE(ext + 8);
    const size = xml.readUInt16LE(ext + 10);
    for (let i = 0; i < xml.readUInt16LE(ext + 12); i++) {
      const a = ext + first + i * size;
      if (xml.readInt32LE(a) !== -1 || strings[xml.readUInt32LE(a + 4)] !== 'package') continue;
      const raw = xml.readInt32LE(a + 8);
      if (raw >= 0) return strings[raw];
      if (xml[a + 15] === 0x03) return strings[xml.readUInt32LE(a + 16)]; // a typed string value
    }
    throw new Error('<manifest> has no package attribute');
  }
  throw new Error('AndroidManifest.xml has no <manifest> element');
}

/** The fields of one protobuf message: [{ field, wire, value }], value a number or, for wire type 2, a Buffer. */
function protoFields(buf) {
  const varint = (p) => {
    let value = 0;
    let scale = 1;
    for (;;) {
      const b = buf[p++];
      if (b === undefined) throw new Error('a protobuf varint runs past its message');
      value += (b & 0x7f) * scale;
      scale *= 128;
      if (!(b & 0x80)) return [value, p];
    }
  };
  const out = [];
  let p = 0;
  while (p < buf.length) {
    const [key, next] = varint(p);
    p = next;
    const field = Math.floor(key / 8);
    const wire = key % 8;
    if (wire === 0) {
      const [value, after] = varint(p);
      out.push({ field, wire, value });
      p = after;
    } else if (wire === 2) {
      const [length, after] = varint(p);
      out.push({ field, wire, value: buf.subarray(after, after + length) });
      p = after + length;
    } else if (wire === 1 || wire === 5) {
      p += wire === 1 ? 8 : 4;
    } else {
      throw new Error(`protobuf wire type ${wire} is not one aapt2 writes`);
    }
  }
  return out;
}

/** The package attribute of an AAB's base/manifest/AndroidManifest.xml (aapt2's protobuf XmlNode). */
export function aabPackage(manifest) {
  // XmlNode { element = 1 }; XmlElement { name = 3, attribute = 4 }; XmlAttribute { namespace_uri = 1, name = 2, value = 3 }
  const element = protoFields(manifest).find((f) => f.field === 1 && f.wire === 2)?.value;
  if (!element) throw new Error('the bundle manifest has no root element');
  const fields = protoFields(element);
  const name = fields.find((f) => f.field === 3 && f.wire === 2)?.value.toString('utf8');
  if (name !== 'manifest') throw new Error('the bundle manifest does not start with <manifest>');
  for (const f of fields.filter((x) => x.field === 4 && x.wire === 2)) {
    const attribute = protoFields(f.value);
    const text = (n) => attribute.find((x) => x.field === n && x.wire === 2)?.value.toString('utf8') ?? '';
    if (text(2) === 'package' && text(1) === '') return text(3);
  }
  throw new Error('<manifest> has no package attribute');
}
