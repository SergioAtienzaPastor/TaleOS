-- Mini REPL de Lua. "exit" para volver al shell.
print('Lua REPL — "exit" para salir')
while true do
  term.write("lua> ")
  local ev, line = tale.pullEvent()
  if ev == "stdin" and line then
    if line == "exit" then
      return
    end
    local fn, err = load("return " .. line, "=repl")
    if not fn then
      fn, err = load(line, "=repl")
    end
    if not fn then
      print(tostring(err))
    else
      local res = table.pack(pcall(fn))
      if res[1] then
        if res.n < 2 then
          print("ok")
        else
          for i = 2, res.n do
            print(res[i])
          end
        end
      else
        print(tostring(res[2]))
      end
    end
  end
end
