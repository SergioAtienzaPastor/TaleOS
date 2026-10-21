# Batería mínima de pruebas (Fase 6)

Prerequisito: servidor WSL arrancado con el jar desplegado y cliente Windows
conectado por `localhost`. Comandos del servidor útiles: logs en
`Server/logs/`, y el mod loguea `[selftest]` si se arranca con
`-Dtalecraft.selftest=true`.

Todo lo que no exige interacción visual del cliente también se puede ejecutar
desde la consola del servidor con
`/talecraft <mundo> <x> <y> <z> <línea de shell>` (p. ej.
`/talecraft creative_test 191 123 -64 cat nota.txt`) y verificarse en
`mods/TaleCraft/logs/computer-<id>.log`.

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
- [x] **API mundo**: `print(tale.getBlock(0,0,0))` → `TaleCraft_Computer`,
  `print(tale.getBlock(0,-1,0))` → `Soil_Grass_Full`,
  `print(tale.detect(0,-1,0))` → `true`, `tale.chat(...)` → mensaje en el chat
  (`[Broadcast]` en el log). ✅ 2026-10-21 por consola. OJO: lecturas solo con
  el chunk cargado (como CC:T) — con 0 jugadores el chunk se descarga y las
  lecturas devuelven nil/false por diseño.

## Monitor
- [ ] Colocar un Monitor pegado al Computer, pulsar F → muestra el terminal del
  ordenador (read-only, sin campo de entrada) y se actualiza en vivo.
  (Pendiente: requiere cliente conectado.)

## Multi-cliente
- [ ] Un segundo cliente (otra sesión/PC) abre el mismo Computer y ve el mismo
  contenido del terminal. (No testeable con un solo PC.)

## Regresión de servidor
- [ ] `grep -i talecraft Server/logs/latest.log` sin ERROR/WARN tras 5 min de
  uso.

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
  `/talecraft <mundo> <x> <y> <z> __probe` (loguea `[TaleCraft probe]`).
