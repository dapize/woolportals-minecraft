# Prompts de Mejora — WoolPortals v0.2.3

Este documento contiene tareas de mejora derivadas de la auditoría de código del proyecto WoolPortals. Cada sección es un prompt autocontenido listo para pasar a una IA. Incluye una fase de análisis previo obligatoria antes de modificar código.

Las tareas están ordenadas por prioridad de implementación: de más inocuas (bajo riesgo, sin dependencias) a más estructurales. Seguir este orden minimiza conflictos y retrabajo entre sesiones.

**Importante:** Este plugin es nuevo, no está en producción, no tiene datos reales que migrar. Cualquier cambio rompedor en el formato de `portals.yml` o `config.yml` es aceptable. No es necesario mantener compatibilidad hacia atrás con datos antiguos.

---

## Reglas generales para todas las tareas

1. **Tómate tu tiempo, no hay prisa.** Leer todo el código relevante antes de tocar una sola línea. Entender qué hace cada clase y cómo se relacionan entre sí.
2. **Prioriza calidad sobre velocidad.** No tomar atajos. Si una solución requiere refactorizar más archivos de los esperados, hazlo. El resultado debe ser mantenible a largo plazo.
3. **No asumas nada, compruébalo todo.** Cada afirmación debe verificarse contra el código fuente real. Si el análisis previo contradice lo descrito en este prompt, actualiza el enfoque y explica por qué.

---

## 1. BAJO — Unificar métodos duplicados de búsqueda y actualización de letreros (DRY)

### Hallazgo

Existen tres métodos que hacen exactamente lo mismo: iterar sobre `BlockFace[]{NORTH, SOUTH, EAST, WEST, UP}` alrededor de una ubicación de lana, buscar si hay un `Sign`, y actualizar su línea 3 con "ON" u "OFF" en verde o gris.

1. `PortalManager.findAndSetSign(Location woolLoc, boolean on)` — línea 318
2. `PortalListener.updateSignAt(Location woolLoc, boolean on)` — línea 157
3. `PortalListener.findSignAtLocAndSet(Location woolLoc, boolean on)` — línea 387

Además hay una cuarta variante `PortalListener.hasSignNear(Location woolLoc)` (línea 376) que solo comprueba existencia sin escribir.

Código copiado y pegado. Si cambia la lógica de búsqueda, hay que modificar 3 o 4 sitios.

**Riesgo detectado en revisión:** `hasSignNear` es de solo consulta. Se usa en `handleWoolPlace` (`PortalListener.java:340`) para verificar si existe un letrero ANTES de intentar reparar el marco. Si se unifica todo en un solo método que siempre escribe en el letrero, se forzaría un "ON"/"OFF" prematuro en el letrero aunque el marco aún no esté listo. **La solución debe mantener separados el método de consulta y el de escritura**, ambos delegando a una lógica de búsqueda compartida privada.

### Archivos implicados

- `PortalManager.java:318-327`
- `PortalListener.java:157-169, 376-399`

### Análisis previo obligatorio

1. Comparar los cuatro métodos línea por línea. Confirmar cuáles son idénticos y en qué difieren.
2. Identificar todas las llamadas a estos métodos (dónde se invocan y con qué parámetros).
3. Diseñar la lógica compartida: un método privado que busque el `Sign` alrededor de la lana y devuelva `Sign` o null. Los métodos públicos serán wrappers:
   - `boolean hasSignNear(Location woolLoc)` — solo consulta, sin efectos secundarios.
   - `void updateSignAtWool(Location woolLoc, boolean on)` — escribe la línea 3.
4. Decidir dónde colocar los métodos unificados.
   - **Opción A:** Método estático en `PortalManager` (es la clase de lógica de negocio).
   - **Opción B:** Nueva clase de utilidad `SignHelper`.
5. Verificar que moverlos no cause dependencias circulares (no debería).
6. Evaluar si también conviene unificar `setSignStatus` (que recibe un `Block` de sign directamente) con el nuevo `updateSignAtWool`, o si son lo suficientemente distintos.

### Solución propuesta (punto de partida, puede cambiar tras el análisis)

- Crear método privado/estático `findSignNear(Location woolLoc): Sign` que contenga la lógica de búsqueda común.
- Crear `hasSignNear(Location): boolean` como wrapper de solo consulta.
- Crear `updateSignAtWool(Location, boolean on): void` como wrapper de escritura.
- Reemplazar todas las llamadas a los 4 métodos antiguos por los nuevos.
- Eliminar los métodos duplicados.

---

## 2. CRÍTICO — Comparación de dueño con `equals` en vez de `equalsIgnoreCase`

### Hallazgo

En `PortalManager.java:267`:
```java
if (!destroyer.getName().equals(owner) && !destroyer.hasPermission("woolportals.admin"))
```
El resto del código usa consistentemente `equalsIgnoreCase` para comparar nombres de jugador. Esta inconsistencia puede impedir que un jugador destruya su propio portal si su nombre tiene capitalización diferente al momento de creación.

### Archivos implicados

- `PortalManager.java:267`

### Análisis previo obligatorio

1. Buscar TODAS las comparaciones de strings que involucren `player.getName()` o `ownerA`/`ownerB` en todo el proyecto. Clasificarlas en "usa equalsIgnoreCase" y "usa equals".
2. Verificar si hay algún caso donde `equals` estricto sea intencionado (no parece).
3. Este cambio es trivial y sin efectos colaterales. La migración a UUID (tarea #6) hará que las comparaciones por nombre desaparezcan, pero corregir esto ahora evita bugs en cualquier versión intermedia del código.

### Solución propuesta

- Cambiar `equals` por `equalsIgnoreCase` en la línea 267 de `PortalManager.java`.

---

## 3. BAJO — Código muerto: eliminar `isPortalTooClose`

### Hallazgo

`PortalManager.java:441-445` define el método `isPortalTooClose()` pero **nunca se invoca** desde ninguna parte del proyecto.

### Archivos implicados

- `PortalManager.java:441-445`

### Análisis previo obligatorio

1. Hacer una búsqueda exhaustiva en todo el proyecto del string `isPortalTooClose` para confirmar que no tiene llamadas.
2. Evaluar si la funcionalidad que pretendía (evitar portales solapados a menos de 5 bloques) es deseable. Si es así, en vez de borrarlo, integrarlo en `validateAndCreatePortal`.
3. Si se decide integrarlo, verificar que la lógica de detección de cercanía compara ubicaciones de lana y añadir un nuevo `CreateStatus.TOO_CLOSE` con su mensaje correspondiente en `PortalListener`.

### Solución propuesta

- Si no se desea la funcionalidad: eliminar el método.
- Si se desea: integrarlo en `validateAndCreatePortal`.

---

## 4. MEDIO — Try-catch robusto en `loadPortals()` para YAML malformado

### Hallazgo

`PortalManager.java:448-497` tiene casteos sin protección:

```java
int x = (Integer) a.get("x");       // ClassCastException si no es Integer
BlockFace.valueOf(facingStr);        // IllegalArgumentException si string inválido
```

Si el archivo `portals.yml` se corrompe (edición manual incorrecta, fallo de disco), el plugin lanza una excepción no capturada y **no arranca** o crashea al hacer `/wp reload`.

**Riesgo detectado en revisión:** El casteo exterior `(List<Map<String, Object>>) config.getList("portals")` (línea 454) está FUERA del bucle. Si `portals.yml` tiene `portals: "texto_cualquiera"`, la `ClassCastException` vuela antes de entrar al `for` y el try-catch propuesto dentro del bucle no lo atraparía. Hay que cubrir también ese casteo externo.

**Confirmación necesaria:** Si un portal se lee a medias (portal A exitoso, portal B falla el casteo), el `continue` impide llegar a `portals.put(...)`, así que no se añade estado inconsistente al mapa. Verificar esto en el código.

### Archivos implicados

- `PortalManager.java:448-497`

### Análisis previo obligatorio

1. Leer el método `loadPortals()` completo. Identificar cada línea con casteo y con `valueOf`.
2. El casteo de la lista exterior requiere su propio chequeo: `instanceof List<?>` y luego validar cada elemento.
3. Para cada elemento del bucle, envolver en try-catch y en caso de fallo loguear warning + `continue`.
4. Para `BlockFace.valueOf`, usar try-catch interno con fallback a `BlockFace.NORTH` + warning.
5. Para casteos numéricos, usar `instanceof Number` con `((Number) obj).intValue()`.

### Solución propuesta

- Validar que `config.getList("portals")` retorna una `List<?>` antes del bucle.
- Iterar cada elemento con `instanceof Map<?,?>` antes de castear.
- Envolver la lectura de cada portal individual en try-catch con `continue`.
- Usar `instanceof Number` para coordenadas en vez de `(Integer)`.
- Usar try-catch para `BlockFace.valueOf` con default `NORTH`.

---

## 5. ALTO — Refactorizar `Portal` en modelo con `PortalSide` independiente

### Hallazgo

La clase `Portal.java` almacena dos mitades (A y B) en una misma instancia con campos duplicados: `worldA`/`worldB`, `xA`/`xB`, `ownerA`/`ownerB`, etc. Cada método de `PortalManager` que trabaja con portales tiene lógica condicional `if (isPortalA) ... else ...`. Esto hace el código más verboso, propenso a errores por confusión de lados, y difícil de extender.

Esta es la tarea más grande del proyecto pero **conviene hacerla ahora**, antes de la migración a UUID (tarea #6) y el índice de botones (tarea #7), porque ambas se beneficiarán de una estructura de datos más limpia y evitarán retrabajo.

### Archivos implicados

- `Portal.java` — todos los campos y métodos
- `PortalManager.java` — prácticamente todos los métodos
- `PortalListener.java` — `handleEdit`, `isPortalB`, `getWoolLoc`
- `WoolPortalsCommand.java` — `handleList`, `getMyLocation`, `getOtherLocation`

### Análisis previo obligatorio

1. Modelar la nueva estructura ANTES de tocar código: `PortalSide` como clase independiente con sus propios campos y métodos. `Portal` contiene dos `PortalSide` (`sideA`, `sideB`), cada una puede ser `null` si ese lado no existe.
2. Identificar qué métodos de `Portal` se mueven a `PortalSide` (ej: `getSignLocation`, `getButtonLocation`, `isUsable`, `isDisabled`).
3. Identificar qué métodos de `PortalManager` se simplifican al delegar en `PortalSide`.
4. Revisar el método `reassignPortal` — es el más complejo y el más propenso a bugs de referencias compartidas (ver riesgos abajo).
5. **Atención al campo `facing`:** En `Portal.java`, `facingA` y `facingB` son `transient`. Esto significa que SnakeYAML no las serializa; en `savePortals()` se guardan manualmente como string. `PortalSide` debe mantener `facing` como `transient` y serializarlo manualmente en `toMap()`/`fromMap()`. Si no se marca transient, SnakeYAML fallará al serializar un `BlockFace`.
6. **Atención a `reassignPortal` y referencias:** Este método mueve lados entre instancias de `Portal`. Con `PortalSide` como objetos, hay peligro de aliasing (dos `Portal` referenciando el mismo `PortalSide`). Decidir explícitamente: ¿se hace deep clone o se transfiere la referencia? Documentarlo.
7. **Atención a lados nulos en `savePortals`:** Si `sideB` es null, `toMap()` no debe ser llamado. Mantener el guard `if (portal.hasPortalB())` o hacer que `toMap()` acepte null.
8. **Atención a `getButtonLocation()` con `facing` null:** Si `facing` es null, el método devuelve null sin advertencia. Considerar loguear un warning en ese caso para facilitar depuración.
9. Evaluar si es mejor usar dos campos `sideA`/`sideB` tipados o una `List<PortalSide>` con máximo 2. Dos campos es más claro y mantiene compatibilidad natural con el YAML.

### Solución propuesta

- Crear clase `PortalSide` con: `String worldName`, `int x, y, z` (o `Location woolLocation`), `String ownerName`, `transient BlockFace facing`, `boolean disabled`, `long createdAt`, `transient Location cachedLocation`.
- `PortalSide` implementa `getSignLocation()`, `getButtonLocation()`, `isUsable()`, etc.
- `PortalSide` implementa `Map<String, Object> toMap()` y un factory estático `PortalSide fromMap(Map<String, Object>)`.
- `Portal` pasa a tener: `String pairId`, `String name`, `String woolColor`, `PortalSide sideA`, `PortalSide sideB`.
- Eliminar TODOS los métodos con sufijo A/B en `Portal`.
- Los métodos de `PortalManager` que reciben `boolean portalB` pasan a recibir `PortalSide`.
- Actualizar `savePortals`/`loadPortals` para delegar serialización a `PortalSide`.
- Actualizar `PortalListener` y `WoolPortalsCommand` para usar la nueva API.

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

