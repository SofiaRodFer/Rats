// Run with the official launcher CLOSED. Copies pack files, never worlds/accounts.
// node scripts/install-test-profile.cjs <minecraft-dir> <source-profile-key> <jar>
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const [rootArg, sourceKey, jarArg] = process.argv.slice(2);
if (!rootArg || !sourceKey || !jarArg) throw new Error('Expected minecraft-dir, source-profile-key, jar');
const root = fs.realpathSync(rootArg);
const jar = fs.realpathSync(jarArg);
const metadataPath = path.join(root, 'launcher_profiles.json');
const originalMetadata = fs.readFileSync(metadataPath);
const metadata = JSON.parse(originalMetadata);
const source = metadata.profiles[sourceKey];
if (!source || !source.gameDir || !source.lastVersionId?.includes('1.20.1-forge-')) throw new Error('Expected existing Forge 1.20.1 profile');
const name = 'tres amigos teste';
const key = 'tres_amigos_teste';
const destination = path.join(root, name);
if (fs.existsSync(destination) || metadata.profiles[key] || Object.values(metadata.profiles).some(p => p.name === name)) throw new Error('Test profile already exists; refusing overwrite');
const pack = fs.realpathSync(source.gameDir);
if (pack === destination || pack === root) throw new Error('Unsafe source/destination');
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const sourceMods = fs.readdirSync(path.join(pack, 'mods')).filter(n => n.endsWith('.jar')).sort();
const oldRats = sourceMods.filter(n => /^Rats-1\.20\.1-.*\.jar$/i.test(n));
if (oldRats.length !== 1 || !/^Rats-1\.20\.1-8\.1\.3-sg\.3\.jar$/.test(path.basename(jar))) throw new Error('Expected exactly one original Rats and the sg.3 artifact');
const beforeHashes = Object.fromEntries(sourceMods.map(n => [n, hash(path.join(pack, 'mods', n))]));
const stage = fs.mkdtempSync(path.join(root, 'tres-amigos-teste-stage-'));
for (const entry of ['mods', 'config', 'defaultconfigs', 'resourcepacks', 'shaderpacks', 'kubejs', 'scripts', 'moonlight-global-datapacks', 'options.txt']) {
  const from = path.join(pack, entry);
  if (fs.existsSync(from)) fs.cpSync(from, path.join(stage, entry), { recursive: true, errorOnExist: true, force: false });
}
// Preserve the replaced jar outside mods so it cannot be loaded twice.
fs.mkdirSync(path.join(stage, 'rollback'));
fs.renameSync(path.join(stage, 'mods', oldRats[0]), path.join(stage, 'rollback', oldRats[0]));
fs.copyFileSync(jar, path.join(stage, 'mods', path.basename(jar)), fs.constants.COPYFILE_EXCL);
const installedMods = fs.readdirSync(path.join(stage, 'mods')).filter(n => n.endsWith('.jar')).sort();
if (installedMods.length !== sourceMods.length || fs.existsSync(path.join(stage, 'saves'))) throw new Error('Invalid staged pack or unexpected worlds');
for (const mod of sourceMods) {
  if (hash(path.join(pack, 'mods', mod)) !== beforeHashes[mod]) throw new Error('Source mod changed during staging');
  if (mod !== oldRats[0] && hash(path.join(stage, 'mods', mod)) !== beforeHashes[mod]) throw new Error(`Other mod differs: ${mod}`);
}
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const backup = path.join(root, `launcher_profiles.pre-tres-amigos-teste-${stamp}.json`);
const installation = {
  name, created: new Date().toISOString(), sourceGameDir: pack, gameDir: destination,
  version: source.lastVersionId, artifact: path.basename(jar), sha256: hash(jar),
  modCount: installedMods.length, worldsCopied: false, launcherBackup: backup,
  originalMods: beforeHashes,
  installedMods: Object.fromEntries(installedMods.map(n => [n, hash(path.join(stage, 'mods', n))]))
};
fs.writeFileSync(path.join(stage, 'RATS_TEST_INSTALLATION.json'), JSON.stringify(installation, null, 2) + '\n', { flag: 'wx' });
if (!fs.readFileSync(metadataPath).equals(originalMetadata)) throw new Error('Launcher metadata changed; close launcher and retry');
fs.copyFileSync(metadataPath, backup, fs.constants.COPYFILE_EXCL);
fs.renameSync(stage, destination);
metadata.profiles[key] = { ...source, name, gameDir: destination, created: installation.created };
delete metadata.profiles[key].lastUsed;
const metadataTemp = path.join(root, `launcher_profiles.test-${stamp}.json`);
fs.writeFileSync(metadataTemp, JSON.stringify(metadata, null, 2) + '\n', { flag: 'wx' });
if (!fs.readFileSync(metadataPath).equals(originalMetadata)) throw new Error('Launcher restarted during install; backup and test files retained');
fs.renameSync(metadataTemp, metadataPath);
const saved = JSON.parse(fs.readFileSync(metadataPath));
if (saved.profiles[key].gameDir !== destination || saved.profiles[key].name !== name || hash(path.join(destination, 'mods', path.basename(jar))) !== installation.sha256) throw new Error('Installation verification failed');
console.log(JSON.stringify({ name, gameDir: destination, version: source.lastVersionId, modCount: installedMods.length, sha256: installation.sha256, backup, worldsCopied: false }, null, 2));
