-- Abre el editor visual de archivos del ordenador (ventana con Guardar,
-- Guardar y ejecutar y Salir). Cierra el editor para volver al terminal.
local file = ...
if not file then
  print("uso: edit <archivo>")
  return
end
os.openEditor(shell.resolve(file))
