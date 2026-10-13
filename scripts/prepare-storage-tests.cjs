// Isolated development staging only; never touch Launcher jars or bundle them.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const sourceDir = fs.realpathSync(process.argv[2]);
const artifacts = [
  ['ironchest','1.20.1-14.4.4'],
  ['sophisticatedcore','1.20.1-1.5.4.2362'],
  ['sophisticatedstorage','1.20.1-1.5.0.2137'],
];
for (const [artifact,version] of artifacts) {
  const name = `${artifact}-${version}.jar`, source = path.join(sourceDir,name);
  const dest = path.join(__dirname,'../build/compat-maven/local/compat',artifact,version);
  fs.mkdirSync(dest,{recursive:true}); fs.copyFileSync(source,path.join(dest,name));
  fs.writeFileSync(path.join(dest,`${artifact}-${version}.pom`),`<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>local.compat</groupId><artifactId>${artifact}</artifactId><version>${version}</version></project>\n`);
  console.log(JSON.stringify({artifact:name,sha256:crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex')}));
}
