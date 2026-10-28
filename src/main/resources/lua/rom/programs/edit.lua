-- Abre el editor visual de archivos del ordenador (ventana con Guardar,
-- Guardar y ejecutar y Salir). Cierra el editor para volver al terminal.
local file = ...
if not file then
  print(tale.tr("Usage: edit <file>"))
  return
end
os.openEditor(shell.resolve(file))
