# WoolPortals

Plugin de portales de lana para Minecraft Paper/Purpur. Creado para el servidor **Hakuna matata**.

## Cómo funciona

1. Construye un marco de **lana 3x4** (mismo color, 10 bloques)
2. Coloca un **letrero** en el centro del borde superior
3. Coloca un **botón** dentro del portal, en una columna
4. Escribe en el letrero:
   - Línea 1: `#tuusuario`
   - Línea 2: nombre del portal (ej: `casa`)
   - Línea 3: `privado` (opcional, solo el dueño puede usarlo)
   - Línea 4: el plugin escribe automáticamente el estado (no tocar)
5. Construye otro portal igual en otra ubicación con el **mismo nombre y color**
6. Párate dentro del portal y presiona el botón para teletransportarte

### Estado del portal (línea 4 automática)

| Color | Texto | Significado |
|:---:|--------|-------------|
| Verde | `ON` | Ambos portales enlazados, listo para usar |
| Gris | `OFF` | Portal desactivado (sin contraparte o letrero roto) |

```
  WLW       W = Lana (mismo color, 10 bloques)
  WBW       L = Letrero (centro del borde superior)
  W W       B = Boton (dentro, columna izq. o der.)
  WWW
```

## Permisos

| Permiso | Descripción | Default |
|---------|-------------|:---:|
| `woolportals.use` | Usar portales | Todos |
| `woolportals.create` | Crear portales | Todos |
| `woolportals.destroy` | Destruir tus portales | Dueño |
| `woolportals.admin` | Administrar todos los portales | OP |

## Comandos

| Comando | Quién | Descripción |
|---------|:---:|-------------|
| `/wp info` | Todos | Muestra cómo construir un portal |
| `/wp list` | Todos | Lista tus propios portales con coordenadas |
| `/wp list all` | Admin | Lista todos los portales del servidor |
| `/wp reload` | Admin | Recarga los portales desde el disco |

## Requisitos

- Paper/Purpur 1.21.4+ (Minecraft 26.2)
- Java 21+

## Instalación

1. Descarga `WoolPortals-0.2.0.jar`
2. Colócalo en la carpeta `plugins/` de tu servidor
3. Reinicia el servidor

## Compilar desde el código fuente

### Requisitos

- **JDK 21 o superior** ([descargar](https://adoptium.net))
- **Maven 3.9+** ([descargar](https://maven.apache.org/download.cgi))

### Compilar

```bash
mvn clean package
```

El archivo `.jar` se genera en `target/WoolPortals-0.2.0.jar`. Cópialo a la carpeta `plugins/` de tu servidor.

### Sin Maven instalado

Si no quieres instalar Maven, puedes generar un wrapper:

```bash
mvn wrapper:wrapper
```

Esto crea `mvnw` (Linux/Mac) y `mvnw.cmd` (Windows). Luego usás:

```bash
./mvnw clean package   # Linux/Mac
mvnw clean package     # Windows
```

## Licencia

MIT
