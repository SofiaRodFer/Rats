// Stage the user's existing compatible jar into an isolated development Maven repo.
// Never change Launcher mods or redistribute the third-party binary in Rats.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const source = fs.realpathSync(process.argv[2]);
if (path.basename(source) !== 'FarmersDelight-1.20.1-1.3.4.jar') throw new Error('Expected installed Forge FarmersDelight-1.20.1-1.3.4.jar');
const destination = path.join(__dirname, '../build/compat-maven/local/compat/FarmersDelight/1.20.1-1.3.4');
fs.mkdirSync(destination, {recursive:true});
fs.copyFileSync(source, path.join(destination, path.basename(source)));
fs.writeFileSync(path.join(destination, 'FarmersDelight-1.20.1-1.3.4.pom'), '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>local.compat</groupId><artifactId>FarmersDelight</artifactId><version>1.20.1-1.3.4</version></project>\n');
console.log(JSON.stringify({artifact:path.basename(source), sha256:crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex'), stagedIn:destination}));
