// Deterministic native geometry; preserves original material, no raster stand-ins.
const fs = require('node:fs'), path = require('node:path');
const destination = path.join(__dirname, '../src/main/resources/assets/rats/models/block');
const faces = (texture, uv) => Object.fromEntries(['north','south','east','west','up','down'].map(face => [face,{texture,uv}]));
const box = (from,to,texture,uv=[0,0,16,16]) => ({from,to,faces:faces(texture,uv)});
const save = (name,textures,elements) => fs.writeFileSync(path.join(destination,name+'.json'),JSON.stringify({parent:'minecraft:block/block',render_type:'minecraft:cutout',textures,elements},null,2)+'\n');
const metal = {metal:'rats:block/iron_bars',particle:'rats:block/iron_bars'};
const bars = [];
// Four vertical bars and three rails, versus a dense repeating texture plane.
for (const x of [0,5,10,15]) bars.push(box([x,0,0],[x+1,16,0.5],'#metal',[3,0,4,16]));
for (const y of [0,8,15]) bars.push(box([0,y,0],[16,y+1,0.5],'#metal',[3,0,4,1]));
save('rat_cage_bars',metal,bars);
const ground = {ground:'rats:block/rat_cage_ground',particle:'rats:block/rat_cage_ground'};
// Keep upper floor faces away from outer metal bars (no coplanar z-fighting).
save('rat_cage_shelf',ground,[box([0.6,0,0.6],[15.4,2,15.4],'#ground')]);
save('rat_cage_shelf_access',ground,[box([0.6,0,10],[15.4,2,15.4],'#ground')]);
save('rat_cage_shelf_tube',ground,[box([0.6,0,0.6],[4,2,15.4],'#ground'),box([12,0,0.6],[15.4,2,15.4],'#ground'),box([4,0,0.6],[12,2,4],'#ground'),box([4,0,12],[12,2,15.4],'#ground')]);
const ladder = [box([3,0,1],[4,16,2],'#metal',[3,0,4,16]),box([12,0,1],[13,16,2],'#metal',[3,0,4,16])];
for (const y of [1,5,9,13]) ladder.push(box([3,y,1],[13,y+1,3],'#metal',[3,0,4,1]));
save('rat_cage_ladder',metal,ladder);
console.log('Generated wider cage bars, tier shelves and interior ladder models.');
