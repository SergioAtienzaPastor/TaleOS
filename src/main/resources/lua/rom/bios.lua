-- TaleOS bios — escrito desde cero para TaleCraft.
-- Prepara las APIs base (term, os), ejecuta startup.lua y arranca el shell.

term = term or {}
colors = {}
for index, name in ipairs({
  "black", "navy", "blue", "cyan", "green", "lime", "yellow", "orange",
  "red", "pink", "magenta", "purple", "brown", "gray", "silver", "white"
}) do
  colors[name] = 2 ^ (index - 1)
end
colours = colors

function os.pullEventRaw()
  return tale.pullEvent()
end
function os.pullEvent(filter)
  while true do
    local ev = table.pack(tale.pullEvent(filter))
    if filter == nil or ev[1] == filter then
      return table.unpack(ev, 1, ev.n)
    end
  end
end
function os.getComputerID()
  return tale.getComputerID()
end
function os.reboot()
  tale.reboot()
end
function os.shutdown()
  tale.shutdown()
end
function os.sleep(seconds)
  local timer = os.startTimer(seconds or 0)
  while true do
    local event, id = os.pullEventRaw()
    if event == "timer" and id == timer then
      return
    end
  end
end

local function loadRom(path)
  local src = fs.read(path)
  if not src then
    error("TaleOS: " .. path .. " no encontrado")
  end
  local fn, err = load(src, "@" .. path)
  if not fn then
    error("TaleOS: " .. tostring(err))
  end
  return fn
end

parallel = loadRom("rom/apis/parallel.lua")()
window = loadRom("rom/apis/window.lua")()
peripheral = loadRom("rom/apis/peripheral.lua")(peripheral)
rednet = loadRom("rom/apis/rednet.lua")()
gps = loadRom("rom/apis/gps.lua")()

print("TaleOS v0.1 (TaleCraft)")

local startup = fs.read("startup.lua")
if startup then
  local fn, err = load(startup, "@startup.lua")
  if not fn then
    print("startup.lua: " .. tostring(err))
  else
    tale.setProgramRunning(true)
    local ok, runErr = pcall(fn)
    tale.setProgramRunning(false)
    if not ok then
      print("startup.lua: " .. tostring(runErr))
    end
  end
end

loadRom("rom/shell.lua")()
