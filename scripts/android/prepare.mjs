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

  const classWithBody = /(class\s+MainActivity\s*:\s*TauriActivity\(\)\s*\{)/;
  const classWithoutBody = /(class\s+MainActivity\s*:\s*TauriActivity\(\)\s*)$/m;
  if ((!classWithBody.test(activity) && !classWithoutBody.test(activity)) || activity.includes("override fun onCreate")) {
    throw new Error(
      `MainActivity.kt mudou de formato; adicione a permissao de microfone manualmente: ${activityPath}`,
    );
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

  const onCreate = `
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
    }
  }
`;
  if (classWithBody.test(activity)) {
    activity = activity.replace(classWithBody, `$1${onCreate}`);
  } else {
    activity = activity.replace(classWithoutBody, `$1{${onCreate}}`);
  }
  writeFileSync(activityPath, activity);
}

ensureManifestPermission();
ensureRuntimePermission();
console.log(`Android preparado: ${manifestPath}`);
