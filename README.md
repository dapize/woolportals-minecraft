# WoolPortals

Plugin de portales de lana para Minecraft Paper/Purpur. Creado para el servidor **Hakuna matata**.

## Cómo funciona

1. Construye un marco de **lana 3x4** (mismo color, 10 bloques)
2. Coloca un **letrero** en el centro del borde superior
3. Coloca un **botón** dentro del portal, en una columna
4. Escribe en el letrero:
   - Línea 1: `@tuusuario`
   - Línea 2: nombre del portal (ej: `casa`)
5. Construye otro portal igual en otra ubicación con el **mismo nombre y color**
6. Párate dentro del portal y presiona el botón para teletransportarte

```
  WLW       W = Lana (mismo color, 10 bloques)
  W W       L = Letrero (centro del borde superior)
  W W       B = Botón (dentro, columna izq. o der.)
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

| Comando | Descripción |
|---------|-------------|
| `/wp list` | Lista todos los portales |
| `/wp info` | Muestra cómo construir un portal |
| `/wp reload` | Recarga la configuración |

## Requisitos

- Paper/Purpur 1.21.4+ (Minecraft 26.2)
- Java 21+

## Instalación

1. Descarga `WoolPortals-0.1.0.jar`
2. Colócalo en la carpeta `plugins/` de tu servidor
3. Reinicia el servidor

## Licencia

MIT
