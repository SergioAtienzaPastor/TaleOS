-- TaleOS bios — escrito desde cero para TaleCraft.
-- Prepara las APIs base (term, os) y arranca el shell desde rom/shell.lua.

-- term: MVP lineal sobre print/rawwrite; el terminal de rejilla llega con la UI.
term = term or {}
function term.write(s)
  rawwrite(tostring(s))
end
function term.getSize()
  return 51, 19
end
function term.clear()
  -- línea de control form-feed: el ordenador limpia el scrollback al verla
  print("\x0c")
end
function term.setCursorPos(x, y)
end

-- os sobre la tabla ya saneada (clock/time/date) del sandbox.
function os.pullEvent(filter)
  while true do
    local ev = { tale.pullEvent() }
    if filter == nil or ev[1] == filter then
      return table.unpack(ev)
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
  -- 20 ticks por segundo de servidor; mínimo un tick
  tale.sleep(math.max(1, math.ceil((seconds or 0) * 20)))
end

print("TaleOS v0.1 (TaleCraft)")

local src = fs.read("rom/shell.lua")
if not src then
  error("TaleOS: rom/shell.lua no encontrado")
end
local fn, err = load(src, "@rom/shell.lua")
if not fn then
  error("TaleOS: " .. tostring(err))
end
fn()
