-- Mini REPL de Lua. "exit" para volver al shell.
print('Lua REPL — "exit" para salir')
while true do
  tale.setPrompt("lua> ")
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
        -- Solo imprime valores no-nil; las sentencias sin retorno quedan mudas.
        for i = 2, res.n do
          if res[i] ~= nil then
            print(res[i])
          end
        end
      else
        print(tostring(res[2]))
      end
    end
  end
end
