#[cfg(not(target_os = "android"))]
use cpal::traits::{DeviceTrait, HostTrait};
use serde::Serialize;
#[cfg(not(windows))]
use sysinfo::Disks;
use sysinfo::System;

use crate::error::AppResult;
use crate::paths::AppPaths;

#[derive(Debug, Serialize)]
pub struct AudioDevice {
    pub name: String,
    pub is_default: bool,
    pub default_sample_rate: u32,
    pub channels: u16,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum CheckStatus {
    Ok,
    Warn,
    Fail,
}

#[derive(Debug, Serialize)]
pub struct DiagnosticCheck {
    pub id: String,
    pub label: String,
    pub status: CheckStatus,
    pub detail: String,
}

#[derive(Debug, Serialize)]
pub struct SystemDiagnostics {
    pub cpu_name: String,
    pub cpu_cores_physical: usize,
    pub cpu_cores_logical: usize,
    pub total_ram_gb: f64,
    pub available_ram_gb: f64,
    pub data_dir_free_gb: f64,
    pub input_devices: Vec<AudioDevice>,
    pub os_version: String,
    pub checks: Vec<DiagnosticCheck>,
}

fn bytes_to_gb(b: u64) -> f64 {
    b as f64 / 1024.0 / 1024.0 / 1024.0
}

/// Espaco livre no volume que contem `path` (Windows via GetDiskFreeSpaceExW).
#[cfg(windows)]
fn free_space_bytes(path: &std::path::Path) -> u64 {
    use std::os::windows::ffi::OsStrExt;
    use std::ptr;

    #[link(name = "kernel32")]
    extern "system" {
        fn GetDiskFreeSpaceExW(
            lpDirectoryName: *const u16,
            lpFreeBytesAvailableToCaller: *mut u64,
            lpTotalNumberOfBytes: *mut u64,
            lpTotalNumberOfFreeBytes: *mut u64,
        ) -> i32;
    }

    let wide: Vec<u16> = path
        .as_os_str()
        .encode_wide()
        .chain(std::iter::once(0))
        .collect();
    let mut free_avail: u64 = 0;
    let ok = unsafe {
        GetDiskFreeSpaceExW(wide.as_ptr(), &mut free_avail, ptr::null_mut(), ptr::null_mut())
    };
    if ok != 0 {
        free_avail
    } else {
        0
    }
}

#[cfg(not(windows))]
fn free_space_bytes(path: &std::path::Path) -> u64 {
    let disks = Disks::new_with_refreshed_list();
    disks
        .iter()
        .filter(|disk| path.starts_with(disk.mount_point()))
        .max_by_key(|disk| disk.mount_point().components().count())
        .map(|disk| disk.available_space())
        .unwrap_or(0)
}

#[cfg(not(target_os = "android"))]
fn enumerate_input_devices() -> Vec<AudioDevice> {
    let host = cpal::default_host();
    let default_name = host
        .default_input_device()
        .and_then(|d| d.name().ok())
        .unwrap_or_default();

    let Ok(devices) = host.input_devices() else {
        return Vec::new();
    };

    devices
        .filter_map(|d| {
            let name = d.name().ok()?;
            let cfg = d.default_input_config().ok()?;
            Some(AudioDevice {
                is_default: name == default_name,
                name,
                default_sample_rate: cfg.sample_rate().0,
                channels: cfg.channels(),
            })
        })
        .collect()
}

// A tela inicial consulta o diagnostico assim que monta. No Android, o CPAL
// usa JNI/Oboe para consultar configuracoes de audio; essa consulta precoce
// pode ocorrer antes de o contexto de audio estar pronto e derrubar o processo
// nativo. A captura continua usando o dispositivo padrao quando o usuario
// inicia a gravacao; apenas adiamos a enumeracao detalhada.
#[cfg(target_os = "android")]
fn enumerate_input_devices() -> Vec<AudioDevice> {
    // O Android expoe uma rota de captura padrao, nao uma lista confiavel de
    // nomes selecionaveis. A captura resolve essa rota novamente depois que a
    // permissao de microfone ja foi concedida.
    vec![AudioDevice {
        name: "Microfone padrao do Android".into(),
        is_default: true,
        default_sample_rate: 0,
        channels: 0,
    }]
}

pub fn run(paths: &AppPaths) -> AppResult<SystemDiagnostics> {
    let mut sys = System::new_all();
    sys.refresh_memory();
    sys.refresh_cpu_all();

    let cpu_name = sys
        .cpus()
        .first()
        .map(|c| c.brand().trim().to_string())
        .unwrap_or_else(|| "desconhecido".into());
    let cpu_cores_logical = sys.cpus().len();
    let cpu_cores_physical = sys.physical_core_count().unwrap_or(cpu_cores_logical);

    let total_ram_gb = bytes_to_gb(sys.total_memory());
    let available_ram_gb = bytes_to_gb(sys.available_memory());
    let data_dir_free_gb = bytes_to_gb(free_space_bytes(&paths.data_dir));

    let os_version = format!(
        "{} {}",
        System::name().unwrap_or_else(|| "Windows".into()),
        System::os_version().unwrap_or_default()
    );

    let input_devices = enumerate_input_devices();

    let mut checks = Vec::new();

    // CPU: o processamento e pesado; abaixo de 4 nucleos fisicos fica lento.
    checks.push(DiagnosticCheck {
        id: "cpu".into(),
        label: "Processador".into(),
        status: if cpu_cores_physical >= 4 {
            CheckStatus::Ok
        } else if cpu_cores_physical >= 2 {
            CheckStatus::Warn
        } else {
            CheckStatus::Fail
        },
        detail: format!("{cpu_cores_physical} nucleos fisicos"),
    });

    // RAM: llama.cpp (Qwen3 4B Q4) + whisper large-turbo pedem folga.
    checks.push(DiagnosticCheck {
        id: "ram".into(),
        label: "Memoria".into(),
        status: if total_ram_gb >= 8.0 {
            CheckStatus::Ok
        } else if total_ram_gb >= 6.0 {
            CheckStatus::Warn
        } else {
            CheckStatus::Fail
        },
        detail: format!("{total_ram_gb:.1} GB no total"),
    });

    // Disco: modelos (~4 GB) + audio de reunioes longas.
    checks.push(DiagnosticCheck {
        id: "disk".into(),
        label: "Espaco em disco".into(),
        status: if data_dir_free_gb >= 12.0 {
            CheckStatus::Ok
        } else if data_dir_free_gb >= 6.0 {
            CheckStatus::Warn
        } else {
            CheckStatus::Fail
        },
        detail: format!("{data_dir_free_gb:.1} GB livres em {}", paths.data_dir.display()),
    });

    // Microfone.
    checks.push(DiagnosticCheck {
        id: "microphone".into(),
        label: "Microfone".into(),
        status: if input_devices.iter().any(|d| d.is_default) {
            CheckStatus::Ok
        } else if !input_devices.is_empty() {
            CheckStatus::Warn
        } else {
            CheckStatus::Fail
        },
        detail: match input_devices.len() {
            0 => "nenhum dispositivo de entrada".into(),
            n => format!("{n} dispositivo(s) detectado(s)"),
        },
    });

    Ok(SystemDiagnostics {
        cpu_name,
        cpu_cores_physical,
        cpu_cores_logical,
        total_ram_gb,
        available_ram_gb,
        data_dir_free_gb,
        input_devices,
        os_version,
        checks,
    })
}
