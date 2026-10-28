local url, path = ...
if not url then
  print("Uso: wget <url> [archivo]")
  return
end
if type(http) ~= "table" or type(http.get) ~= "function" then
  print("wget: HTTP está desactivado en mods/TaleCraft/config.json")
  return
end

if not path or path == "" then
  local clean = url:gsub("[?#].*$", "")
  path = clean:match("([^/]+)/?$") or "download"
end

print("Descargando " .. url)
local response, err = http.get(url)
if not response then
  print("wget: " .. tostring(err))
  return
end

local code, reason = response.getResponseCode()
if code < 200 or code >= 300 then
  response.close()
  print("wget: HTTP " .. tostring(code) .. " " .. tostring(reason))
  return
end

local body = response.readAll()
response.close()
local ok, writeErr = pcall(fs.write, path, body)
if not ok then
  print("wget: no se pudo guardar " .. path .. ": " .. tostring(writeErr))
  return
end
print("Guardado " .. path .. " (" .. tostring(#body) .. " bytes)")
