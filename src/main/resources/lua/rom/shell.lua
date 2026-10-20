-- TaleOS shell: REPL de líneas. Ejecuta programas de rom/programs o archivos
-- .lua del disco del ordenador. Cada línea de entrada llega como evento stdin.

local function splitWords(line)
  local words = {}
  for w in tostring(line):gmatch("%S+") do
    words[#words + 1] = w
  end
  return words
end

local function findProgram(name)
  local candidates = { "rom/programs/" .. name .. ".lua", name .. ".lua" }
  for _, path in ipairs(candidates) do
    local src = fs.read(path)
    if src then
      return src, path
    end
  end
  return nil
end

while true do
  tale.setPrompt("tale> ")
  local ev, line = tale.pullEvent()
  if ev == "stdin" and line then
    local words = splitWords(line)
    if #words > 0 then
      local name = words[1]
      local src, path = findProgram(name)
      if not src then
        print("comando desconocido: " .. name)
      else
        local fn, err = load(src, "@" .. path)
        if not fn then
          print("error cargando " .. name .. ": " .. tostring(err))
        else
          local ok, perr = pcall(fn, table.unpack(words, 2))
          if not ok then
            print(name .. ": " .. tostring(perr))
          end
        end
      end
    end
  end
end
