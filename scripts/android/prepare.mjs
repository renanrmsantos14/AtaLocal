import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(fileURLToPath(new URL("../..", import.meta.url)));
const androidRoot = resolve(repoRoot, "src-tauri", "gen", "android");
const manifestPath = resolve(androidRoot, "app", "src", "main", "AndroidManifest.xml");
const gradlePath = resolve(androidRoot, "app", "build.gradle.kts");
const keystorePropertiesPath = resolve(androidRoot, "keystore.properties");

function findFile(root, filename) {
  const pending = [root];
  while (pending.length > 0) {
    const current = pending.pop();
    for (const entry of readdirSync(current, { withFileTypes: true })) {
      const path = resolve(current, entry.name);
      if (entry.isDirectory()) {
        pending.push(path);
      } else if (entry.name === filename) {
        return path;
      }
    }
  }
  return null;
}

function ensureManifestPermission() {
  let manifest = readFileSync(manifestPath, "utf8");
  const permissions = [
    "android.permission.INTERNET",
    "android.permission.RECORD_AUDIO",
  ];
  const missing = permissions.filter((permission) => !manifest.includes(permission));
  if (missing.length === 0) {
    return;
  }

  const openTag = manifest.match(/<manifest\b[^>]*>/)?.[0];
  if (!openTag) {
    throw new Error(`manifesto Android invalido: ${manifestPath}`);
  }
  const declarations = missing
    .map((permission) => `    <uses-permission android:name="${permission}" />`)
    .join("\n");
  manifest = manifest.replace(openTag, `${openTag}\n${declarations}`);
  writeFileSync(manifestPath, manifest);
}

function ensureRuntimePermission() {
  const activityPath = findFile(resolve(androidRoot, "app", "src", "main"), "MainActivity.kt");
  if (!activityPath) {
    throw new Error("MainActivity.kt nao encontrado no projeto Android gerado pelo Tauri");
  }

  let activity = readFileSync(activityPath, "utf8");
  if (activity.includes("RECORD_AUDIO") && activity.includes("requestPermissions")) {
    return;
  }

  const packageEnd = activity.match(/^package\s+[^\r\n]+/m)?.[0];
  if (!packageEnd) {
    throw new Error(`package ausente em ${activityPath}`);
  }

  const imports = [
    "import android.Manifest",
    "import android.content.pm.PackageManager",
    "import android.os.Build",
    "import android.os.Bundle",
  ].filter((line) => !activity.includes(line));
  activity = activity.replace(packageEnd, `${packageEnd}\n${imports.join("\n")}`);

  const permissionCheck = (indent) => `${indent}if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
${indent}    checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
${indent}  requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
${indent}}
`;
  const existingOnCreate = activity.match(/(^[ \t]*override\s+fun\s+onCreate[^\{]*\{)/m);
  if (existingOnCreate) {
    const functionIndent = existingOnCreate[1].match(/^[ \t]*/)?.[0] ?? "";
    const bodyIndent = `${functionIndent}  `;
    const onCreateStart = existingOnCreate.index + existingOnCreate[1].length;
    const superOnCreate = activity.slice(onCreateStart).match(/^[ \t]*super\.onCreate\([^\r\n]*\)(?:\r?$)/m);
    if (!superOnCreate) {
      throw new Error(
        `onCreate Android sem super.onCreate; adicione a permissao de microfone manualmente: ${activityPath}`,
      );
    }
    const insertionPoint = onCreateStart + superOnCreate.index + superOnCreate[0].length;
    activity = `${activity.slice(0, insertionPoint)}\n${permissionCheck(bodyIndent)}${activity.slice(insertionPoint)}`;
    writeFileSync(activityPath, activity);
    return;
  }

  const classLineMatch = activity.match(/^([ \t]*class\s+MainActivity\b[^\r\n]*)(?:\r?$)/m);
  if (!classLineMatch) {
    throw new Error(
      `MainActivity.kt mudou de formato; adicione a permissao de microfone manualmente: ${activityPath}`,
    );
  }

  const onCreate = `
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
${permissionCheck("    ")}
  }
`;
  const classLine = classLineMatch[1];
  if (classLine.includes("{")) {
    activity = activity.replace(classLine, `${classLine}${onCreate}`);
  } else {
    activity = activity.replace(classLine, `${classLine} {${onCreate}}`);
  }
  writeFileSync(activityPath, activity);
}

function ensureReleaseSigning() {
  if (!existsSync(keystorePropertiesPath) || !existsSync(gradlePath)) {
    return;
  }

  let gradle = readFileSync(gradlePath, "utf8");
  let changed = false;
  for (const line of ["import java.io.FileInputStream", "import java.util.Properties"]) {
    if (!gradle.includes(line)) {
      gradle = `${line}\n${gradle}`;
      changed = true;
    }
  }

  if (!gradle.includes('create("atalocalRelease")')) {
    const buildTypesIndex = gradle.indexOf("buildTypes {");
    if (buildTypesIndex < 0) {
      throw new Error(`buildTypes nao encontrado no Gradle Android: ${gradlePath}`);
    }
    const signingConfig = `signingConfigs {
    create("atalocalRelease") {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        keyAlias = keystoreProperties["keyAlias"] as String
        keyPassword = keystoreProperties["password"] as String
        storeFile = file(keystoreProperties["storeFile"] as String)
        storePassword = keystoreProperties["password"] as String
    }
}

`;
    gradle = `${gradle.slice(0, buildTypesIndex)}${signingConfig}${gradle.slice(buildTypesIndex)}`;
    changed = true;
  }

  const signingAssignment = 'signingConfig = signingConfigs.getByName("atalocalRelease")';
  if (!gradle.includes(signingAssignment)) {
    const releaseBlock = gradle.match(/getByName\("release"\)\s*\{/);
    if (!releaseBlock || releaseBlock.index === undefined) {
      throw new Error(`buildType release nao encontrado no Gradle Android: ${gradlePath}`);
    }
    const insertionPoint = releaseBlock.index + releaseBlock[0].length;
    gradle = `${gradle.slice(0, insertionPoint)}\n        ${signingAssignment}${gradle.slice(insertionPoint)}`;
    changed = true;
  }

  if (changed) {
    writeFileSync(gradlePath, gradle);
  }
}

ensureManifestPermission();
ensureRuntimePermission();
ensureReleaseSigning();
console.log(`Android preparado: ${manifestPath}`);
