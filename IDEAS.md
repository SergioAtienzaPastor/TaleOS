# TaleOS — Ideas para las próximas evoluciones

Este documento recoge propuestas para ampliar TaleOS a partir de la versión 0.1.0. Son ideas de diseño, no funciones ya implementadas ni un compromiso de fechas o versiones. Los nombres de objetos, métodos y eventos son propuestas sujetas a las pruebas con la API de Hytale.

## Punto de partida

TaleOS ya dispone de ordenadores, Pocket Computers, Turtle con 16 ranuras, dos mejoras externas y cuatro módulos internos, movimiento y animaciones, inventarios adyacentes, fabricación, almacenamiento USB/NAS, monitores con texto y píxeles, altavoces, red inalámbrica, localización GPS, Chunk Loader y Player Link.

Por tanto, las propuestas siguientes no repiten `turtle.forward`, `gps.locate`, `turtle.suck`, `turtle.drop` o el acceso a cofres: los utilizan como base para crear automatismos de más alto nivel.

## Orden recomendado

| Prioridad | Propuesta | Resultado para el jugador | Dificultad principal |
|---|---|---|---|
| Implementada | Mejora Player Link | Una Turtle entrega objetos directamente a ranuras elegidas del inventario vinculado. | Resuelta: envíos exactos sin intercambios ni duplicados. |
| 1 | Mejora GPS Navigator | Ordenar `goTo(x, y, z)` y dejar que la Turtle calcule y siga la ruta. | Pathfinding, obstáculos, chunks y presupuesto de CPU. |
| 2 | Programas en pestañas | Controlar una Turtle mientras sigue ejecutando navegación u otros programas. | Gestión de procesos, foco de terminal y cancelación. |
| 3 | Monitores interactivos | Botones, paneles y mapas que responden al toque del jugador. | Eventos, coordenadas y dibujo eficiente. |
| 4 | Dock para Turtle | Punto de origen, reabastecimiento, reparación lógica y telemetría. | Integración con navegación e inventarios. |
| 5 | API pública para otros mods | Que otros mods expongan máquinas y funciones como periféricos TaleOS. | Estabilidad, versionado y aislamiento de errores. |
| 6 | Constructor por planos | Guardar una construcción en el NAS y dejar que una Turtle la reproduzca. | Orientación de bloques, materiales y recuperación ante errores. |

La recomendación es continuar con **GPS Navigator** y combinarlo con Player Link en un programa de mensajería logística. Después conviene añadir pestañas para poder controlar esos automatismos sin detenerlos.

---

## 1. Mejora Player Link — implementada

### Concepto

El objeto `TaleOS_Player_Link` puede equiparse en cualquiera de los dos huecos externos o cuatro huecos internos de una Turtle. Expone un periférico `player_link` que permite enviar objetos desde la Turtle al inventario del jugador vinculado, nunca retirarlos.

El jugador vincula el objeto al mantenerlo en la mano y pulsar **F**. La identidad, las rutas y la configuración se guardan en los metadatos del propio objeto y se conservan al moverlo, retirarlo o romper y volver a colocar la Turtle. El enlace no restringe quién puede abrir o programar la Turtle.

Ejemplos de uso:

- Una Turtle minera envía el cobre a la barra rápida del jugador.
- Un robot agrícola entrega la cosecha sin obligar a vaciarlo manualmente.
- Una Turtle de mensajería recoge equipamiento y lo entrega al volver.
- Un programa reserva la ranura 9 para objetos recogidos y evita llenar otras ranuras.

### Vinculación y permisos

La vinculación no puede cambiarse desde Lua; así un programa no puede elegir cualquier jugador ni manipular inventarios ajenos.

Implementación segura:

1. Un jugador vincula el objeto al mantenerlo en la mano y pulsar F, nunca simplemente al equiparlo.
2. El propietario queda registrado con su UUID y su nombre visible.
3. Solo el propietario o un administrador con `taleos.playerlink.admin` puede desvincularlo desde la página de F.
4. Lua puede consultar el propietario y el estado, pero no cambiarlo ni eliminarlo.
5. Solo se permiten envíos al propietario. No existe API para retirar objetos del jugador ni para cambiar su propietario.

### API Lua implementada

| Método | Función |
|---|---|
| `getOwner()` | Devuelve el nombre del propietario vinculado o `nil`. |
| `isAvailable()` | Devuelve `true`, o `false, reason` si el jugador no puede recibir objetos. |
| `setRoute(turtleSlot, playerSlot)` | Configura el destino automático de una ranura de la Turtle. |
| `clearRoute(turtleSlot)` | Elimina una ruta automática. |
| `getRoutes()` | Devuelve una tabla de rutas configuradas. |
| `setDefaultSlot(playerSlot)` | Destino para las ranuras sin ruta específica; `nil` lo elimina. |
| `setAutoTransfer(enabled)` | Activa o detiene el envío automático. |
| `transfer(turtleSlot, playerSlot[, count])` | Devuelve `true, moved` o `false, reason`. |
| `getPlayerSlot(slot)` | Consulta nombre, cantidad y máximo del contenido visible para el propietario. |

Las ranuras Lua empiezan en 1. Primero se numera la barra rápida completa y después la mochila; por tanto, con la barra habitual de 9 huecos, `9` es su noveno hueco y `10` es el primero de la mochila. Los destinos fuera de la capacidad real se rechazan al transferir o consultar.

### Ejemplo: recoger y enviar a la ranura 9

```lua
local link = peripheral.wrap("left")
assert(link and peripheral.getType("left") == "player_link",
  "Equip a Player Link on the left")

-- Turtle slot 1 -> player slot 9.
link.setRoute(1, 9)
link.setAutoTransfer(true)

while true do
  turtle.select(1)
  local ok, reason = turtle.suckDown()
  if not ok then print(reason) end
  os.sleep(0.25)
end
```

También se podría configurar un destino general para todas las ranuras:

```lua
link.setDefaultSlot(9)
link.setAutoTransfer(true)
```

### Comportamiento ante problemas

- Si la ranura de destino está ocupada por otro objeto o no admite el stack, la operación falla sin intercambiar ni mover objetos; nunca se retira el contenido del jugador.
- Si el inventario está lleno, los objetos permanecen en la Turtle.
- Si el jugador está desconectado, en otro mundo o fuera del alcance configurado, la transferencia queda pausada.
- Si una transferencia solo cabe parcialmente, se mueve exactamente la cantidad confirmada y el resto permanece en la Turtle.
- Los eventos `player_link_full` y `player_link_unavailable` tienen límite de frecuencia para no inundar la cola.
- Romper o mover la Turtle no reinicia la vinculación ni las rutas, ya que pertenecen al objeto.

### Configuración del servidor implementada

```json
{
  "player_link": {
    "enabled": true,
    "max_distance": 64,
    "transfers_per_tick": 8
  }
}
```

El propietario debe estar siempre conectado y en el mismo mundo. No se accede al inventario almacenado de jugadores desconectados, evitando problemas de sincronización y duplicación.

### Pruebas imprescindibles

- Inventario lleno, ranura ocupada, apilado parcial y límite de stack.
- Desconexión o cambio de mundo durante una transferencia.
- Reinicio del servidor y ruptura de la Turtle con objetos pendientes.
- Dos Turtle vinculadas al mismo jugador sin mezclar configuraciones.
- Intentos de otro jugador o de un programa de cambiar el propietario.
- Comparar el total de objetos antes y después de cada operación para demostrar que no se duplica ni desaparece nada.

---

## 2. Mejora GPS Navigator

### Concepto

Nuevo objeto `TaleOS_GPS_Navigator`, equipable en una Turtle y expuesto como periférico `navigator`. Aprovecha la red de referencias GPS existente, pero añade algo que `gps.locate()` no hace: calcular una ruta y ejecutar automáticamente los giros y desplazamientos necesarios.

El programa indicaría un destino, no una secuencia manual de `turtle.forward()`, `turtle.up()` y `turtle.turnLeft()`.

### Comportamiento propuesto

1. Localizar la posición inicial mediante GPS.
2. Comprobar que el destino es válido y está dentro del límite permitido.
3. Buscar una ruta 3D con A* u otro algoritmo acotado.
4. Inspeccionar los bloques próximos y evitar obstáculos.
5. Mover la Turtle con las animaciones actuales.
6. Recalcular si aparece un obstáculo o cambia el terreno.
7. Detenerse con un motivo claro si no hay ruta, se pierde el GPS o se descarga el chunk.

Por defecto no debe excavar, romper, construir puentes ni atravesar bloques. Esas capacidades pueden ser opciones explícitas más adelante y siempre con la herramienta adecuada.

### API Lua propuesta

| Método | Función |
|---|---|
| `getPosition()` | Devuelve la posición GPS actual o `nil, reason`. |
| `goTo(x, y, z[, options])` | Calcula y sigue una ruta; devuelve `true` o `false, reason`. |
| `setHome(x, y, z)` | Guarda un punto de origen persistente. |
| `getHome()` | Consulta el punto de origen. |
| `goHome([options])` | Regresa automáticamente al punto guardado. |
| `getStatus()` | Estado, destino, pasos restantes y último error. |
| `cancel()` | Cancela una navegación en curso desde otra tarea cooperativa. |

`goTo` puede bloquear el programa actual de forma cooperativa, pero nunca el hilo del servidor. El botón Stop debe cancelar tanto el programa como el movimiento pendiente.

Opciones iniciales razonables:

```lua
{
  timeout = 120,
  dig = false,
  replan = true
}
```

### Ejemplo: ir a unas coordenadas

```lua
local nav = peripheral.find("navigator")
assert(nav, "Equip a GPS Navigator")

local ok, reason = nav.goTo(120, 70, -40, {
  timeout = 120,
  dig = false
})

if not ok then
  print("Navigation failed: " .. tostring(reason))
else
  print("Destination reached")
end
```

### Ejemplo: transporte entre dos cofres

```lua
local nav = peripheral.find("navigator")
assert(nav, "Equip a GPS Navigator")

local pickup = { x = 100, y = 65, z = 20 }
local delivery = { x = 140, y = 65, z = 20 }

local function go(point)
  local ok, reason = nav.goTo(point.x, point.y, point.z)
  if not ok then error(reason) end
end

go(pickup)
turtle.select(1)
assert(turtle.suckDown())

go(delivery)
assert(turtle.dropDown())
print("Delivery complete")
```

Más adelante, este mismo sistema permitiría rutas con varias paradas, prioridades y una cola de encargos recibida por `rednet`.

### Límites necesarios

- Distancia máxima por encargo y número máximo de pasos.
- Número máximo de nodos explorados por tick, distribuyendo el pathfinding entre varios ticks.
- Tiempo máximo de navegación y número de recálculos ante obstáculos.
- No cargar chunks de forma oculta. Si el mundo no está cargado, detenerse e informar.
- No prometer una ruta por terreno desconocido: combinar mapa recordado con inspección real.
- Cancelar de forma segura al retirar la mejora, romper la Turtle, apagarla o terminar el programa.

### Configuración del servidor propuesta

```json
{
  "navigation": {
    "enabled": true,
    "max_distance": 512,
    "max_steps": 4096,
    "max_nodes_per_tick": 256,
    "allow_digging": false,
    "require_loaded_chunks": true
  }
}
```

### Pruebas imprescindibles

- Destino en línea recta, rodear una pared y superar un desnivel permitido.
- Obstáculo que aparece durante el trayecto y recalcula sin atravesarlo.
- Destino inalcanzable, sin cobertura GPS o fuera del límite.
- Chunk descargado, jugador desconectado y reinicio durante la navegación.
- Cancelación con Stop y retirada de la mejora a mitad de movimiento.
- Muchas Turtle navegando a la vez sin picos grandes de CPU.
- La posición, orientación, inventario e ID de la Turtle siguen siendo correctos al llegar.

---

## 3. Programas en pestañas y procesos

Una evolución parecida a `multishell`, adaptada a la interfaz de TaleOS:

- Pestañas para shell, editor, reloj, navegación y programas de usuario.
- Cambiar de pestaña sin interrumpir la ejecución.
- Comandos `jobs`, `fg`, `bg` y `kill`.
- Indicador de programa activo, en pausa o terminado con error.
- Un proceso solo recibe teclado cuando su pestaña tiene el foco; los eventos de red y temporizadores siguen llegando a su propia cola.
- Límites de procesos por ordenador y presupuesto de instrucciones global, para que varias tareas no multipliquen el consumo sin control.

Es especialmente útil junto con GPS Navigator: el usuario puede consultar el estado o cancelar una ruta mientras el programa de transporte sigue abierto en otra pestaña.

## 4. Monitores táctiles y lienzo de dibujo

### Interacción

Añadir `monitor_touch` con monitor, coordenadas del lienzo, bloque físico y jugador. Incluir permisos, distancia máxima y límite de eventos.

### Dibujo

Una API `canvas` o `paintutils` con:

- Líneas, rectángulos, marcos y rellenos.
- Imágenes guardadas en archivos dentro del filesystem.
- Botones, barras de progreso, gráficas y tablas como biblioteca Lua, no como componentes cerrados.
- Actualización por regiones modificadas para no reconstruir todo el monitor en cada cambio.

Esto permitiría paneles de almacenamiento, mapas de Turtle, botones para puertas, relojes, agendas y pantallas de control mucho más vistosas.

## 5. Dock para Turtle

Un bloque `TaleOS_Dock` que actúa como base de una Turtle:

- Emparejamiento con una Turtle y guardado de su posición como `home`.
- Reabastecimiento desde el inventario del Dock mediante filtros.
- Descarga automática de objetos en ranuras reservadas.
- Estado de batería solo si algún día se activa un modo de energía; no cambiar el combustible ilimitado actual por defecto.
- Telemetría: posición, último contacto, programa y error.
- Integración con Navigator para `goHome()` y con NAS para recibir nuevos programas.

El Dock puede convertirse en la pieza central de una estación de mensajería o minería sin introducir todavía una red logística compleja.

## 6. API pública de periféricos para otros mods

Publicar una API Java pequeña y versionada para que otros desarrolladores registren periféricos TaleOS:

- Descubrimiento de bloques y mejoras.
- Métodos permitidos y conversión segura de valores Lua.
- Ejecución en el hilo correcto y límites de coste.
- Aislamiento de excepciones de mods externos.
- Identificadores estables y documentación de compatibilidad.

Esto permite integrar futuras máquinas, contenedores, sensores o sistemas de energía sin que TaleOS tenga que implementar cada puente dentro de su propio código.

## 7. Constructor por planos

Un sistema para capturar y reproducir construcciones:

1. Seleccionar dos esquinas.
2. Guardar un plano con bloques, orientaciones y coordenadas relativas en un PenDrive o NAS.
3. Calcular la lista de materiales.
4. Comprobar el inventario antes de empezar.
5. Dejar que una Turtle coloque el plano por capas y reanude desde el último paso confirmado.

Funciones posibles: `blueprint.capture`, `blueprint.inspect`, `blueprint.materials` y `turtle.build`. Debe tener vista previa, confirmación antes de sustituir bloques y un modo que nunca rompa nada.

## 8. Mapa compartido del mundo en NAS

Cada Turtle puede guardar obstáculos y bloques inspeccionados en un mapa del NAS. Las demás Turtle lo utilizan para planificar rutas mejores.

- Coordenadas absolutas y versión del mundo.
- Marca de tiempo para distinguir datos antiguos.
- Actualizaciones atómicas y tamaño limitado.
- Regiones desconocidas que se comprueban físicamente antes de atravesarlas.
- Visualización del mapa y de las Turtle en monitores.

Es una ampliación natural de Navigator, no un requisito para su primera versión.

## 9. Router de objetos programable

Un periférico para mover objetos entre contenedores de la misma red, con filtros y prioridades:

- Rutas por ID de objeto, origen, destino y cantidad.
- Reservas mínimas: por ejemplo, conservar 32 bloques en un cofre.
- Cola de trabajos y resultado confirmado.
- Sin teletransporte ilimitado por defecto: alcance y número de transferencias configurables.
- Integración con Turtle, Dock, NAS y Player Link.

Conviene empezar por un router sencillo y no intentar reproducir de una vez todo un sistema de almacenamiento masivo.

## 10. Mejora Magnet

Una mejora que recoge objetos soltados cercanos y los introduce en ranuras permitidas:

- Radio, filtros y frecuencia configurables.
- Prioridad para objetos producidos por la propia Turtle.
- Si está llena, deja los objetos en el mundo.
- Compatible con Player Link para enviar después lo recogido.

## 11. Programas agrícolas y forestales inteligentes

Antes de añadir nuevos bloques, crear programas ROM que combinen las funciones existentes:

- Preparar suelo, plantar, cosechar y reponer semillas si Hytale expone esas acciones.
- Detectar estado de crecimiento sin romper cultivos inmaduros.
- Talar, replantar y separar troncos, hojas y semillas.
- Volver al Dock cuando falta espacio o material.
- Guardar las dimensiones de la parcela con una API `settings`.

## 12. Control remoto desde Pocket Computer

Un programa de Pocket Computer para emparejar Turtle y mostrar:

- Posición, estado, inventario resumido y último error.
- Órdenes de volver a casa, pausar o continuar.
- Mapa en un monitor y avisos mediante altavoz.
- Canales emparejados por propietario para no aceptar órdenes de cualquier ordenador.

No debe convertirse en una consola administrativa oculta: los permisos del jugador y del servidor siguen aplicando.

## 13. Sensores de entorno, entidades y jugadores

Nuevos periféricos con consultas acotadas y enfriamiento:

- Clima, hora y condiciones del entorno.
- Entidades cercanas, tipo, distancia y si son objetivos válidos.
- Jugadores cercanos, solo con permisos y sin consultar jugadores desconectados.
- Escáner de bloques para cartografiar una zona sin revelar más alcance del configurado.

Estos sensores alimentarían granjas, alarmas, navegación y paneles, pero necesitan límites estrictos de frecuencia y alcance.

## 14. Chunk Loader Upgrade — implementado

La mejora `TaleOS_Chunk_Loader_Upgrade` ya mantiene cargado el chunk actual de la Turtle, lo actualiza al cruzar un límite y lo restaura tras reiniciar el servidor. Retirar la mejora o romper la Turtle libera el chunk. Las Turtle disponen además de cuatro ranuras internas para módulos cuyo contenido no se dibuja en el modelo.

Posibles ampliaciones futuras, todavía no implementadas:

- Límites configurables por jugador y globales.
- Página de administración para ver y descargar chunks activos.
- Coste opcional configurable, sin imponer un sistema de energía a quien no lo quiera.

Estas ampliaciones siguen siendo independientes de Navigator.

## 15. Compatibilidad práctica para programadores

Ampliar las bibliotecas Lua sin prometer compatibilidad total con otros mods:

- `settings` para configuraciones persistentes de programas.
- `textutils` para serialización, tablas y formato.
- `keys` para nombres de teclas.
- `paintutils` para imágenes y figuras.
- Un `require` seguro limitado al filesystem de TaleOS, con caché, detección de ciclos y sin acceso a paquetes del host.
- Editor con resaltado, búsqueda, reemplazo y navegación a errores si la UI de Hytale lo permite.

Estas mejoras reducen el trabajo necesario para crear y compartir programas grandes.

## 16. Gestor de paquetes `tpm`

Un instalador de programas con manifiestos y dependencias:

```text
tpm install owner/package
tpm update
tpm list
tpm remove owner/package
```

- Usa el HTTP ya existente para descargar desde repositorios permitidos.
- Manifiesto con versión, archivos, dependencias y hash.
- Nunca ejecuta automáticamente lo descargado.
- Actualizaciones con confirmación y copia de seguridad.
- Catálogo opcional alojado como archivos estáticos en GitHub Pages.

## 17. Secuenciador musical

Crear música utilizando los SoundEvent registrados que ya puede usar Speaker:

- Notas, tempo, instrumentos permitidos y varias pistas.
- Archivos de canciones compartidos por NAS.
- Altavoces sincronizados por red.
- Editor visual en monitores.

No requiere abrir inicialmente la subida de audio arbitrario, con sus problemas de tamaño, moderación y ancho de banda.

## 18. Métricas y herramientas para administradores

A medida que crezcan los automatismos, será importante poder gobernarlos:

- CPU, memoria, eventos y llamadas al mundo por ordenador.
- Últimos errores y programas activos.
- Turtle en navegación y chunks mantenidos.
- Comandos para pausar una máquina concreta sin apagar el servidor.
- Umbrales de alerta y registros rotados con tamaño máximo.

## Ideas que conviene acotar o posponer

- **Acceso a inventarios de jugadores desconectados:** riesgo alto de sincronización y duplicados.
- **Chunk loaders ilimitados:** pueden degradar todo el servidor.
- **Pathfinding sin límites:** una sola orden lejana podría consumir demasiada CPU.
- **Excavación automática durante la navegación:** debe ser opcional, visible y nunca el comportamiento predeterminado.
- **Descargar y ejecutar código en un solo paso:** mantiene la separación actual entre instalar y ejecutar.
- **Canales inalámbricos sin identidad para funciones sensibles:** Player Link, control remoto y logística necesitan emparejamiento y permisos.
- **Sistema de energía obligatorio:** sería un cambio de balance; si se añade, debe ser opcional y conservar el combustible ilimitado como configuración predeterminada.

## Hitos propuestos

### Hito A — Enlace con el jugador: implementado

- Player Link con vinculación mediante F, propietario, rutas y envío manual/automático.
- Pruebas automatizadas de duplicación, intercambios, inventario lleno y stacks parciales; pruebas visuales y de desconexión recogidas en TESTING.
- Receta, modelo, traducciones, wiki y programa de ejemplo incluidos.

### Hito B — Navegación y mensajería

- GPS Navigator con `goTo`, cancelación y recálculo.
- Programa de transporte entre cofres.
- Dock básico y coordenadas guardadas.
- Pruebas de carga con varias Turtle y límites de chunks.

### Hito C — Control y creatividad

- Pestañas de procesos.
- Monitor táctil, figuras e imágenes.
- Panel de Turtle y control remoto desde Pocket Computer.

### Hito D — Ecosistema

- API pública de periféricos.
- `settings`, `textutils` y `require` seguro.
- Planos, mapa compartido, router y gestor de paquetes.

## Requisitos comunes para cualquier mejora

- Persistencia por identidad, nunca por posición del bloque.
- Operaciones de inventario atómicas en el hilo correcto del servidor.
- Presupuestos de CPU, tamaño de cola, alcance y frecuencia.
- Configuración y permisos para propietarios de servidores.
- Traducciones completas en inglés y español.
- Pruebas automatizadas y verificación real en el cliente.
- Receta, modelos, iconos, ejemplos y wiki actualizados.
- Compatibilidad con archivos y configuraciones de la versión anterior.

## Referencias de inspiración

Estas referencias sirven para estudiar conceptos y experiencia de uso; TaleOS debe mantener su propia implementación y adaptar las ideas a Hytale:

- [CC:Tweaked — GPS](https://tweaked.cc/module/gps.html): localización mediante referencias inalámbricas.
- [CC:Tweaked — multishell](https://tweaked.cc/module/multishell.html): ejecución de programas en pestañas.
- [CC:Tweaked — paintutils](https://tweaked.cc/module/paintutils.html): líneas, cajas e imágenes.
- [CC:Tweaked — settings](https://tweaked.cc/module/settings.html): configuración persistente para programas.
- [Advanced Peripherals — documentación](https://docs.advanced-peripherals.de/): ejemplos de integración con inventarios, mundo y otros sistemas.
- [netNav — concepto de mapa compartido y pathfinding](https://forums.computercraft.cc/index.php?topic=131.0): inspiración para combinar navegación con mapas de red.
