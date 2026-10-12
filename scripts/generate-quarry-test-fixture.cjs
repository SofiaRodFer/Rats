// Minimal compressed NBT structure for the development-only GameTests.
const fs = require('node:fs');
const zlib = require('node:zlib');
const path = require('node:path');
const destination = path.join(__dirname, '../src/main/resources/data/rats/structures/quarry_test_empty.nbt');
const short = n => { const b = Buffer.alloc(2); b.writeUInt16BE(n); return b; };
const int = n => { const b = Buffer.alloc(4); b.writeInt32BE(n); return b; };
const str = s => Buffer.concat([short(Buffer.byteLength(s)), Buffer.from(s)]);
const tag = (type, name, value) => Buffer.concat([Buffer.from([type]), str(name), value]);
const compound = tags => Buffer.concat([...tags, Buffer.from([0])]);
// Explicit air clears terrain; a ceiling excludes falling blocks from above.
const airBlocks = [];
for (let x = 0; x < 24; x++) for (let y = 0; y < 12; y++) for (let z = 0; z < 24; z++) {
  airBlocks.push(compound([
    tag(9, 'pos', Buffer.concat([Buffer.from([3]), int(3), int(x), int(y), int(z)])),
    tag(3, 'state', int(y === 11 ? 1 : 0))
  ]));
}
const root = compound([
  tag(3, 'DataVersion', int(3465)),
  tag(9, 'size', Buffer.concat([Buffer.from([3]), int(3), int(24), int(12), int(24)])),
  tag(9, 'palette', Buffer.concat([Buffer.from([10]), int(2), compound([tag(8, 'Name', str('minecraft:air'))]), compound([tag(8, 'Name', str('minecraft:barrier'))])])),
  tag(9, 'blocks', Buffer.concat([Buffer.from([10]), int(airBlocks.length), ...airBlocks])),
  tag(9, 'entities', Buffer.concat([Buffer.from([10]), int(0)]))
]);
fs.mkdirSync(path.dirname(destination), { recursive: true });
fs.writeFileSync(destination, zlib.gzipSync(Buffer.concat([Buffer.from([10]), str(''), root])));
console.log('Generated quarry GameTest fixture (24x12x24).');
