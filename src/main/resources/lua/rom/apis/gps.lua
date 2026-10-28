local gps = {
  CHANNEL = 65534,
}

local requestSerial = 0

local function finite(value)
  return type(value) == "number" and value == value and value ~= math.huge and value ~= -math.huge
end

local function firstModem()
  for _, side in ipairs(peripheral.getNames()) do
    if peripheral.getType(side) == "modem" then
      local modem = peripheral.wrap(side)
      if modem and modem.isWireless() then
        return side, modem
      end
    end
  end
  return nil
end

local function validReference(reference)
  return type(reference) == "table" and finite(reference.x) and finite(reference.y)
      and finite(reference.z) and finite(reference.distance) and reference.distance >= 0
end

local function solveFour(a, b, c, d)
  local others = { b, c, d }
  local rows = {}
  for i = 1, 3 do
    local ref = others[i]
    rows[i] = {
      2 * (ref.x - a.x),
      2 * (ref.y - a.y),
      2 * (ref.z - a.z),
      a.distance ^ 2 - ref.distance ^ 2 + ref.x ^ 2 + ref.y ^ 2 + ref.z ^ 2
          - a.x ^ 2 - a.y ^ 2 - a.z ^ 2,
    }
  end
  for column = 1, 3 do
    local pivot = column
    for row = column + 1, 3 do
      if math.abs(rows[row][column]) > math.abs(rows[pivot][column]) then
        pivot = row
      end
    end
    if math.abs(rows[pivot][column]) < 1e-9 then
      return nil
    end
    rows[column], rows[pivot] = rows[pivot], rows[column]
    local divisor = rows[column][column]
    for entry = column, 4 do
      rows[column][entry] = rows[column][entry] / divisor
    end
    for row = 1, 3 do
      if row ~= column then
        local factor = rows[row][column]
        for entry = column, 4 do
          rows[row][entry] = rows[row][entry] - factor * rows[column][entry]
        end
      end
    end
  end
  return rows[1][4], rows[2][4], rows[3][4]
end

local function consistent(references, x, y, z)
  for _, reference in ipairs(references) do
    local measured = math.sqrt((reference.x - x) ^ 2 + (reference.y - y) ^ 2
        + (reference.z - z) ^ 2)
    if math.abs(measured - reference.distance) > math.max(0.05, reference.distance * 0.02) then
      return false
    end
  end
  return true
end

function gps.trilaterate(references)
  if type(references) ~= "table" or #references < 4 then
    return nil, "at least four references are required"
  end
  for _, reference in ipairs(references) do
    if not validReference(reference) then
      return nil, "invalid GPS reference"
    end
  end
  for a = 1, #references - 3 do
    for b = a + 1, #references - 2 do
      for c = b + 1, #references - 1 do
        for d = c + 1, #references do
          local x, y, z = solveFour(references[a], references[b], references[c], references[d])
          if x and consistent(references, x, y, z) then
            return x, y, z
          end
        end
      end
    end
  end
  return nil, "references must be non-coplanar and have consistent distances"
end

local function openGpsModem()
  local side, modem = firstModem()
  if not modem then
    return nil, "no wireless modem attached"
  end
  local wasOpen = modem.isOpen(gps.CHANNEL)
  if not wasOpen then
    modem.open(gps.CHANNEL)
  end
  return side, modem, not wasOpen
end

function gps.locate(timeout)
  local side, modem, closeOnExit = openGpsModem()
  if not modem then
    return nil, side
  end
  requestSerial = (requestSerial + 1) % 1000000
  local request = os.getComputerID() * 1000000 + requestSerial
  modem.transmit(gps.CHANNEL, gps.CHANNEL, { gps = "request", request = request })
  local timer = os.startTimer(timeout or 2)
  local references = {}
  while true do
    local event, eventSide, channel, replyChannel, payload, distance = os.pullEventRaw()
    if event == "timer" and eventSide == timer then
      if closeOnExit then
        modem.close(gps.CHANNEL)
      end
      return nil, "GPS needs at least four non-coplanar references with consistent distances"
    elseif event == "modem_message" and eventSide == side and channel == gps.CHANNEL
        and type(payload) == "table" and payload.gps == "response" and payload.request == request
        and finite(payload.x) and finite(payload.y) and finite(payload.z)
        and finite(distance) and distance >= 0 then
      references[#references + 1] = {
        x = payload.x,
        y = payload.y,
        z = payload.z,
        distance = distance,
      }
      if #references >= 4 then
        local x, y, z = gps.trilaterate(references)
        if x then
          os.cancelTimer(timer)
          if closeOnExit then
            modem.close(gps.CHANNEL)
          end
          return x, y, z
        end
      end
    end
  end
end

function gps.host(x, y, z)
  if not finite(x) or not finite(y) or not finite(z) then
    error("GPS host coordinates must be finite numbers", 2)
  end
  local side, modem = openGpsModem()
  if not modem then
    error(side, 2)
  end
  modem.open(gps.CHANNEL)
  while true do
    local event, eventSide, channel, replyChannel, payload = os.pullEventRaw()
    if event == "modem_message" and eventSide == side and channel == gps.CHANNEL
        and type(payload) == "table" and payload.gps == "request"
        and type(replyChannel) == "number" then
      modem.transmit(replyChannel, gps.CHANNEL, {
        gps = "response",
        request = payload.request,
        x = x,
        y = y,
        z = z,
      })
    end
  end
end

return gps
