// Update ONLY the named isolated profile, after Minecraft has been closed.
// node scripts/update-test-rats.cjs <test-gameDir> <new-jar>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
function requireClosedClient() {
  try {
    const running = execFileSync('pgrep', ['-f', 'net.minecraft.client.main.Main|--launchTarget forgeclient'], {encoding:'utf8'});
    if (running.trim()) throw new Error('Close Minecraft before replacing its loaded mod');
  } catch (error) { if (error.status !== 1) throw error; }
}
const [directory, artifact] = process.argv.slice(2);
if (!directory || !artifact) throw new Error('Expected test gameDir and new jar');
const gameDir = fs.realpathSync(directory), jar = fs.realpathSync(artifact);
if (path.basename(gameDir) !== 'tres amigos teste' || !/^Rats-1\.20\.1-8\.1\.3-sg\.[456]\.jar$/.test(path.basename(jar))) throw new Error('Only tres amigos teste / sg.4, sg.5 or sg.6 is supported');
requireClosedClient();
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const manifestPath = path.join(gameDir, 'RATS_TEST_INSTALLATION.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath));
if (manifest.gameDir !== gameDir || manifest.name !== 'tres amigos teste') throw new Error('Instance manifest does not match');
const modsDir = path.join(gameDir, 'mods');
const names = fs.readdirSync(modsDir).filter(n => n.endsWith('.jar'));
const rats = names.filter(n => /^Rats-1\.20\.1-.*\.jar$/i.test(n));
if (rats.length !== 1 || rats[0] !== manifest.artifact) throw new Error('Unexpected Rats installation');
if (names.length !== manifest.modCount) throw new Error('Mod count changed; inspect before updating');
for (const name of names) if (hash(path.join(modsDir, name)) !== manifest.installedMods[name]) throw new Error(`Installed mod changed: ${name}`);
const newName = path.basename(jar), sha256 = hash(jar);
if (fs.existsSync(path.join(modsDir, newName))) throw new Error('Target jar already present');
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const targetVersion = path.basename(jar).match(/sg\.(\d+)\.jar$/)[1];
const rollbackDir = path.join(gameDir, 'rollback', `before-sg${targetVersion}-${stamp}`);
fs.mkdirSync(rollbackDir, {recursive:true});
fs.copyFileSync(manifestPath, path.join(rollbackDir, 'RATS_TEST_INSTALLATION.json'), fs.constants.COPYFILE_EXCL);
const saves = path.join(gameDir, 'saves');
const worldsBackedUp = fs.existsSync(saves);
if (worldsBackedUp) fs.cpSync(saves, path.join(rollbackDir, 'saves'), {recursive:true, force:false, errorOnExist:true});
const temporary = path.join(modsDir, `${newName}.installing`);
fs.copyFileSync(jar, temporary, fs.constants.COPYFILE_EXCL);
if (hash(temporary) !== sha256) throw new Error('Staged jar hash mismatch');
requireClosedClient();
fs.renameSync(path.join(modsDir, rats[0]), path.join(rollbackDir, rats[0]));
fs.renameSync(temporary, path.join(modsDir, newName));
delete manifest.installedMods[rats[0]];
manifest.installedMods[newName] = sha256;
manifest.artifact = newName; manifest.sha256 = sha256; manifest.updated = new Date().toISOString();
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
if (hash(path.join(modsDir, newName)) !== sha256) throw new Error('Installed hash mismatch');
console.log(JSON.stringify({gameDir, artifact:newName, sha256, modCount:names.length, rollbackDir, worldsBackedUp, worldsModified:false}, null, 2));
