# Prompts de Mejora — WoolPortals v0.2.3

Este documento contiene tareas de mejora derivadas de la auditoría de código del proyecto WoolPortals. Cada sección es un prompt autocontenido listo para pasar a una IA. Incluye una fase de análisis previo obligatoria antes de modificar código.

Las tareas están ordenadas por prioridad de implementación: de más inocuas (bajo riesgo, sin dependencias) a más estructurales. Seguir este orden minimiza conflictos y retrabajo entre sesiones.

**Importante:** Este plugin es nuevo, no está en producción, no tiene datos reales que migrar. Cualquier cambio rompedor en el formato de `portals.yml` o `config.yml` es aceptable. No es necesario mantener compatibilidad hacia atrás con datos antiguos.

---

## Reglas generales para todas las tareas

1. **Tómate tu tiempo, no hay prisa.** Leer todo el código relevante antes de tocar una sola línea. Entender qué hace cada clase y cómo se relacionan entre sí.
2. **Prioriza calidad sobre velocidad.** No tomar atajos. Si una solución requiere refactorizar más archivos de los esperados, hazlo. El resultado debe ser mantenible a largo plazo.
3. **No asumas nada, compruébalo todo.** Cada afirmación debe verificarse contra el código fuente real. Si el análisis previo contradice lo descrito en este prompt, actualiza el enfoque y explica por qué.
4. **Flujo de pruebas QA obligatorio.** Al terminar una tarea:
   - Compila el plugin y copia el JAR a la carpeta `plugins/` del servidor de pruebas (`~/Desktop/servamc/`).
   - Redacta un plan de pruebas paso a paso dirigido a un QA humano, cubriendo los escenarios impactados por la tarea (creación, enlace, destrucción, reparación, renombrado, comandos, etc. según aplique).
   - El QA ejecuta las pruebas, reporta resultados y cualquier comportamiento inesperado.
   - Si hay bugs encontrados durante las pruebas, evalúa si son pre-existentes o introducidos por la tarea:
     - **Bugs introducidos:** corrígelos en el momento.
     - **Bugs pre-existentes no cubiertos por el documento:** agrégalos como nuevas tareas al final del documento.
   - Solo cuando las pruebas pasan satisfactoriamente se marca la tarea como `COMPLETADO` y se reescribe su sección con un resumen conciso (sin el análisis detallado, para no generar ruido en futuras sesiones).

---

## 1. ~~BAJO — Unificar métodos duplicados de búsqueda y actualización de letreros (DRY)~~ COMPLETADO

Eliminados 4 métodos duplicados (`PortalManager.findAndSetSign`, `PortalListener.updateSignAt`, `PortalListener.findSignAtLocAndSet`, `PortalListener.hasSignNear`). Unificados en 3 métodos centralizados en `PortalManager`: `findSignNear` (privado, búsqueda), `hasSignNear` (público, solo consulta), `updateSignAtWool` (público, escritura ON/OFF). Los 16 callers fueron actualizados. Probado en servidor: creación, enlace, destrucción y reparación de portales funcionan correctamente.

---

## 2. ~~CRÍTICO — Comparación de dueño con `equals` en vez de `equalsIgnoreCase`~~ COMPLETADO

Corregido: se cambió `equals` por `equalsIgnoreCase` en la comparación de dueño de `removePortalAtSign`. Además, se eliminaron por completo los checks de permiso `woolportals.destroy` y de dueño en `removePortalAtSign`, ya que la protección de bloques la maneja un plugin de claims externo. El permiso `woolportals.destroy` fue eliminado de `plugin.yml`.

---

## 3. ~~BAJO — Código muerto: eliminar `isPortalTooClose`~~ COMPLETADO

Eliminado el método `isPortalTooClose()` de `PortalManager.java`. Era código muerto sin llamadas en todo el proyecto. Probado en servidor: creación, enlace, teletransporte, destrucción, reparación, renombrado, comandos y reinicio funcionan correctamente.

---

## 4. ~~MEDIO — Try-catch robusto en `loadPortals()` para YAML malformado~~ COMPLETADO

Refactorizado `loadPortals()` en `PortalManager.java` con validaciones defensivas: (1) `instanceof List<?>` en el casteo exterior de `portals`, (2) `instanceof Map<?,?>` para cada elemento del bucle, (3) try-catch envolviendo cada portal individual con `continue` + warning en fallo, (4) `instanceof Number` con `((Number) obj).intValue()` para coordenadas en vez de casteo directo a `Integer`, (5) try-catch en `BlockFace.valueOf` con fallback a `NORTH` + warning. Se añadieron dos helpers privados: `toInt(Object)` y `parseFacing(String)`. Probado en servidor: portales válidos cargan normalmente; entradas corruptas se saltan con warning en consola sin crashear el plugin.

---

## 5. ~~ALTO — Refactorizar `Portal` en modelo con `PortalSide` independiente~~ COMPLETADO

Creada la clase `PortalSide` que encapsula todos los datos de un lado de portal: `worldName`, coordenadas, `ownerName`, `disabled`, `transient facing`, `transient cachedLocation`, `createdAt`. Implementa `init()`, `getSignLocation()`, `getButtonLocation()`, `getExitLocation()`, `isUsable()`, `clear()`, `yawFromFacing()` estatico, y serializacion `toMap()`/`fromMap()` con `facing` manejado manualmente por ser transient.

`Portal.java` simplificado: eliminados los ~14 campos duplicados A/B y los ~25 metodos con sufijo A/B. Ahora contiene solo `pairId`, `name`, `woolColor`, `sideA` (PortalSide), `sideB` (PortalSide) con metodos `getSideA()`, `getSideB()`, `hasSideA()`, `hasSideB()`, `isComplete()`, `isButtonForThisPortal()`, `getOrCreateA/B()`.

`PortalManager.java`: todos los metodos que recibian `boolean portalB` (`validateAndCreatePortal`, `removePortalAtSign`, `teleportPlayer`, `reassignPortal`, `isFrameIntact`, `isPlayerInsidePortal`, `disablePortal`, `savePortals`, `loadPortals`) pasaron a recibir o devolver `PortalSide`. Eliminado metodo `toInt` duplicado (ahora en PortalSide). `reassignPortal` recibe `(portal, mySide, orphanSide, ...)` explicito.

`PortalListener.java` y `WoolPortalsCommand.java` actualizados a la nueva API. En `WoolPortalsCommand`, `getMyLocation`/`getOtherLocation` usan `portal.getSideA()/B()`.

Probado en servidor: creacion, enlace, teletransporte, destruccion por rotura de letrero y lana, reparacion, persistencia en disco, `/wp list`, `/wp reload`, colocacion de boton para activar, y limite de portales — todo funciona igual que antes.

---

## 6. CRÍTICO — Migrar de player name a UUID como identificador de dueño

### Hallazgo

Actualmente el dueño de un portal se almacena como `String` usando `player.getName()`. Si un jugador cambia su nombre de Minecraft, **pierde el acceso a todos sus portales**, ya que el nombre antiguo queda grabado en disco y el nuevo nombre no coincide.

El estándar en plugins Bukkit/Paper modernos es usar `UUID` (`player.getUniqueId()`), inmutable y que sobrevive a cambios de nombre.

**Nota importante:** Este plugin no está en producción. No hay portales legacy que migrar. Podemos cambiar el formato de `portals.yml` sin restricciones de compatibilidad. Esto simplifica enormemente la tarea: no se necesita lógica de fallback ni migración gradual.

### Archivos implicados

- `PortalSide.java` (creada en tarea #5) — añadir campo `UUID ownerUuid`
- `PortalManager.java` — `validateAndCreatePortal`, `removePortalAtSign`, `countPortalsOwnedBy`, `savePortals`, `loadPortals`, `reassignPortal`
- `PortalListener.java` — `onSignChange`, `handleEdit`, `handleButtonPlace`
- `WoolPortalsCommand.java` — `handleList`, `getMyLocation`, `getOtherLocation`
- `portals.yml` — nuevo formato con `owner-uuid`

### Análisis previo obligatorio

1. Rastrear cada uso de `getOwnerName()` (o equivalente tras tarea #5) en todo el proyecto. Clasificar en lecturas, escrituras y comparaciones.
2. Identificar qué comparaciones deben usar UUID (propiedad, permisos) y cuáles solo muestran el nombre (comandos, mensajes).
3. Dado que no hay datos legacy, el nuevo `portals.yml` usará directamente `owner-uuid` (string del UUID) y `owner-name` (string para display). Sin lógica de migración.
4. Verificar que `Bukkit.getOfflinePlayer(uuid).getName()` funciona para resolver UUID → nombre en mensajes al usuario.
5. Confirmar que `player.getUniqueId()` está disponible en Paper 1.21.4 y es confiable.

### Solución propuesta

- Añadir campo `UUID ownerUuid` en `PortalSide` (además del existente `ownerName` para display).
- En `savePortals`: guardar `owner-uuid` (como string) y `owner-name`.
- En `loadPortals`: leer ambos campos. Si falta `owner-uuid` (archivo muy antiguo), simplemente dejar null y loguear warning.
- Actualizar todas las comparaciones de propiedad: usar `player.getUniqueId().equals(side.getOwnerUuid())`.
- En mensajes al usuario, mostrar `ownerName` (resuelto o almacenado).
- El letrero sigue mostrando `#nombre` (el nombre, no el UUID) para que sea legible.

---

## 7. ALTO — `getPortalAtButton()` es O(n) lineal sin índice espacial

### Hallazgo

Cada vez que un jugador pulsa un botón, `PortalManager.getPortalAtButton()` (línea 171) itera TODOS los portales llamando a `portal.isButtonForThisPortal()`, que a su vez escanea vecinos. Con un `HashMap<Location, PortalSide>` se resolvería en O(1).

Ahora que el modelo usa `PortalSide` (tarea #5), el índice puede apuntar directamente al lado correcto.

### Archivos implicados

- `PortalManager.java` — `getPortalAtButton`, `validateAndCreatePortal`, `reassignPortal`, `removePortalAtSign`, `disablePortal`
- `PortalSide.java` — `getButtonLocation`

### Análisis previo obligatorio

1. Identificar todos los lugares donde un portal se crea, modifica o destruye. En cada uno, mantener el índice actualizado.
2. **Riesgo: `Location.equals()` de Bukkit incluye yaw/pitch.** Si dos instancias de `Location` para el mismo bloque difieren en yaw/pitch, `HashMap.get()` falla silenciosamente. **No usar `Location` directamente como clave.** Usar una key propia basada en world+coordenadas:
   ```java
   private String blockKey(Location loc) {
       return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
   }
   ```
3. **Riesgo: índice estancado al romper un botón.** `onBlockBreak` no maneja rotura de botones. Si se rompe un botón y no se reemplaza, la entrada queda en el índice como basura (aunque no causa bugs porque `onPlayerInteract` verifica que el bloque sea BUTTON antes de consultar el índice). Añadir limpieza en `rebuildButtonIndex()`.
4. El método `handleButtonPlace` ya revalida portales al colocar un botón — el índice debe actualizarse ahí.
5. Invalidar entradas del índice en `PortalSide.clear()` y `PortalSide.setDisabled(true)`.
6. Crear método `rebuildButtonIndex()` llamado tras `loadPortals()` y `/wp reload`.

### Solución propuesta

- Añadir `Map<String, PortalSide> buttonIndex = new ConcurrentHashMap<>()` en `PortalManager`, donde la key es `blockKey(Location)`.
- Al crear/enlazar/reparar un portal, registrar la entrada para ambos lados.
- En `getPortalAtButton`, hacer `buttonIndex.get(blockKey(clickedBlock.getLocation()))`.
- Al destruir/desactivar un portal, eliminar sus entradas del índice.
- Crear `rebuildButtonIndex()` que itere todos los portales y reconstruya el mapa.
- En `rebuildButtonIndex()`, validar que cada ubicación de botón siga siendo efectivamente un botón; si no lo es, no indexarla y loguear warning (portal huérfano).

---

## 8. ALTO — El mapa de cooldowns crece sin control (memory leak)

### Hallazgo

`PortalManager.java:25-31` define `cooldowns` como `ConcurrentHashMap<UUID, Long>`. Cada uso de portal inserta o actualiza una entrada (línea 217). Este mapa **nunca se limpia**. Las entradas caducadas se acumulan indefinidamente.

### Archivos implicados

- `PortalManager.java` — campo `cooldowns`, uso en `teleportPlayer`, constructor

### Análisis previo obligatorio

1. Inspeccionar todos los puntos donde se escribe en `cooldowns`. Confirmar que solo se añade/actualiza y nunca se elimina.
2. Evaluar opciones de limpieza. La más simple y sin dependencias externas: un scheduler periódico que elimine entradas expiradas.
3. Si `cooldownSeconds` es 0, el cooldown está desactivado y `teleportPlayer` no inserta entradas. Pero si se cambió de 3 a 0 en caliente (tras implementar tarea #10), pueden quedar entradas huérfanas. El scheduler debe manejar threshold=0 eliminando todas las entradas.
4. Determinar frecuencia de limpieza: cada 12000 ticks (10 min) es razonable.
5. El scheduler de limpieza debe cancelarse en `onDisable`. Guardar la referencia en un campo `private BukkitTask cooldownCleanupTask;` en `PortalManager`.

### Solución propuesta

- Añadir campo `private BukkitTask cooldownCleanupTask;` en `PortalManager`.
- En el constructor, programar tarea repetitiva asíncrona (`runTaskTimerAsynchronously`):
  ```java
  this.cooldownCleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
      long now = System.currentTimeMillis();
      long threshold = config.getCooldownSeconds() * 1000L;
      cooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= threshold);
  }, 12000L, 12000L);
  ```
- Exponer un método `cancelCooldownCleanup()` llamado desde `onDisable()`.
- La tarea #10 (`/wp reload`) deberá cancelar y reprogramar este scheduler si cambia `cooldown-seconds`.

---

## 9. MEDIO — `savePortals()` es síncrono en el thread principal

### Hallazgo

El scheduler de auto-save (`PortalManager.java:34`) ejecuta `this::savePortals` en el thread principal cada 10 minutos. Con muchos portales, la escritura a disco puede causar micro-lag.

**Riesgo crítico detectado en revisión:** Si el auto-save se mueve a asíncrono, y un admin ejecuta `/wp reload` (que llama a `savePortals()` + `loadPortals()`), **dos hilos escribirían `portals.yml` simultáneamente**, corrompiendo el archivo. El `synchronized` simple no resuelve esto porque el hilo principal se bloquearía esperando al hilo asíncrono, causando el mismo tick lag que se quiere evitar.

**Enfoque correcto:** Todas las escrituras a disco deben ir por un único punto asíncrono. Si `/wp reload` necesita guardar-y-recargar, debe encolar la operación de forma segura.

### Archivos implicados

- `PortalManager.java:34-35` — scheduler
- `PortalManager.java:499-543` — método `savePortals`
- `WoolPortals.java:29-31` — `onDisable` también llama a `savePortals`
- `WoolPortalsCommand.java:139-147` — `handleReload`

### Análisis previo obligatorio

1. Confirmar que `YamlConfiguration.save(File)` es thread-safe cuando cada llamada crea su propia instancia de `YamlConfiguration` (el código actual lo hace). La respuesta es sí: no comparte estado mutable entre instancias.
2. `portals` es `ConcurrentHashMap`, iterable desde cualquier thread sin `ConcurrentModificationException`.
3. `onDisable()` no puede usar tareas asíncronas (scheduler ya detenido). El guardado en `onDisable` DEBE ser síncrono.
4. Diseñar mecanismo anti-concurrencia para el resto de casos:
   - Usar un `AtomicBoolean saving = new AtomicBoolean(false)` como flag.
   - Si una operación de guardado está en curso, la siguiente espera o se encola.
   - La forma más limpia: wrapper `savePortalsAsync(Runnable callback)` que ejecuta el guardado en async y llama al callback al terminar. `/wp reload` usaría `savePortalsAsync(() -> { loadPortals(); /* notificar */ })`.
5. Evaluar si la simplicidad de un solo flag `saving` + saltar si ya está guardando es suficiente para este plugin. Probablemente sí, dado que el auto-save es cada 10 minutos.

### Solución propuesta

- Cambiar el scheduler de auto-save a `runTaskTimerAsynchronously`.
- Añadir `AtomicBoolean saving` como guarda contra escritura concurrente.
- `savePortals()` verifica `if (!saving.compareAndSet(false, true)) return;` al inicio y `saving.set(false)` en finally.
- `onDisable()` llama a `savePortals()` directamente (síncrono, no hay riesgo de concurrencia en shutdown).
- `handleReload()` (tarea #10) deberá guardar de forma segura antes de recargar.

---

## 10. CRÍTICO — `/wp reload` no recarga la configuración

### Hallazgo

El comando `/wp reload` (`WoolPortalsCommand.java:139-147`) ejecuta `portalManager.savePortals()` + `portalManager.loadPortals()`. Esto solo recarga los portales. Los valores de `config.yml` fueron leídos UNA sola vez en `ConfigManager.load()` durante `onEnable()`. Si un admin edita `config.yml` y ejecuta `/wp reload`, los cambios no surten efecto.

### Archivos implicados

- `WoolPortalsCommand.java` — método `handleReload`, constructor
- `ConfigManager.java` — método `load`
- `WoolPortals.java` — `onEnable`
- `PortalManager.java` — scheduler de auto-save, scheduler de cooldowns (tarea #8)

### Análisis previo obligatorio

1. Leer `ConfigManager.load()` línea por línea. Identificar qué valores se cargan y dónde se guardan (campos de instancia).
2. **Riesgo detectado en revisión:** El constructor de `WoolPortalsCommand` recibe `WoolPortals plugin` pero **no lo almacena**:
   ```java
   public WoolPortalsCommand(WoolPortals plugin, PortalManager portalManager) {
       this.portalManager = portalManager;
   }
   ```
   Para llamar a `configManager.load()` desde `handleReload()`, necesita acceso al `ConfigManager`. Hay que almacenar el `plugin` o pasar `ConfigManager` directamente.
3. Si cambia `auto-save-interval-ticks`, hay que cancelar y reprogramar el scheduler de auto-save.
4. Si cambia `cooldown-seconds`, hay que cancelar y reprogramar el scheduler de limpieza de cooldowns (tarea #8).
5. `plugin.reloadConfig()` recarga `config.yml` desde disco. Verificar que funciona en Paper 1.21.4.
6. Evaluar si `ConfigManager` necesita un método `reload()` separado de `load()` (ej: `load()` se llama en `onEnable` por primera vez; `reload()` se llama desde `/wp reload`). Pueden ser el mismo método si no hay diferencias.

### Solución propuesta

- Almacenar `plugin` en `WoolPortalsCommand` para acceder a `plugin.getConfigManager()`.
- En `handleReload()`:
  1. Guardar valores anteriores de `auto-save-interval-ticks` y `cooldown-seconds`.
  2. Llamar a `configManager.load()` para recargar `config.yml`.
  3. Si `auto-save-interval-ticks` cambió, cancelar y reprogramar el auto-save.
  4. Si `cooldown-seconds` cambió, cancelar y reprogramar la limpieza de cooldowns.
  5. Guardar portales y recargarlos de disco.
- Exponer métodos `rescheduleAutoSave(int newInterval)` y `rescheduleCooldownCleanup()` en `PortalManager`.
- Tras implementar la tarea #9, la recarga de portales usará el mecanismo de guardado asíncrono seguro.

---

## 11. BAJO — Añadir tests unitarios

### Hallazgo

El proyecto tiene **cero tests**. No hay carpeta `src/test` ni dependencias de testing en `pom.xml`. Para este punto ya se han completado todas las refactorizaciones estructurales, así que los tests se escriben sobre la versión final del código. La lógica más crítica y testeable incluye:

- Detección de marco de lana (`tryDetectFrame`)
- Validación de letrero (`validateAndCreatePortal` — la parte de validación pura)
- Serialización/deserialización de portales (`savePortals`/`loadPortals`)
- Lógica de enlazado (portal A + portal B = linked)
- Cálculo de posición de salida (`getExitLocation`, `yawFromFacing`)
- La clase `PortalSide` y su interacción con `Portal`

### Archivos implicados

- `pom.xml` — añadir dependencias de test
- Nueva carpeta `src/test/java/com/hakunamatata/woolportals/`
- `PortalManager.java` — `tryDetectFrame`, lógica de creación
- `PortalSide.java` — `getExitLocation`, `yawFromFacing`, `equals`/`hashCode`
- `Portal.java` — `equals`/`hashCode`

### Análisis previo obligatorio

1. Revisar qué motor de tests usa el ecosistema Bukkit/Paper. Opciones:
   - **JUnit 5 + MockBukkit** — mockea el servidor sin necesitar una instancia real.
   - **JUnit 5 + Paperweight Test API** — si se usa el plugin `paperweight-userdev`.
2. **Verificar compatibilidad:** Confirmar que MockBukkit tiene soporte para Paper 1.21.4. Si no, evaluar Paperweight Test API como alternativa.
3. Determinar qué métodos son **funciones puras** (sin dependencia del estado del servidor) y empezar por ellos:
   - `PortalSide.yawFromFacing()`
   - `PortalSide.equals()` y `PortalSide.hashCode()`
   - Lógica de creación de `pairId`
4. **Requisito obligatorio:** `tryDetectFrame` es `private` en `PortalManager` y no se puede testear directamente. **Extraerlo a una clase separada `FrameDetector`** con métodos públicos (o package-private) que tomen `Block` y `Material` como parámetros. Esto también mejora el diseño al separar la responsabilidad de detección de marcos.
5. Determinar qué métodos necesitan mock de Bukkit (World, Location, Block, Material, Sign) y si MockBukkit los proporciona.
6. Los tests deben correr con `mvn test`.

### Solución propuesta

- Añadir JUnit 5 y MockBukkit como dependencias de test en `pom.xml`.
- **Extraer `FrameDetector`** como clase pública con métodos como `detectFrame(Block signBlock, BlockFace facing, Material woolType): List<Block>` y `findButtonInside(Block signBlock, BlockFace facing): Block`.
- Crear tests para:
  - `FrameDetector` — detección de marcos válidos e inválidos (requiere mock de World y Block).
  - `PortalSide` — construcción, equals/hashCode, `yawFromFacing`, `isUsable`, `toMap`/`fromMap`.
  - `Portal` — construcción, `isComplete`, `equals`/`hashCode`.
  - `ConfigManager` — parsing de sonidos y partículas válidos e inválidos.
- No aspirar a 100% de cobertura; apuntar a cubrir la lógica de negocio crítica.

---

## 12. BAJO — Mensaje "marco dañado" falso cuando el portal ya está en OFF

### Hallazgo

En `PortalListener.onBlockBreak` (líneas 219-262), cuando un jugador rompe un bloque de lana de un portal que ya está desactivado (`disabledA=true` o `disabledB=true`), el código ejecuta `isFrameIntact`, detecta que el marco está roto, llama a `disablePortal`, y muestra el mensaje rojo "Marco del portal dañado. Portal desactivado (OFF)." aunque el portal ya estaba en OFF. El mensaje es redundante y confunde al jugador.

### Archivos implicados

- `PortalListener.java:240-246, 251-257`

### Análisis previo obligatorio

1. En `onBlockBreak`, verificar que el bloque roto pertenece al plano de un portal (OK, `isBlockOnPortalPlane` ya lo hace).
2. Antes de ejecutar `disablePortal`, añadir un guard: si el lado ya está `disabled`, no hacer nada ni mostrar mensaje.
3. Distinguir si es lado A o B para acceder a `portal.isDisabledA()` / `portal.isDisabledB()`.

### Solución propuesta

- Antes de `if (!portalManager.isFrameIntact(p, false))`, verificar `if (portal.isDisabledA()) return;`.
- Antes de `if (!portalManager.isFrameIntact(p, true))`, verificar `if (portal.isDisabledB()) return;`.

---

## 13. BAJO — Botón de portal huérfano no responde (sin mensaje de error)

### Hallazgo

Cuando un portal enlazado se renombra (vía `reassignPortal`), el lado que NO se renombró queda huérfano: su letrero existe, su marco está intacto, pero ya no pertenece a ningún `Portal` en el mapa `portals`. Al presionar su botón, `getPortalAtButton` no encuentra nada (esa ubicación de botón ya no está asociada a ningún portal) y el evento `onPlayerInteract` simplemente retorna sin hacer nada. El jugador no recibe ningún mensaje.

### Archivos implicados

- `PortalListener.java:196-198` — `onPlayerInteract` retorna silenciosamente cuando `getPortalAtButton` devuelve null.
- `PortalManager.java:633-697` — `reassignPortal` deja el lado huérfano fuera del mapa.

### Análisis previo obligatorio

1. Tras `reassignPortal`, el lado huérfano sigue teniendo un letrero con `#nombre` y un nombre de portal, pero ese `pairId` no existe en `portals`. El marco y el letrero están intactos.
2. Opciones para manejar el lado huérfano:
   - **Opción A:** En `onBlockBreak`, si se rompe el letrero de un portal huérfano, detectarlo y limpiarlo (mostrar mensaje adecuado).
   - **Opción B:** En `reassignPortal`, marcar el letrero del huérfano con algo como "ORPHAN" en línea 4 para que el jugador sepa que debe reconstruirlo.
   - **Opción C:** En `handleButtonPlace` (que escanea letreros cercanos al colocar un botón), detectar el letrero huérfano y crear un nuevo portal unilateral con él.
3. La opción C es la más completa: un marco huérfano con letrero válido debería poder reinsertarse en el sistema como un portal nuevo (estado CREATED) al colocar un botón. Pero actualmente `handleButtonPlace` llama a `validateAndCreatePortal` que detecta el `pairId` antiguo (que ya no existe), por lo que sí debería crear un nuevo portal unilateral. **Verificar por qué no funciona:** probablemente el letrero del huérfano usa el nombre antiguo (ej: "casa"), y `reassignPortal` creó un nuevo `pairId` con el nombre nuevo (ej: "casita_BLACK_WOOL"). El huérfano tendría `pairId = "casa_BLACK_WOOL"` que ya no existe. `validateAndCreatePortal` con `portalName = "casa"` + `woolColor` generaría `pairId = "casa_BLACK_WOOL"`, que es el original. Debería funcionar. **Hay que debuggear por qué no lo detecta.**
4. El problema real puede ser que `handleButtonPlace` escanea todos los letreros cercanos pero `validateAndCreatePortal` requiere que el letrero tenga el formato `#nombredeusuario` en línea 1. Si el letrero huérfano fue escrito por otro jugador o tiene el nombre del dueño original, debería funcionar. **Testear esto.**

### Solución propuesta

- **Inmediata (mínimo):** En `onPlayerInteract`, si `getPortalAtButton` retorna null, verificar si el bloque clickeado es un botón y si hay un letrero con formato `#usuario` cerca; si lo hay pero no hay portal asociado, mostrar mensaje: "No hay un portal activo aquí. Rompe el letrero y vuelve a crearlo."
- **Completa:** Tras `reassignPortal`, si el lado huérfano tiene marco intacto + letrero, crear automáticamente un nuevo `Portal` unilateral con ese lado (reinsertarlo en `portals`). Esto haría que el botón del huérfano vuelva a funcionar como portal independiente.

---

## 14. BAJO — Mensaje engañoso "Portal destruido" cuando solo se desactiva un lado

### Hallazgo

En `PortalManager.removePortalAtSign`, cuando se rompe el letrero de un portal enlazado, el mensaje final "Portal 'X' destruido" (línea 314) se muestra siempre que `hasOther` es true, sin importar que el par siga existiendo (solo se desactivó un lado, el otro sigue en `/wp list`). El mensaje correcto sería "Lado del portal 'X' destruido. El otro lado sigue activo." o similar.

### Archivos implicados

- `PortalManager.java:314`

### Análisis previo obligatorio

1. Trazar el flujo de `removePortalAtSign`: si `hasOther` es true, se desactiva el lado roto (disabled=true + clear) pero el par NO se elimina del mapa. El mensaje "destruido" es incorrecto.
2. El mensaje "eliminado completamente" (línea 295) sí es correcto: se muestra cuando ningún lado queda activo y el par se borra del mapa.
3. La corrección es trivial: cambiar el texto de un mensaje y posiblemente añadir contexto sobre qué pasó con el otro lado.

### Solución propuesta

- Cambiar línea 314: `destroyer.sendMessage(ChatColor.GREEN + "Lado del portal '" + portal.getName() + "' destruido. El otro lado sigue en OFF.");`

---

## 15. BAJO — Renombrar un portal de vuelta a su nombre original no re-enlaza

### Hallazgo

Si un portal enlazado se renombra de "A" a "B" y luego de "B" a "A", el re-enlace no ocurre. Ambos portales quedan en OFF como portales independientes. Esto sucede porque `reassignPortal` extrae el lado renombrado del `Portal` original y crea uno nuevo con el `pairId` nuevo. El lado que quedó atrás (huérfano) fue deshabilitado y su referencia se perdió. Al volver a renombrar a "A", `reassignPortal` busca `pairId = "A_BLACK_WOOL"` en `portals`, pero ese `pairId` ya no existe (fue eliminado en la primera renombrada cuando se hizo `portals.remove(oldPairId)`). El huérfano quedó fuera del mapa para siempre.

### Archivos implicados

- `PortalManager.java:633-697` — `reassignPortal`

### Análisis previo obligatorio

1. En la primera renombrada (A→B):
   - `oldPairId = "A_BLACK_WOOL"` se elimina del mapa (`portals.remove`).
   - El lado no renombrado (huérfano) queda referenciado en `orphanLoc` pero solo se usa para poner su letrero en OFF.
   - El `Portal` original se desecha.
   - Se crea un nuevo `Portal` con `pairId = "B_BLACK_WOOL"` que contiene solo el lado renombrado.
2. En la segunda renombrada (B→A):
   - `oldPairId = "B_BLACK_WOOL"` se elimina.
   - Se busca `newPairId = "A_BLACK_WOOL"` en `portals`, pero no existe.
   - El huérfano sigue ahí físicamente pero nunca se reinserta.
3. **Posible solución:** En `reassignPortal`, antes de desechar el `Portal` original, verificar si el lado huérfano (`orphanLoc`) tiene marco intacto y letrero válido. Si es así, en lugar de abandonarlo, crear un nuevo `Portal` unilateral para él e insertarlo en `portals` con su `pairId` original. Esto permitiría que futuras renombradas lo encuentren.
4. Alternativa: no eliminar el `oldPairId` del mapa, sino solo limpiar el lado que se va y dejar el huérfano como un portal unilateral con su `pairId` original. Esto es más simple y preserva la capacidad de re-enlace.

### Solución propuesta

- En `reassignPortal`, tras extraer el lado renombrado:
  1. Si el lado huérfano existe (`orphanLoc != null`), NO eliminar `oldPairId` del mapa.
  2. En su lugar, limpiar solo el lado que se va del `Portal` original (ej: si se renombró B, hacer `portal.clearB()` + `portal.setDisabledB(true)`).
  3. El `Portal` original se queda en el mapa con un solo lado (el huérfano), en estado DISABLED.
  4. Si luego alguien renombra de vuelta al nombre original, `targetPair` será el `Portal` original (aún en el mapa) y el re-enlace funcionará.
   5. Poner el letrero del huérfano en OFF (ya se hace).

---

## 16. ALTO — Portales siguen funcionales aunque se rompan los letreros

### Hallazgo

En pruebas de la tarea #2 se descubrió que tras romper **ambos** letreros de un par de portales enlazados, los portales seguían funcionando (teletransporte activo) incluso tras reiniciar el servidor.

El portal usa el letrero solo para display (ON/OFF) y para identificar dueño/nombre en creación. En tiempo de teletransporte, `isComplete()` solo verifica que ambos lados tengan `world != null && !disabled`. El letrero no se verifica. Si ambos letreros se rompen pero `removePortalAtSign` no se ejecuta (o falla), el portal sigue operativo.

### Causa raíz

**Causa 1 (corregida en tarea #2):** Los checks de permiso `woolportals.destroy` y dueño dentro de `removePortalAtSign` impedían que el método corriera si quien rompía el letrero no era el dueño o no tenía el permiso. Esto ya fue eliminado.

**Causa 2 (persistente):** Si el auto-save no se ejecutó entre la rotura de los letreros y el reinicio del servidor, `portals.yml` en disco aún contiene los datos del portal con ambos lados activos. Al reiniciar, el portal se restaura completo desde disco. `removePortalAtSign` limpia los datos en memoria, pero `savePortals()` necesitaría ejecutarse antes del reinicio para persistir el cambio.

### Archivos implicados

- `PortalManager.java:231-316` — `removePortalAtSign`
- `PortalManager.java:510-553` — `savePortals`
- `PortalListener.java:205-248` — `onBlockBreak`

### Análisis previo obligatorio

1. Confirmar que `removePortalAtSign` limpia correctamente en memoria los datos del portal cuando se rompe un letrero (sí, lo hace).
2. Identificar el gap: entre la rotura del letrero y el siguiente auto-save (cada 10 min), si el servidor se reinicia, los datos no persisten. Solución: forzar un `savePortals()` inmediato tras `removePortalAtSign`.
3. Evaluar si conviene un save asíncrono o síncrono. Un save síncrono en cada rotura de letrero es seguro (no hay riesgo de concurrencia porque `onBlockBreak` es en main thread y es poco frecuente), pero la tarea #9 propone migrar todo a asíncrono con `AtomicBoolean`. Coordinar con esa tarea.
4. Evaluar si `removePortalAtSign` debería forzar `savePortals()` o si el caller (`onBlockBreak`) debería hacerlo. Lo más limpio es que `removePortalAtSign` fuerce el save tras modificar datos.

### Solución propuesta

- En `removePortalAtSign`, tras modificar el portal (disable/clear/remove), llamar a `savePortals()` para persistir inmediatamente.
- Si se implementó la tarea #9 (save asíncrono), coordinar para que este save use el mismo mecanismo.

