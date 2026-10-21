-- Reloj en el monitor adyacente: HH:MM centrado, actualizado cada segundo.
-- Ctrl: detenlo con reboot (o apaga el ordenador).
local mon = tale.monitor()
if not mon then
  print("no hay monitor adyacente")
  print("coloca un Monitor tocando el ordenador")
  return
end
local w, h = mon.getSize()
while true do
  local t = os.date("*t")
  local s = string.format("%02d:%02d:%02d", t.hour, t.min, t.sec)
  -- 8 caracteres * 4px - 1 de cola = 31px de ancho, 5 de alto
  mon.clear("black")
  mon.write(math.floor((w - 31) / 2), math.floor((h - 5) / 2), s, "lime")
  tale.sleep(20)
end
