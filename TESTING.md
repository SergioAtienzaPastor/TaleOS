# Batería mínima de pruebas (Fase 6)

Prerequisito: servidor WSL arrancado con el jar desplegado y cliente Windows
conectado por `localhost`. Comandos del servidor útiles: logs en
`Server/logs/`, y el mod loguea `[selftest]` si se arranca con
`-Dtaleos.selftest=true`.

Todo lo que no exige interacción visual del cliente también se puede ejecutar
desde la consola del servidor con
`/taleos <mundo> <x> <y> <z> <línea de shell>` (p. ej.
`/taleos creative_test 191 123 -64 cat nota.txt`) y verificarse en
`mods/TaleOS/logs/computer-<id>.log`.

Marcar cada prueba solo si se reproduce tal cual:

## Núcleo
- [x] **Boot a TaleOS**: colocar un Computer (Workbench: 4 cobre + 4 madera
  muerta), pulsar F → se abre el terminal y se ve el banner `TaleOS v0.1` y el
  prompt `tale>`. Suena el beep de arranque (solo el primer encendido).
  ✅ 2026-10-21 (cliente, beep oído por el usuario; boot reproducido por
  consola: `[BOOT] computer #8`).
- [x] **print visible**: en el terminal, `lua` → `print("Hello, Hytale!")` →
  `exit`. La salida aparece en el terminal. ✅ 2026-10-21 (cliente + log).
- [x] **Bucle infinito sin caída**: `lua` → `while true do print("spam") end`
  → la máquina muere (`[DEAD] ... BudgetExceededException`) y el servidor
  sigue respondiendo. ✅ 2026-10-21 (computer-8.log 19:37 + server estable).
- [x] **Persistencia**: `cat nota.txt` → `persisto` tras varios reinicios del
  servidor. ✅ 2026-10-21 (tras los reinicios de las 11:23, 16:41, 16:59,
  17:12 y 17:35; id estable #8 gracias al fix de `ids.json` load).
- [x] **API mundo**: `print(tale.getBlock(0,0,0))` → `TaleOS_Computer`,
  `print(tale.getBlock(0,-1,0))` → `Soil_Grass_Full`,
  `print(tale.detect(0,-1,0))` → `true`, `tale.chat(...)` → mensaje en el chat
  (`[Broadcast]` en el log). ✅ 2026-10-21 por consola. OJO: lecturas solo con
  el chunk cargado (como CC:T) — con 0 jugadores el chunk se descarga y las
  lecturas devuelven nil/false por diseño.

## Monitor
- [ ] Colocar monitores apilados junto al Computer, ejecutar `reloj` y comprobar que el
  contenido se pinta directamente en el mundo. Pulsar F no debe abrir ninguna interfaz.

## Multi-cliente
- [ ] Un segundo cliente (otra sesión/PC) abre el mismo Computer y ve el mismo
  contenido del terminal. (No testeable con un solo PC.)

## Regresión de servidor
- [x] `grep -i taleos Server/logs/latest.log` sin ERROR/WARN tras 5 min de
  uso. ✅ 2026-10-21 — soak de 5+ min con la batería de consola ejecutándose:
  0 excepciones, 0 crashes de mundo, ningún SEVERE/WARN de TaleOS (solo el
  WARN benigno del escaneo del directorio de datos `mods/TaleOS` como pack).

## Notas de la sesión 2026-10-21
- El terminal muestra siempre la **cola** del transcript (14 líneas que caben
  en pantalla): la API de UI no tiene comando de scroll; historial completo en
  el log del ordenador. Pendiente confirmación visual del usuario.
- `worldOf` por accessor de interacción devolvía null → los ordenadores
  arrancaban sin mundo (detect/chat/beep muertos). Resuelto vía
  `Universe.get().getWorld(playerRef.getWorldUuid())`.
- Las máquinas se tickean **por mundo** (`tickWorld`): un worldcall desde el
  hilo de otro mundo violaba el assert de afinidad de hilo del store y
  crasheaba ese mundo (0.7.2).
- Bug `ids.json`: el load descartaba todos los mapeos (off-by-one al parsear);
  los ids cambiaban en cada reinicio. Corregido + test `ComputerIdStoreTest`.
- Región keep-loaded de vanilla aplicada alrededor del ordenador de demo:
  `/world settings keeploaded set 176 -80 207 -49 --world=creative_test`
  (reversible con `... keeploaded reset --world=creative_test`).
- Diagnóstico de lecturas de bloques desde consola:
  `/taleos <mundo> <x> <y> <z> __probe` (loguea `[TaleOS probe]`).

## Turtle (2026-10-25)
- [x] **Movimiento e identidad**: Turtle #12 avanzó de `(200,123,-64)` a
  `(200,123,-63)`, dejó el origen vacío, conservó `id` = 12 y volvió con `back`.
- [x] **API Lua**: `select(3)` + `getSelectedSlot()` devolvió 3; el slot queda
  guardado en `mods/TaleOS/turtles.json`.
- [x] **Inventario y colocación**: recibió 2 `Soil_Grass_Full`, `place()` creó el
  bloque delante y el stack quedó en 1.
- [x] **Herramienta y minería**: `Tool_Shovel_Copper` equipada desde el slot 2;
  `dig()` rompió el bloque frontal y `detect()` devolvió false.
- [ ] **Recogida**: `suck()` busca items en 2.5 bloques. Sin jugadores cerca,
  Hytale invalida el item entity al crearlo; `drop()` responde false y restaura
  el stack (verificado: queda en 1). Falta probar `drop → suck` con cliente cerca.
- [x] **Mejoras laterales**: inventario antiguo migrado de 17 a 18 slots sin perder
  contenido; `equipLeft()` y `equipRight()` confirmaron `Tool_Shovel_Copper` y
  `Tool_Pickaxe_Copper`; `getEquippedLeft/Right` devolvieron ambos nombres.
- [x] **Giros**: `go left 1` y `go right 1` terminan correctamente con el chunk
  cargado. ROM adicional: `turn`, `equip` y `unequip` (requiere la build desplegada).
- [x] **Túnel 3×3**: prueba automatizada con dos secciones verifica giros, subida,
  bajada, `digUp` y avance. Las 71 pruebas Gradle terminan en verde.
- [ ] **UI cliente**: abrir con F y comprobar la página combinada: terminal, 4×4,
  inventario del jugador y dos mejoras. Clic selecciona/transfiere; clic derecho
  devuelve objetos. Los slots de mejora solo aceptan herramientas.
- [ ] **Visual cliente**: comprobar icono, frontal y geometría de pala/pico en los
  laterales (no hay herramienta dibujada cuando los slots están vacíos).
- Comandos de apoyo: `__turtleplace`, `__turtleboot`, `__turtlegive <item> <n>`,
  `__turtle <línea>` y `__blockid`, todos con coordenadas y mundo de `/taleos`.

## HTTP y wget (2026-10-25)

- [x] Pruebas headless del servicio: reglas allow/deny, DNS y rangos privados,
  redirecciones, UTF-8, 404, límite de descarga, concurrencia y cancelación.
- [x] **GitHub Raw**: `/taleos creative_test 199 123 -65 wget https://raw.githubusercontent.com/github/gitignore/main/README.md github-readme.txt`
  guardó 5847 bytes en la Turtle #15; `cat github-readme.txt` mostró el contenido
  completo en `computer-15.log`. ✅ 2026-10-25.
- [x] **Red privada denegada**: con la configuración predeterminada, `wget
  http://127.0.0.1:5520 local.txt` respondió `HTTP address denied by rule` y no
  creó `local.txt`. ✅ 2026-10-25.
- [ ] **Cliente**: comprobar `edit hola.lua`, sugerencias azules y botón Completar;
  el editor no debe desconectar al abrirse.

## Eventos, timers, parallel y startup (2026-10-25)

- [x] `clean build`: 135 pruebas, 0 fallos. Cubre eventos tipados/FIFO/cola de 256,
  timers y cancelación antes/después de encolar, `parallel` any/all/filtros,
  presupuesto, terminate y startup válido/con error/con sleep.
- [x] **Servidor real, Turtle #15**: `startup.lua` ejecutó `STARTUP_OK` y dos tareas
  con `parallel.waitForAll`; el timer de 0.05 s produjo `TIMER_OK`. `reboot` creó
  una máquina nueva conservando el ID #15 y el disco. El startup temporal se retiró.
- [ ] **Cliente**: crear `startup.lua` con el editor, reiniciar desde la terminal y
  comprobar que el programa vuelve solo y puede detenerse con el botón Detener.

## Terminal, ventanas y redirección (2026-10-25)

- [x] `clean build`: 157 pruebas, 0 fallos. Incluye rejilla, colores/blit, cursor,
  scroll, ventanas recortadas, redirect/print, API legacy de píxeles y dos observadores.
- [x] **Servidor real, Turtle #15**: `term.clear`, `setCursorPos(2,2)`, `blit` y
  `NATIVE_TERM_OK` funcionaron; la shell siguió respondiendo y el reboot restauró
  el modo normal. Jar desplegado con SHA-256 coincidente.
- [x] Monitores usan revisiones monótonas; dos observadores ya no se consumen cambios.
- [ ] **Monitor real/visual**: los equipos de prueba no tenían un Monitor adyacente
  en esta sesión. Redirect, texto/colores y compatibilidad de `reloj.lua` tienen
  cobertura headless, pero falta comprobarlos en un Monitor del mundo y en cliente.
- [ ] La UI nativa conserva un Label: no muestra colores individuales por celda ni
  blink animado. El modelo Lua sí conserva esos atributos; el Monitor sí los pinta.

## Periféricos y cofres (2026-10-25)

- [x] `clean build`: 168 pruebas, 0 fallos. Incluye wrap/call/find, attach/detach,
  handles invalidados, monitores y transferencias parciales con inventarios fake.
- [x] **Servidor real**: `peripheral.getNames/getType/getMethods` funcionó en Computer
  #8 y Turtle #15 sin errores; no había cofres ni monitores adyacentes en esa sesión.
- [ ] **Integración con cofre**: colocar un cofre junto a Computer/Turtle, comprobar
  `size/list/getItemDetail`, extraer e insertar cantidades parciales y retirar el cofre
  para comprobar `peripheral_detach` y el error seguro del handle.

## Módems, rednet y GPS (2026-10-25)

- [x] `clean build`: 182 pruebas, 0 fallos. Incluye canales, alcance/mundos, deep copy,
  ciclos/saturación, protocolos/timeouts/DNS y trilateración 3D con referencias insuficientes.
- [x] **Servidor real**: módem junto a #8 y otro junto a #15. #8 envió `hola_red`
  con protocolo `demo`; #15 recibió `REDNET_RX 8 hola_red demo`. DNS devolvió `DNS 15`.
- [x] La shell y el daemon `rednet.run` coexistieron durante la prueba. El arranque desde
  consola se corrigió para ejecutar periféricos en el WorldThread; `__probe` conserva
  su carga asíncrona fuera de ese hilo.
- [ ] **Visual/GPS en mundo**: comprobar modelo/icono del módem en cliente y montar cuatro
  referencias no coplanares para `gps.locate`; la matemática está cubierta headless.

## Turtle: combate, crafting y mejoras (2026-10-25)

- [x] `clean build`: 194 pruebas, 0 fallos. Incluye registro de upgrades, selección/cálculo
  de ataque, planes de craft, módem en Turtle y validación de ROM/completado.
- [x] **Servidor real, Turtle #15**: mejora crafting equipada, `craft(1)` fabricó un
  `TaleOS_Modem` con cobre+madera y otro módem quedó equipado a la derecha.
- [x] **Red por upgrade**: #15 recibió `UPGRADE_RX 8 modem_equipado upgrade`; la mejora
  tiene prioridad sobre un bloque vecino en el mismo lado.
- [x] `attack()` sin entidad devolvió `false, "no hay ninguna entidad dañable"`.
- [ ] **Cliente/mundo**: comprobar daño y drops contra una entidad real, aspecto de las nuevas
  mejoras, inventario al arrastrar y `excavate` en terreno seguro.
  El craft se amplió durante la prueba para admitir recetas Crafting tier-0 de Workbench.

## Monitores multibloque, PenDrives, altavoces y Pocket (2026-10-26)

- [x] **Monitores**: dos bloques reales formaron 40×48, llegó `monitor_resize left` y
  `setPixel(5,30,"lime")` escribió en el bloque superior. La geometría 8×8, fusiones,
  divisiones, orientaciones, huecos y mesher tienen cobertura JUnit.
- [x] **PenDrive real**: lector USB sobre #8, PenDrive #19 etiquetado `Prueba`, mount `usb`,
  escritura/lectura `pendrive_ok`. Tras otro reinicio siguió `USB 19 Prueba usb pendrive_ok`.
  Se corrigió la lectura de contenedores presentes solo en Holder, no en entidad activa.
- [x] **Expulsión sin jugadores**: `eject()` devolvió false y el archivo siguió montado;
  el PenDrive se restauró, sin pérdida. La expulsión como drop real requiere cliente cerca.
- [x] **Altavoz real**: `playSound("TaleOS_Boot_Beep")` devolvió true; el segundo sonido
  en el mismo tick fue limitado y un SoundEvent inexistente se rechazó correctamente.
- [x] **Pocket**: spike de API favorable; item Use sin targetBlock, holder por jugador/slot,
  IDs/UUID persistentes, sesión única y suspensión implementados. 15 pruebas específicas.
- [x] **Regresión final**: `clean build`, 246 pruebas, 0 fallos. Monitor+USB+speaker y
  mensaje #8→#15 (`regresion_ok/final`) funcionaron juntos sin excepciones. Jar final:
  SHA-256 `3953462e19837720aebd0e8f38051e2a0dbe6dbea40cde896b5c4c128e149957`.

### Pendiente de verificación por una persona en el juego

- [ ] Abrir el editor y probar autocompletado; terminal con colores/cursor y scroll.
- [ ] Ver la pantalla multibloque frontal, su alineación y `reloj.lua` tras redirigirla.
- [ ] Insertar/retirar PenDrives mediante la UI, expulsar con un jugador cerca y ver iconos.
- [ ] Oír el altavoz y comprobar una mejora Speaker equipada en Turtle.
- [ ] Abrir el Pocket con Use/F, ejecutar comandos y comprobar cambio de slot/drop/logout.
- [ ] Probar cofre real, daño/drops de Turtle, `excavate`, cuatro hosts GPS y modelos nuevos.

## Correcciones de cliente, construcción y NAS (2026-10-27)

- [x] **Regresión**: `clean build`, 255 pruebas, 0 fallos. `git diff --check` limpio.
- [x] **Crash Turtle/NAS corregido**: las copias visuales de objetos TaleOS no llevan metadatos
  incompatibles al cliente; los stacks reales conservan IDs, etiquetas y persistencia. El usuario
  confirmó que las interfaces y el resto de las pruebas visuales funcionan.
- [x] **Autocompletado de consola**: Computer y Turtle autocompletan con TAB y ya no tienen botón;
  el editor conserva el botón `Completar`. Pendiente confirmación visual del foco y las sugerencias.
- [x] **Monitor**: retirada la interfaz F; se mantiene el pintado directo en el mundo.
  Los bloques TaleOS tienen soporte superior y el Monitor puede apilarse sin exigir apoyo.
- [x] **NAS real**: dos unidades apiladas compartieron los PenDrives #22 y #23 entre Computers y
  Turtles. El montaje se crea después del arranque y se repara si el registro queda obsoleto.
- [x] **NAS en Pocket**: monta unidades próximas al jugador al abrir y reexplora cada segundo;
  cerrar, soltar o desconectar libera sus montajes. PenDrive #22 contiene `LEEME_NAS.txt` para
  comprobarlo con `cat nas/22/LEEME_NAS.txt`. Pendiente confirmación visual con el portátil.
- [x] **Sin NAS fantasma**: el espacio `nas` queda reservado para montajes reales. Tras retirar los
  PenDrives, Computer #8 dejó de listar `nas/` y `cat nas/22/compartido.txt` respondió
  `mount is not attached: nas`, aunque existía una copia local anterior bajo `computers/8/nas`.
- [x] **Armas como mejoras**: el registro admite tanto `ItemTool` como `ItemWeapon`; hay prueba
  unitaria para espadas. La prueba física quedó pendiente porque el chunk de la Turtle estaba
  descargado y el juego respondió `inventario no disponible`.
- [x] **Sin desgaste**: `dig*` y `attack*` ya no modifican la durabilidad de las mejoras equipadas;
  las animaciones y el resto de las acciones se conservan. Pendiente comprobación visual.
- [x] **excavate real**: Turtle temporal #24 con pala ejecutó `excavate 3`, terminó tres secciones,
  recogió drops y gastó durabilidad 200 → 196 cuando el desgaste todavía estaba activo.
- [x] **Listado NAS y cd reales**: `ls nas/22` mostró `LEEME_NAS.txt` y `compartido.txt`;
  `cd nas/22`, `ls`, `cat LEEME_NAS.txt` y `cd /` funcionaron con prompt `tale:/nas/22>`.
  La ocultación de carpetas fantasma solo se aplica a la raíz local, nunca dentro de un montaje.
- Jar desplegado: SHA-256 `043910dd8ba024d3f4d4a2c97df709cca7b0314bc88e865f4f34661de1838918`.
