-- Editor mínimo por líneas: escribe líneas, ".save" guarda, ".abort" cancela.
local file = ...
if not file then
  print("uso: edit <archivo>")
  return
end
local existing = fs.read(file)
local lines = {}
if existing then
  for l in (existing .. "\n"):gmatch("(.-)\n") do
    lines[#lines + 1] = l
  end
  print("editando " .. file .. " (" .. #lines .. " líneas; escribe para añadir al final)")
else
  print("editando " .. file .. " (nuevo)")
end
print(".save guarda — .abort cancela")
while true do
  local ev, line = tale.pullEvent()
  if ev == "stdin" and line then
    if line == ".save" then
      local ok, err = pcall(fs.write, file, table.concat(lines, "\n"))
      if ok then
        print("guardado " .. file)
      else
        print("edit: " .. tostring(err))
      end
      return
    elseif line == ".abort" then
      print("cancelado")
      return
    else
      lines[#lines + 1] = line
    end
  end
end
