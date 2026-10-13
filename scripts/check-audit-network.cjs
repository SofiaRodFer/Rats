// Structural regression only: does not simulate a graphical client or wire spoof.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const base = 'src/main/java/com/github/alexthe666/rats/';
const source = fs.readFileSync(path.join(root, base, 'server/message/RatsNetworkHandler.java'), 'utf8');
const clients = ['FarmRosterPacket','StorageOutlinePacket','StorageSnapshotPacket','QuarryRosterPacket',
  'CageRosterPacket','ChangeRatlantisStatusPacket','ManageRatStaffPacket','OpenRatScreenPacket',
  'SyncPlaguePacket','SyncThrownBlockPacket','UpdateCurdlerFluidPacket','UpdateRatFluidPacket',
  'UpdateRatMusicPacket','UpdateSelectedRatPacket'];
const expectedOrder = ['FarmRosterPacket','StorageOutlinePacket','StorageActionPacket','StorageSnapshotPacket',
  'QuarryRosterPacket','CageActionPacket','CageRosterPacket','ChangeRatlantisStatusPacket',
  'ClearRatRecipePacket','CycleRatRecipePacket','DismountRatPacket','ManageRatStaffPacket',
  'OpenRatScreenPacket','RatCommandPacket','RatUpgradeVisibilityPacket','SetDancingRatPacket',
  'SyncArmSwingPacket','SyncPlaguePacket','SyncRatStaffPacket','SyncRatTagPacket','SyncThrownBlockPacket',
  'UpdateCurdlerFluidPacket','UpdateMobFilterPacket','UpdateRatFluidPacket','UpdateRatMusicPacket',
  'UpdateSelectedRatPacket'];
const lines = source.split('\n').filter(line=>line.includes('CHANNEL.registerMessage('));
assert.equal(lines.length, expectedOrder.length);
lines.forEach((line,index)=>{
  assert.ok(line.includes('id++, '+expectedOrder[index]+'.class,'), 'Preserve discriminator '+index);
  const direction = clients.includes(expectedOrder[index]) ? 'PLAY_TO_CLIENT' : 'PLAY_TO_SERVER';
  assert.ok(line.includes('Optional.of(net.minecraftforge.network.NetworkDirection.'+direction+')'),
    'Explicit direction for '+expectedOrder[index]);
});
for(const file of ['server/entity/ai/goal/RatDepositGoal.java','server/entity/ai/goal/RatPickupGoal.java',
  'server/entity/ai/goal/harvest/RatMilkCowGoal.java','server/block/AutoCurdlerBlock.java',
  'server/block/entity/AutoCurdlerBlockEntity.java']) {
  const contents=fs.readFileSync(path.join(root,base,file),'utf8');
  assert.ok(!contents.includes('PacketDistributor.ALL'), 'No cross-dimension fluid broadcast in '+file);
}
for(const file of ['server/items/upgrades/PsychicRatUpgradeItem.java','server/entity/monster/boss/NeoRatlantean.java']) {
  const contents=fs.readFileSync(path.join(root,base,file),'utf8');
  assert.ok(!contents.includes('new SyncThrownBlockPacket('), 'Native entity metadata is sole source of thrown block state');
}
console.log('PASS: 26 packet directions and preserved IDs; scoped fluid broadcasts; native thrown-block metadata.');
