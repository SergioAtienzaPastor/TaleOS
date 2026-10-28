-- Reloj en el monitor adyacente: HH:MM:SS centrado, actualizado cada segundo.
-- Detén el programa con el botón Detener del terminal.
local mon = tale.monitor()
if not mon then
  print(tale.tr("No adjacent monitor"))
  print(tale.tr("Place a Monitor next to the computer"))
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
