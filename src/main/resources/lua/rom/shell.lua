-- TaleOS shell: REPL de líneas. Ejecuta programas de rom/programs o archivos
-- .lua del disco del ordenador. Cada línea de entrada llega como evento stdin.

local function splitWords(line)
  local words = {}
  for w in tostring(line):gmatch("%S+") do
    words[#words + 1] = w
  end
  return words
end

local rawFs = fs
local currentDir = ""

local function resolve(path)
  local text = tostring(path or "")
  local combined
  if text:sub(1, 1) == "/" then
    combined = text
  elseif currentDir == "" then
    combined = text
  else
    combined = currentDir .. "/" .. text
  end
  local parts = {}
  for part in combined:gmatch("[^/]+") do
    if part == ".." then
      if #parts > 0 then
        table.remove(parts)
      end
    elseif part ~= "." then
      parts[#parts + 1] = part
    end
  end
  return table.concat(parts, "/")
end

shell = {}
function shell.dir()
  return currentDir
end
shell.resolve = resolve
function shell.setDir(path)
  currentDir = resolve(path)
end

fs = {}
for name, fn in pairs(rawFs) do
  fs[name] = function(path, ...)
    if type(path) == "string" then
      return fn(resolve(path), ...)
    end
    return fn(path, ...)
  end
end

local function findProgram(name)
  local candidates = { "/rom/programs/" .. name .. ".lua", name .. ".lua" }
  for _, path in ipairs(candidates) do
    local src = fs.read(path)
    if src then
      return src, path
    end
  end
  return nil
end

local function shellLoop()
  while true do
    tale.setPrompt(currentDir == "" and "tale> " or "tale:/" .. currentDir .. "> ")
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
            -- Marca "programa en ejecución" para que el botón Detener solo
            -- mate programas y nunca a la propia shell.
            tale.setProgramRunning(true)
            local ok, perr = pcall(fn, table.unpack(words, 2))
            tale.setProgramRunning(false)
            if not ok then
              print(name .. ": " .. tostring(perr))
            end
          end
        end
      end
    end
  end
end

parallel.waitForAll(shellLoop, rednet.run)
