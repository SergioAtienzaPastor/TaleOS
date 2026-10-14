local path = ...
if not path then
  print("uso: cat <archivo>")
  return
end
local content = fs.read(path)
if not content then
  print("cat: no existe: " .. path)
  return
end
print(content)
