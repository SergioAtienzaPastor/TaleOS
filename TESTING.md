# Batería mínima de pruebas (Fase 6)

Prerequisito: servidor WSL arrancado con el jar desplegado y cliente Windows
conectado por `localhost`. Comandos del servidor útiles: logs en
`Server/logs/`, y el mod loguea `[selftest]` si se arranca con
`-Dtalecraft.selftest=true`.

Marcar cada prueba solo si se reproduce tal cual:

## Núcleo
- [ ] **Boot a TaleOS**: colocar un Computer (Workbench: 4 cobre + 4 madera
  muerta), pulsar F → se abre el terminal y se ve el banner `TaleOS v0.1` y el
  prompt `tale>`. Suena el beep de arranque (solo el primer encendido).
- [ ] **print visible**: en el terminal, `lua` → `print("Hello, Hytale!")` →
  `exit`. La salida aparece en el terminal.
- [ ] **Bucle infinito sin caída**: `lua` → `while true do end` → el terminal
  muere (mensaje de presupuesto) y el servidor sigue respondiendo (TPS
  estable: moverse, romper bloques, otro comando).
- [ ] **Persistencia**: `lua` → `fs.write('nota.txt', 'persisto')` → `exit` →
  `cat nota.txt`. Reiniciar el servidor, pulsar F de nuevo en el mismo bloque
  → `cat nota.txt` sigue funcionando.
- [ ] **API mundo**: `lua` → `print(tale.detect(0,-1,0))` (true si hay bloque
  debajo) → `tale.chat('hola')` aparece en el chat.

## Monitor
- [ ] Colocar un Monitor pegado al Computer, pulsar F → muestra el terminal del
  ordenador (read-only, sin campo de entrada) y se actualiza en vivo.

## Multi-cliente
- [ ] Un segundo cliente (otra sesión/PC) abre el mismo Computer y ve el mismo
  contenido del terminal.

## Regresión de servidor
- [ ] `grep -i talecraft Server/logs/latest.log` sin ERROR/WARN tras 5 min de
  uso.
