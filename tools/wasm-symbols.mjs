#!/usr/bin/env node
/**
 * Give the web build's WebAssembly what Sentry needs to read a crash back to a Kotlin line.
 *
 *     node tools/wasm-symbols.mjs [dist-dir] [out-dir]
 *
 * Run after `./gradlew :composeApp:wasmJsBrowserDistribution` and before the dist is published.
 * It changes two files in the dist and writes one beside it:
 *
 *   * **the app's `.wasm`** gains a 16-byte `build_id` custom section — the key Sentry files a
 *     WebAssembly debug file under. Appended after every other section, so no code moves;
 *   * **`index.html`** gains `<meta name="vinto-wasm-build-id" content="<file> <build id>">`, which
 *     is how the crash reporter learns that key and names it in a report's `debug_meta`;
 *   * **`<out-dir>/<file>.debug.wasm`** is the debug file `upload-web-sourcemaps.mjs` sends to Sentry,
 *     and which is never published: that `build_id`, and DWARF line information for the module.
 *
 * ## Why DWARF, and why from a source map
 *
 * A wasm frame used to reach Sentry as a function name and nothing else: `kotlin.Throwable.<init>
 * in 8e3eab49a1da99ef8c43.wasm [Line: Unknown]`. Sentry symbolicates WebAssembly from DWARF only —
 * it does not read wasm source maps — and finds the DWARF by the module's `build_id`, which
 * Kotlin/Wasm does not write. The compiler *can* write DWARF (`-Xwasm-generate-dwarf`), but it
 * writes version 5 and `wasm-opt` stops on it (`unsupported DWARF version (5)`), so it cannot
 * survive the optimiser the shipped module goes through.
 *
 * A source map can. Handed one, `wasm-opt` carries every location through its passes
 * (`composeApp/build.gradle.kts` sets that up, and writes `<module>.wasm.optimized.map` beside the
 * optimised module). This turns that map into the smallest DWARF Sentry can use: one compile unit,
 * one subprogram per function named from the module's own name section, and a line table.
 *
 * ## The debug file holds no code
 *
 * Sentry's wasm reader (`symbolic`) cannot open Kotlin's module. It notes which types are function
 * types by *recursion group*, and functions name them by *type index*; a Wasm GC module declares
 * its types in recursion groups, so every function "references unknown type" and the whole file is
 * refused. It reads nothing from the code it needs, though: the `build_id`, and the DWARF. So the
 * debug file is a module with those custom sections and no others. With no code section its code
 * offset is 0, so the DWARF's addresses are plain offsets into the served module — exactly what V8
 * prints in a frame (`wasm-function[123]:0x564a6e`) and what the report sends as `instruction_addr`.
 *
 * ## It refuses to guess
 *
 * The map is only true for the module it was written for, so the dist's `.wasm` is matched to the
 * optimised module **by its bytes** — never by name — and a dist holding anything else fails here.
 * Then the DWARF it wrote is read back and every row compared with the map, and a function table
 * that does not line up with the name section fails too. A debug file that is subtly wrong is
 * worse than none: every frame resolves, and to the wrong line.
 *
 * Library code (the standard library, coroutines, Compose) has mappings too, but no source on this
 * machine; those files are named by their package path, so a frame reads
 * `kotlinx/coroutines/JobSupport.kt:1520` rather than an address.
 */

import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { basename, dirname, join, relative, resolve, sep } from 'node:path';

const DIST = process.argv[2] ?? 'composeApp/build/dist/wasmJs/productionExecutable';
const OUT = process.argv[3] ?? 'composeApp/build/wasm-symbols';
const OPTIMIZED = 'composeApp/build/compileSync/wasmJs/main/productionExecutable/optimized/vinto-kmp-composeApp.wasm';
const MAP = `${OPTIMIZED}.optimized.map`;
const META = 'vinto-wasm-build-id';
const OURS = 'game.vinto.';

/** The repository, which is where the build ran and where `--include-sources` will look. */
const ROOT = process.cwd();

function fail(...lines) {
  lines.forEach((line) => console.error(line));
  process.exit(1);
}

// ---------------------------------------------------------------------------------------------
// Reading a module

function uleb(buf, p) {
  let value = 0;
  let shift = 0;
  let byte;
  do {
    byte = buf[p++];
    value += (byte & 0x7f) * 2 ** shift;
    shift += 7;
  } while (byte & 0x80);
  return [value, p];
}

function skipLeb(buf, p) {
  while (buf[p++] & 0x80);
  return p;
}

function sections(buf) {
  if (buf.readUInt32LE(0) !== 0x6d736100) throw new Error('not a WebAssembly module');
  const found = [];
  let p = 8;
  while (p < buf.length) {
    const start = p;
    const id = buf[p++];
    let size;
    [size, p] = uleb(buf, p);
    const content = p;
    let name = null;
    if (id === 0) {
      const [length, at] = uleb(buf, content);
      name = buf.toString('utf8', at, at + length);
    }
    p = content + size;
    found.push({ id, start, content, end: p, name });
  }
  if (p !== buf.length) throw new Error('the section table runs past the end of the module');
  return found;
}

/** A value type: one byte, or a reference type with its heap type after it. */
function skipValueType(buf, p) {
  const type = buf[p++];
  return type === 0x63 || type === 0x64 ? skipLeb(buf, p) : p;
}

function skipLimits(buf, p) {
  const flags = buf[p++];
  p = skipLeb(buf, p);
  return flags & 1 ? skipLeb(buf, p) : p;
}

/** How many of the module's functions are imported, which is where its own start counting. */
function importedFunctions(buf, section) {
  if (!section) return 0;
  let p = section.content;
  let count;
  [count, p] = uleb(buf, p);
  let functions = 0;
  for (let i = 0; i < count; i++) {
    for (let name = 0; name < 2; name++) {
      let length;
      [length, p] = uleb(buf, p);
      p += length;
    }
    const kind = buf[p++];
    if (kind === 0x00) {
      functions++;
      p = skipLeb(buf, p);
    } else if (kind === 0x01) {
      p = skipLimits(buf, skipValueType(buf, p));
    } else if (kind === 0x02) {
      p = skipLimits(buf, p);
    } else if (kind === 0x03) {
      p = skipValueType(buf, p) + 1;
    } else if (kind === 0x04) {
      p = skipLeb(buf, p + 1);
    } else {
      throw new Error(`import ${i} has kind ${kind}, which this reader does not know`);
    }
  }
  return functions;
}

/** Function index → name, from the `name` section's function-name subsection. */
function functionNames(buf, section) {
  const names = new Map();
  if (!section) return names;
  let [length, p] = uleb(buf, section.content);
  p += length;
  while (p < section.end) {
    const kind = buf[p++];
    let size;
    [size, p] = uleb(buf, p);
    const next = p + size;
    if (kind === 1) {
      let count;
      [count, p] = uleb(buf, p);
      for (let i = 0; i < count; i++) {
        let index;
        let nameLength;
        [index, p] = uleb(buf, p);
        [nameLength, p] = uleb(buf, p);
        names.set(index, buf.toString('utf8', p, p + nameLength));
        p += nameLength;
      }
    }
    p = next;
  }
  return names;
}

/** Each function body's bytes, as offsets into the module. */
function functionBodies(buf, code) {
  let [count, p] = uleb(buf, code.content);
  const bodies = [];
  for (let i = 0; i < count; i++) {
    let size;
    [size, p] = uleb(buf, p);
    bodies.push({ start: p, end: p + size });
    p += size;
  }
  if (p !== code.end) throw new Error('the code section does not end where its bodies do');
  return bodies;
}

// ---------------------------------------------------------------------------------------------
// Reading the source map

const BASE64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

/**
 * Rows of `[offset in the module, source index or -1, line]`, in the order the map lists them.
 *
 * A wasm source map is one line long, and its column is the byte offset into the module. A segment
 * with no source says that the bytes from there on belong to nothing — those become line 0.
 */
function mapRows(map) {
  if (map.mappings.includes(';')) throw new Error('a wasm source map has one line, and this one has more');
  const rows = [];
  let column = 0;
  let source = 0;
  let line = 0;
  for (const segment of map.mappings.split(',')) {
    if (!segment) continue;
    const values = [];
    let value = 0;
    let shift = 0;
    for (const char of segment) {
      const digit = BASE64.indexOf(char);
      if (digit < 0) throw new Error(`'${char}' is not a source-map digit`);
      value += (digit & 31) * 2 ** shift;
      if (digit & 32) {
        shift += 5;
      } else {
        values.push(value % 2 ? -(value - 1) / 2 : value / 2);
        value = 0;
        shift = 0;
      }
    }
    column += values[0];
    if (values.length >= 4) {
      source += values[1];
      line += values[2];
      rows.push([column, source, line + 1]);
    } else {
      rows.push([column, -1, 0]);
    }
  }
  return rows;
}

/**
 * Where a source lives, as a reader of a crash report wants it named.
 *
 * Ours resolve to a file in this repository and are named from its root, which is also how
 * `sentry-cli --include-sources` finds them to bundle. A library's path is whatever its own build
 * machine had, made relative to the compiler's working directory — `…/kotlin/daemon/Z:/BuildAgent/
 * work/<hash>/core/commonMain/src/kotlinx/serialization/Serializers.kt` — so it is cut back to the
 * part after its last `src/`, which is the package path.
 */
function nameSource(raw, mapDirectory) {
  const absolute = resolve(mapDirectory, raw);
  if (absolute.startsWith(ROOT + sep) && existsSync(absolute)) {
    return { ours: true, path: relative(ROOT, absolute).split(sep).join('/') };
  }
  let path = raw.replace(/\\/g, '/');
  const src = path.lastIndexOf('/src/');
  if (src >= 0) path = path.slice(src + '/src/'.length).replace(/^[A-Za-z0-9]+Main\/(kotlin\/)?/, '');
  path = path.replace(/^(\.\.\/)+/, '').replace(/^.*\/kotlin\/daemon\//, '');
  return { ours: false, path };
}

// ---------------------------------------------------------------------------------------------
// Writing

class Writer {
  constructor(capacity = 1 << 16) {
    this.buf = Buffer.alloc(capacity);
    this.length = 0;
  }

  room(n) {
    if (this.length + n <= this.buf.length) return;
    const bigger = Buffer.alloc(Math.max(this.buf.length * 2, this.length + n));
    this.buf.copy(bigger, 0, 0, this.length);
    this.buf = bigger;
  }

  u8(value) {
    this.room(1);
    this.buf[this.length++] = value;
    return this;
  }

  u16(value) {
    this.room(2);
    this.buf.writeUInt16LE(value, this.length);
    this.length += 2;
    return this;
  }

  u32(value) {
    this.room(4);
    this.buf.writeUInt32LE(value >>> 0, this.length);
    this.length += 4;
    return this;
  }

  uleb(value) {
    do {
      let byte = value % 128;
      value = Math.floor(value / 128);
      if (value !== 0) byte |= 0x80;
      this.u8(byte);
    } while (value !== 0);
    return this;
  }

  sleb(value) {
    for (;;) {
      const byte = value & 0x7f;
      value >>= 7;
      if ((value === 0 && !(byte & 0x40)) || (value === -1 && byte & 0x40)) return this.u8(byte);
      this.u8(byte | 0x80);
    }
  }

  bytes(buffer) {
    this.room(buffer.length);
    buffer.copy(this.buf, this.length);
    this.length += buffer.length;
    return this;
  }

  string(text) {
    return this.bytes(Buffer.from(text, 'utf8')).u8(0);
  }

  done() {
    return this.buf.subarray(0, this.length);
  }
}

function customSection(name, payload) {
  const nameBytes = Buffer.from(name, 'utf8');
  const head = new Writer(16).uleb(nameBytes.length).bytes(nameBytes).done();
  return new Writer(payload.length + 32).u8(0).uleb(head.length + payload.length).bytes(head).bytes(payload).done();
}

// DWARF 4 constants, only the ones written.
const DW_TAG_compile_unit = 0x11;
const DW_TAG_subprogram = 0x2e;
const DW_AT_name = 0x03;
const DW_AT_stmt_list = 0x10;
const DW_AT_low_pc = 0x11;
const DW_AT_high_pc = 0x12;
const DW_AT_comp_dir = 0x1b;
const DW_AT_producer = 0x25;
const DW_FORM_addr = 0x01;
const DW_FORM_data4 = 0x06;
const DW_FORM_string = 0x08;
const DW_FORM_strp = 0x0e;
const DW_FORM_sec_offset = 0x17;
const DW_LNS_copy = 0x01;
const DW_LNS_advance_pc = 0x02;
const DW_LNS_advance_line = 0x03;
const DW_LNS_set_file = 0x04;
const DW_LNE_end_sequence = 0x01;
const DW_LNE_set_address = 0x02;
const OPCODE_BASE = 13;
const STANDARD_OPCODE_LENGTHS = [0, 1, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1];

function debugAbbrev() {
  const w = new Writer(64);
  w.uleb(1).uleb(DW_TAG_compile_unit).u8(1);
  for (const [at, form] of [
    [DW_AT_producer, DW_FORM_string],
    [DW_AT_name, DW_FORM_string],
    [DW_AT_comp_dir, DW_FORM_string],
    [DW_AT_stmt_list, DW_FORM_sec_offset],
    [DW_AT_low_pc, DW_FORM_addr],
    [DW_AT_high_pc, DW_FORM_data4],
  ]) w.uleb(at).uleb(form);
  w.uleb(0).uleb(0);
  w.uleb(2).uleb(DW_TAG_subprogram).u8(0);
  for (const [at, form] of [
    [DW_AT_name, DW_FORM_strp],
    [DW_AT_low_pc, DW_FORM_addr],
    [DW_AT_high_pc, DW_FORM_data4],
  ]) w.uleb(at).uleb(form);
  w.uleb(0).uleb(0);
  return w.uleb(0).done();
}

function debugInfo({ unit, codeStart, codeSize, functions }) {
  const strings = new Writer(1 << 20);
  const body = new Writer(1 << 20);
  body.u16(4).u32(0).u8(4); // version, .debug_abbrev offset, address size
  body.uleb(1).string('tools/wasm-symbols.mjs').string(unit).string(ROOT).u32(0).u32(codeStart).u32(codeSize);
  for (const fn of functions) {
    body.uleb(2).u32(strings.length).u32(fn.low).u32(fn.high - fn.low);
    strings.string(fn.name);
  }
  body.u8(0);
  const info = new Writer(body.length + 4).u32(body.length).bytes(body.done()).done();
  return { info, str: strings.done() };
}

function debugLine({ files, rows, codeStart, codeEnd }) {
  const header = new Writer(1 << 16);
  header.u8(1).u8(1).u8(1).u8(-5 & 0xff).u8(14).u8(OPCODE_BASE); // instruction length, ops, is_stmt, line base/range
  STANDARD_OPCODE_LENGTHS.forEach((n) => header.u8(n));
  header.u8(0); // no include directories: every file is named from the compile unit's directory
  for (const file of files) header.string(file).uleb(0).uleb(0).uleb(0);
  header.u8(0);

  const program = new Writer(1 << 20);
  program.u8(0).uleb(5).u8(DW_LNE_set_address).u32(codeStart);
  let address = codeStart;
  let file = 1;
  let line = 1;
  for (const row of rows) {
    if (row.file !== file) program.u8(DW_LNS_set_file).uleb((file = row.file));
    if (row.line !== line) program.u8(DW_LNS_advance_line).sleb(row.line - line), (line = row.line);
    if (row.address !== address) program.u8(DW_LNS_advance_pc).uleb(row.address - address), (address = row.address);
    program.u8(DW_LNS_copy);
  }
  program.u8(DW_LNS_advance_pc).uleb(codeEnd - address);
  program.u8(0).uleb(1).u8(DW_LNE_end_sequence);

  const head = header.done();
  const unit = new Writer(head.length + program.length + 16);
  unit.u16(4).u32(head.length).bytes(head).bytes(program.done());
  return new Writer(unit.length + 4).u32(unit.length).bytes(unit.done()).done();
}

// ---------------------------------------------------------------------------------------------
// Reading back what was written, as the check that it says what the map says

function readLineTable(line) {
  let p = 4;
  const end = 4 + line.readUInt32LE(0);
  if (line.readUInt16LE(p) !== 4) throw new Error('not a version 4 line table');
  p += 2;
  const headerLength = line.readUInt32LE(p);
  p += 4;
  const programStart = p + headerLength;
  p += 6 + STANDARD_OPCODE_LENGTHS.length;
  while (line[p] !== 0) p = line.indexOf(0, p) + 1;
  p++;
  const files = [];
  while (line[p] !== 0) {
    const nul = line.indexOf(0, p);
    files.push(line.toString('utf8', p, nul));
    p = skipLeb(line, skipLeb(line, skipLeb(line, nul + 1)));
  }
  if (p + 1 !== programStart) throw new Error('the line table header is not the length it claims');
  p = programStart;
  const rows = [];
  let address = 0;
  let file = 1;
  let lineNumber = 1;
  let ended = false;
  while (p < end) {
    const op = line[p++];
    if (op === 0) {
      let length;
      [length, p] = uleb(line, p);
      const sub = line[p];
      if (sub === DW_LNE_set_address) address = line.readUInt32LE(p + 1);
      else if (sub === DW_LNE_end_sequence) ended = true;
      else throw new Error(`extended opcode ${sub} was never written`);
      p += length;
    } else if (op === DW_LNS_copy) {
      rows.push({ address, file, line: lineNumber });
    } else if (op === DW_LNS_advance_pc) {
      let delta;
      [delta, p] = uleb(line, p);
      address += delta;
    } else if (op === DW_LNS_advance_line) {
      let value = 0;
      let shift = 0;
      let byte;
      do {
        byte = line[p++];
        value |= (byte & 0x7f) << shift;
        shift += 7;
      } while (byte & 0x80);
      if (shift < 32 && byte & 0x40) value |= -1 << shift;
      lineNumber += value;
    } else if (op === DW_LNS_set_file) {
      [file, p] = uleb(line, p);
    } else {
      throw new Error(`opcode ${op} was never written`);
    }
  }
  if (!ended) throw new Error('the line table never ends its sequence');
  return { files, rows };
}

// ---------------------------------------------------------------------------------------------

if (!existsSync(OPTIMIZED) || !existsSync(MAP)) {
  fail(
    `no ${OPTIMIZED} with its ${basename(MAP)} beside it.`,
    'Build the web client first (./gradlew :composeApp:wasmJsBrowserDistribution); the map is written by',
    'the source-map arguments composeApp/build.gradle.kts hands wasm-opt.',
  );
}

const original = readFileSync(OPTIMIZED);
const digest = createHash('sha256').update(original).digest();
const buildId = digest.subarray(0, 16);
const buildIdHex = buildId.toString('hex');
const served = Buffer.concat([original, customSection('build_id', buildId)]);

// By the bytes, never by the name: webpack renames the module to its own hash, and a map paired
// with the wrong module resolves every frame confidently to the wrong line.
const candidates = readdirSync(DIST).filter((f) => f.endsWith('.wasm'));
const matches = candidates.filter((f) => {
  const bytes = readFileSync(join(DIST, f));
  return bytes.equals(original) || bytes.equals(served);
});
if (matches.length !== 1) {
  fail(
    `expected exactly one module in ${DIST} with the bytes of ${OPTIMIZED}; found ${matches.length}`,
    `among: ${candidates.join(', ') || 'nothing'}. A dist from another build would be described by the wrong map.`,
  );
}
const file = matches[0];

const table = sections(original);
const code = table.find((s) => s.id === 10);
if (!code) fail(`${OPTIMIZED} has no code section`);
const imported = importedFunctions(original, table.find((s) => s.id === 2));
const names = functionNames(original, table.find((s) => s.id === 0 && s.name === 'name'));
const bodies = functionBodies(original, code);
const codeStart = code.content;
const codeSize = code.end - code.content;

// Offsets into the served module, not into its code section: see "The debug file holds no code".
const functions = bodies.map((body, i) => ({
  name: names.get(imported + i) ?? `wasm-function[${imported + i}]`,
  low: body.start,
  high: body.end,
}));

const map = JSON.parse(readFileSync(MAP, 'utf8'));
const mapDirectory = dirname(resolve(MAP));
const sources = map.sources.map((raw) => nameSource(raw, mapDirectory));
const rows = mapRows(map)
  .filter(([at]) => at >= codeStart && at < code.end)
  .map(([at, source, line]) => ({ address: at, file: source < 0 ? 0 : source + 1, line }));
// A row with no source keeps the file it is in and says line 0, which DWARF reads as "no line".
rows.forEach((row, i) => {
  if (row.file === 0) row.file = i > 0 ? rows[i - 1].file : 1;
});
for (let i = 1; i < rows.length; i++) {
  if (rows[i].address < rows[i - 1].address) fail('the source map is not in address order, which a line table must be');
}

const lineSection = debugLine({ files: sources.map((s) => s.path), rows, codeStart, codeEnd: code.end });
const { info, str } = debugInfo({ unit: basename(OPTIMIZED), codeStart, codeSize, functions });
const debug = Buffer.concat([
  original.subarray(0, 8),
  customSection('build_id', buildId),
  customSection('.debug_abbrev', debugAbbrev()),
  customSection('.debug_info', info),
  customSection('.debug_str', str),
  customSection('.debug_line', lineSection),
]);

// The check: read the debug file back as a stranger would, and hold it to the map.
const written = sections(debug);
const writtenBuildId = written.filter((s) => s.name === 'build_id');
if (writtenBuildId.length !== 1) fail('the debug file has no single build_id section');
if (written.some((s) => s.id !== 0)) fail('the debug file carries more than custom sections, which Sentry would try to parse');
const writtenLine = written.find((s) => s.name === '.debug_line');
const [nameLength, lineAt] = uleb(debug, writtenLine.content);
const readBack = readLineTable(debug.subarray(lineAt + nameLength, writtenLine.end));
if (readBack.rows.length !== rows.length) fail(`the line table holds ${readBack.rows.length} rows; the map has ${rows.length}`);
readBack.rows.forEach((row, i) => {
  const expected = rows[i];
  if (row.address !== expected.address || row.line !== expected.line || readBack.files[row.file - 1] !== sources[expected.file - 1].path) {
    fail(`row ${i} reads back as ${JSON.stringify(row)}; the map says ${JSON.stringify(expected)}`);
  }
});
for (const s of sections(served).slice(0, -1)) {
  const before = table.find((t) => t.start === s.start);
  if (!before || before.end !== s.end) fail('stamping the build id moved a section, so every frame address would be off');
}

// And the names line up: a row in one of our files should sit, more often than not, in one of our
// functions. If the import count were misread, every name would be shifted onto a stranger's body.
let ourRows = 0;
let ourRowsInOurFunctions = 0;
let fn = 0;
for (const row of rows) {
  while (fn < functions.length - 1 && functions[fn].high <= row.address) fn++;
  if (row.line === 0 || !sources[row.file - 1]?.ours) continue;
  ourRows++;
  if (functions[fn].low <= row.address && functions[fn].name.startsWith(OURS)) ourRowsInOurFunctions++;
}
const agreement = ourRows === 0 ? 0 : ourRowsInOurFunctions / ourRows;
if (agreement < 0.5) {
  fail(`only ${(agreement * 100).toFixed(1)}% of the rows in our sources fall in functions named as ours — the function table is misaligned`);
}

mkdirSync(OUT, { recursive: true });
const debugFile = join(OUT, `${file}.debug.wasm`);
writeFileSync(debugFile, debug);
writeFileSync(join(DIST, file), served);

const index = join(DIST, 'index.html');
const html = readFileSync(index, 'utf8');
const tag = `<meta name="${META}" content="${file} ${buildIdHex}">`;
const stamped = html.includes(`name="${META}"`)
  ? html.replace(new RegExp(`<meta name="${META}"[^>]*>`), tag)
  : html.replace('</head>', `  ${tag}\n  </head>`);
if (!stamped.includes(tag)) fail(`${index} has no </head> to put the build id in`);
writeFileSync(index, stamped);

const summary = {
  file,
  buildId: buildIdHex,
  debugId: `${buildIdHex}0`,
  debugFile,
  functions: functions.length,
  named: functions.filter((f) => !f.name.startsWith('wasm-function[')).length,
  rows: rows.length,
  sources: { ours: sources.filter((s) => s.ours).length, libraries: sources.filter((s) => !s.ours).length },
  agreement: Number(agreement.toFixed(3)),
};
writeFileSync(join(OUT, 'symbols.json'), `${JSON.stringify(summary, null, 2)}\n`);
console.log(JSON.stringify(summary, null, 2));
