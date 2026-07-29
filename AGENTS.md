# AGENTS.md — WoolPortals

Plugin de teletransporte con portales de lana para Minecraft Paper/Purpur 1.21.4.

## Mecánica del plugin (qué hace, no cómo)

El plugin permite a los jugadores crear pares de portales de teletransporte usando marcos de lana. La mecánica es:

1. El jugador construye un marco rectangular de lana 3x4 (3 de ancho, 4 de alto) usando 10 bloques del **mismo color** de lana.
2. Coloca un **letrero** (wall sign) en el centro del borde superior.
3. Coloca un **botón** en el interior del marco (cualquiera de las 6 posiciones interiores: columna central, izquierda o derecha, en las filas 2 o 3).
4. Escribe en el letrero:
   - Línea 1: `#nombredeusuario`
   - Línea 2: nombre del portal
   - Línea 3: vacío
   - Línea 4: el plugin escribe `ON` (verde) o `OFF` (gris) automáticamente
5. Construye OTRO portal igual en otra ubicación, usando el **mismo nombre de portal y mismo color de lana**.
6. Al colocarse dentro de uno de los portales y presionar el botón, el jugador se teletransporta al otro.

Dos portales del mismo color y nombre se enlazan automáticamente al detectarse. Si un marco se rompe, el portal asociado se desactiva (OFF). Al repararlo y reescribir el letrero, se reactiva.

El control de acceso a los portales se maneja únicamente por permisos Bukkit. No hay integración con plugins de protección de terreno (GriefPrevention, WorldGuard) — está pendiente como funcionalidad futura.

## Estructura del proyecto

Es un proyecto Maven estándar con Paper API como dependencia.

| Archivo/Clase | Rol |
|---|---|
| `WoolPortals.java` | Punto de entrada del plugin. Inicializa config, carga portales, registra listeners y comandos. |
| `ConfigManager.java` | Lee y expone los valores de `config.yml`: cooldown, sonido, partículas, intervalo de auto-save, máximo de portales por jugador. |
| `Portal.java` | Modelo de datos. Representa un **par** de portales enlazados (lado A y lado B). Cada lado almacena ubicación de la lana, dueño, orientación, y estado (activado/desactivado). |
| `PortalManager.java` | Toda la lógica de negocio: validación de marcos, creación/enlace/reparación de portales, teletransporte, destrucción, persistencia a `portals.yml`, cooldowns. |
| `PortalListener.java` | Eventos de Bukkit: detección al escribir letreros, click en botones, rotura de bloques (desactiva portales dañados), colocación de bloques (reactiva portales reparados). |
| `WoolPortalsCommand.java` | Comandos `/wp list`, `/wp info`, `/wp reload`. |
| `plugin.yml` | Metadatos del plugin. Clase principal, permisos, alias `/wp`. |
| `config.yml` | Configuración por defecto empaquetada en el JAR. |

## Cómo se identifican y enlazan los portales

Cada portal se identifica por un `pairId` compuesto: `nombreDelPortal + "_" + colorDeLana`. Ejemplo: `casa_BLACK_WOOL`, `minas_RED_WOOL`. Cuando dos jugadores construyen portales que generan el mismo `pairId`, el sistema los enlaza automáticamente como lados A y B del mismo par.

## Sistema de permisos

| Permiso | Efecto |
|---|---|
| `woolportals.use` | Usar portales (teletransportarse) |
| `woolportals.create` | Crear portales |
| `woolportals.admin` | Ver/recargar todos los portales |

## Cosas que debes saber antes de tocar el código

1. **El plugin no está en producción aún.** No hay servidores reales usándolo. Cambios rompedores en formatos de archivo son aceptables.
2. **Los dueños se almacenan por nombre, no por UUID.** Si un jugador cambia su nombre de Minecraft, pierde sus portales. Está identificado como una mejora prioritaria.
3. **El modelo `Portal` almacena dos mitades (A/B) en una sola clase** con campos duplicados. Esto hace el código verboso y propenso a errores. Está planeada una refactorización a `PortalSide`.
4. **No hay tests.** Cero cobertura. La detección de marcos (`tryDetectFrame`) es la lógica más compleja y vulnerable a regresiones.
5. **`/wp reload` solo recarga portales, no la configuración.** Los cambios en `config.yml` requieren reinicio del servidor.
6. **La detección de botones y lana usa strings frágiles** (`material.name().contains("BUTTON")`, `.endsWith("_WOOL")`). Funciona pero no es robusto ante cambios de la API de Bukkit.
7. **Hay un documento `prompts/auditoria-pendientes.md`** con 11 tareas de mejora priorizadas: refactorización, migración a UUID, índice de botones, tests, etc. Consúltalo antes de emprender cambios estructurales.
8. **El proyecto compila con Maven y Java 21.** El JAR generado se copia a la carpeta `plugins/` de un servidor Paper/Purpur 1.21.4. No hay wrapper de Maven incluido aún.
