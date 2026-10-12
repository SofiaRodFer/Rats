// Read-only entity-region diagnostic; outputs rats' positions, not player data.
const fs = require('node:fs'), path = require('node:path'), zlib = require('node:zlib');
function parseNbt(buffer) {
  let p = 0;
  const byte = () => buffer.readUInt8(p++);
  const short = () => { const n = buffer.readUInt16BE(p); p += 2; return n; };
  const int = () => { const n = buffer.readInt32BE(p); p += 4; return n; };
  const string = () => { const n = short(), s = buffer.toString('utf8', p, p + n); p += n; return s; };
  const value = type => {
    switch (type) {
      case 1: return byte();
      case 2: return short();
      case 3: return int();
      case 4: { const n = buffer.readBigInt64BE(p); p += 8; return n.toString(); }
      case 5: { const n = buffer.readFloatBE(p); p += 4; return n; }
      case 6: { const n = buffer.readDoubleBE(p); p += 8; return n; }
      case 7: { const n = int(); p += n; return null; }
      case 8: return string();
      case 9: { const kind = byte(), n = int(); return Array.from({length: n}, () => value(kind)); }
      case 10: { const obj = {}; let kind; while ((kind = byte()) !== 0) { const key = string(); obj[key] = value(kind); } return obj; }
      case 11: { const n = int(); return Array.from({length: n}, int); }
      case 12: { const n = int(); p += n * 8; return {length:n}; }
      default: throw new Error(`Invalid NBT type ${type}`);
    }
  };
  const kind = byte(); string(); return value(kind);
}
const folder = process.argv[2];
for (const file of fs.readdirSync(folder).filter(n => /^r\.-?\d+\.-?\d+\.mca$/.test(n))) {
  const region = fs.readFileSync(path.join(folder, file));
  if (region.length < 8192) continue; // Empty region placeholder.
  for (let slot = 0; slot < 1024; slot++) {
    const offset = region.readUInt32BE(slot * 4) >>> 8;
    if (!offset) continue;
    const start = offset * 4096, length = region.readInt32BE(start), compression = region[start + 4];
    if (compression !== 2) continue;
    const chunk = parseNbt(zlib.inflateSync(region.subarray(start + 5, start + 4 + length)));
    for (const entity of chunk.Entities || []) if (entity.id?.startsWith('rats:')) {
      console.log(JSON.stringify({region:file, id:entity.id, pos:entity.Pos, motion:entity.Motion, variant:entity.ColorVariant, command:entity.Command, items:entity.Items, tray:entity.SilverTrayCargo, returning:entity.QuarryReturning, ore:entity.OreVeinWork, deposit:entity.DepositPos, facing:entity.DepositFacing}));
    }
    for (const block of chunk.block_entities || []) if (block.id === 'rats:rat_quarry') console.log(JSON.stringify({region:file, id:block.id, pos:[block.x,block.y,block.z], direction:block.QuarryDirection, progress:block.QuarryProgress, extent:block.QuarryBuiltExtent, workers:block.QuarryWorkers?.length, paused:block.QuarryPaused, length:block.QuarryLength, stored:block.Items?.length}));
  }
}
