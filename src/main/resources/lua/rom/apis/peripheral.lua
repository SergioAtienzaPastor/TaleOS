local native = ...

local peripheral = {
  getNames = native.getNames,
  getType = native.getType,
  getMethods = native.getMethods,
  call = native.call,
}

function peripheral.wrap(name)
  return native._wrap(name)
end

function peripheral.find(peripheralType)
  local found = {}
  for _, name in ipairs(native.getNames()) do
    if native.getType(name) == peripheralType then
      local wrapped = peripheral.wrap(name)
      if wrapped then
        table.insert(found, wrapped)
      end
    end
  end
  return table.unpack(found)
end

return peripheral
