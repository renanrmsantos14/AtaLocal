import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(fileURLToPath(new URL("../..", import.meta.url)));
const androidRoot = resolve(repoRoot, "src-tauri", "gen", "android");
const manifestPath = resolve(androidRoot, "app", "src", "main", "AndroidManifest.xml");

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
  const permission = "android.permission.RECORD_AUDIO";
  if (!manifest.includes(permission)) {
    const openTag = manifest.match(/<manifest\b[^>]*>/)?.[0];
    if (!openTag) {
      throw new Error(`manifesto Android invalido: ${manifestPath}`);
    }
    manifest = manifest.replace(
      openTag,
      `${openTag}\n    <uses-permission android:name="${permission}" />`,
    );
    writeFileSync(manifestPath, manifest);
  }
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

ensureManifestPermission();
ensureRuntimePermission();
console.log(`Android preparado: ${manifestPath}`);
