function test()
    return {
        b = 12
    }
end

for a in pairs(test()) do
    print(a)
end

local f,e = pcall(load)

print(f,e)



