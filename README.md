# Ficha de Inscripción deportiva

Aplicación Android del Laboratorio 08, subgrupo 15. **Inscripciones** reúne socio, tipo de socio, deporte, fecha y monto. Los otros módulos administran los catálogos **Socios**, **Tipos de socio** y **Deportes**. La aplicación usa Kotlin, vistas XML y SQLite local.

## Requisitos

- Git 2.23 o posterior y Android Studio Quail 2 (2026.1.2) o posterior compatible con AGP 9.3.2. Usa el JDK integrado de Android Studio o JDK 17. [Compatibilidad de Android Studio](https://developer.android.com/studio/releases)
- En SDK Manager instala Android SDK Platform 36, Build-Tools 36.0.0, Platform-Tools y Android Emulator. Crea un teléfono virtual con una imagen API 36 recomendada. La app admite Android 8.0 o posterior (API 26+). El proyecto trae el Gradle Wrapper 9.5.0. [Requisitos de AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes)

## Clonar y ejecutar

1. Acepta la invitación al repositorio privado y clónalo con `git clone URL_DEL_REPOSITORIO`, usando la URL de GitHub.
2. En Android Studio, abre la carpeta clonada que contiene `settings.gradle.kts` y espera **Gradle Sync**. Android Studio registra la ruta del SDK de tu equipo en `local.properties`. La primera sincronización necesita conexión para descargar dependencias.
3. En **Device Manager**, inicia el emulador. Selecciona la configuración **app** y pulsa **Run**. Para compilar desde una terminal con el SDK y el JDK configurados, ejecuta `gradlew.bat :app:assembleDebug` en Windows o `./gradlew :app:assembleDebug` en macOS o Linux.

## Datos iniciales y restablecimiento

La primera vez que se crea la base, la app carga [datos_iniciales.json](app/src/main/res/raw/datos_iniciales.json), extraído del emulador del grupo. Incluye **6 socios, 5 tipos de socio, 4 deportes y 2 fichas**. Al volver a abrir la app se usa la base del dispositivo. Los cambios de cada integrante permanecen en su emulador o teléfono.

En **Ver inscripciones**, con el filtro **Todos**, comprueba estas fichas activas.

| Ficha | Socio | Tipo | Deporte | Fecha | Monto |
|---|---|---|---|---|---|
| F0001 | S001 · Ana Torres | 2 · Senior | D001 · Natación | 16/11/2026 | S/ 80,00 |
| F0015 | S015 · Carlos Medina Rojas | 5 · Visitante deportivo | D015 · Atletismo libre | 19/11/2026 | S/ 85,00 |

F0098 fue una ficha temporal del video y se retiró al restaurar la base del emulador. En **Socios**, el filtro **Todos** permite verificar que S004 está inactivo y S005 está eliminado lógicamente.

Para volver a los datos originales, abre **Ajustes → Apps → Inscripción deportiva → Almacenamiento → Borrar almacenamiento** y vuelve a iniciar la app. Esto borra los cambios locales de esa instalación. Si ya tenías una instalación anterior y quieres empezar con la muestra compartida, aplica el mismo restablecimiento. Desde una terminal con un único emulador conectado, el equivalente es `adb shell pm clear pe.unsa.inscripcion`.

Las pruebas instrumentadas reinician SQLite. Si vas a ejecutarlas, usa un emulador de pruebas aparte.

## Colaborar

Parte de `main` actualizada y crea una rama. Después de modificar y probar el código, confirma los archivos de tu cambio y abre un pull request hacia `main`. [Guía de pull requests](https://docs.github.com/es/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request)

```bash
git switch main
git pull --ff-only
git switch -c mejora/nombre-corto
git add RUTA_DEL_ARCHIVO
git commit -m "Describe el cambio"
git push -u origin mejora/nombre-corto
```
